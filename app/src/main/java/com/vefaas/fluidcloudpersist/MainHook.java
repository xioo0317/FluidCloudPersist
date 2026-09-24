package com.vefaas.fluidcloudpersist;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import dalvik.system.DexFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * FluidCloud Persist - Xposed 模块入口
 *
 * 功能：强制流体云胶囊在横屏/沉浸模式下常驻显示
 * 原理：Hook ColorOS SystemUI 中控制流体云胶囊显隐的类与方法，
 *       在状态栏自动收起/沉浸模式触发时强制保持胶囊可见。
 *
 * 作用域：com.android.systemui
 * 适配：ColorOS 14 / 15 (OPPO / OnePlus / realme)
 * 框架：LSPosed / EdXposed (Xposed API 93+)
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "FluidCloudPersist";
    private static boolean hooked = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只处理 SystemUI
        if (!"com.android.systemui".equals(lpparam.packageName)) return;

        synchronized (MainHook.class) {
            if (hooked) return;
            hooked = true;
        }

        // 初始化偏好设置
        PrefHelper.init();
        if (!PrefHelper.isModuleEnabled()) {
            log("模块未启用，跳过 hook");
            return;
        }

        log("模块已加载，开始 hook SystemUI...");

        // 策略 1：Hook ColorOS 流体云胶囊控制器（核心）
        hookCapsuleController(lpparam);

        // 策略 2：Hook Android 框架状态栏收起方法（兜底）
        hookStatusBarCollapse(lpparam);

        // 策略 3：Hook Window 可见性变化（兜底）
        hookWindowVisibility(lpparam);

        // 策略 4：Hook View 显隐（安全网）
        hookViewVisibility(lpparam);

        log("所有 hook 完成");
    }

    // ================================================================
    // 策略 1：Hook ColorOS 流体云胶囊控制器
    // ================================================================

    private void hookCapsuleController(XC_LoadPackage.LoadPackageParam lpparam) {
        log("策略 1：搜索并 hook 胶囊控制器...");

        // 已知 ColorOS 类名候选（不同版本可能混淆不同）
        String[] candidateClasses = {
            "com.android.systemui.fluidcloud.capsule.CapsuleController",
            "com.android.systemui.fluidcloud.CapsuleController",
            "com.android.systemui.oplus.capsule.CapsuleController",
            "com.android.systemui.oplus.statusbar.OplusFluidCloudManager",
            "com.android.systemui.oplus.statusbar.OplusCapsuleManager",
            "com.android.systemui.statusbar.oplus.OplusFluidCloudManager",
            "com.android.systemui.statusbar.oplus.OplusCapsuleController",
            "com.oplus.systemui.statusbar.capsule.CapsuleController",
            "com.oplus.systemui.statusbar.capsule.FluidCloudCapsuleView",
            "com.android.systemui.statusbar.phone.CapsuleController",
            "com.android.systemui.statusbar.phone.FluidCloudController",
        };

        boolean found = false;

        // 先尝试已知类名
        for (String className : candidateClasses) {
            if (tryHookClass(className, lpparam)) {
                found = true;
                break;
            }
        }

        // 模式匹配：遍历已加载类，寻找包含关键词的类
        if (!found) {
            log("已知类名未命中，尝试模式匹配...");
            List<String> patternClasses = findClassesByPattern(lpparam,
                    "capsule", "fluidcloud", "pill", "seedling", "liveactivity");
            for (String cls : patternClasses) {
                if (tryHookClass(cls, lpparam)) {
                    found = true;
                }
            }
        }

        if (!found) {
            log("未找到胶囊控制器类，将依赖兜底策略");
        }
    }

    /**
     * 尝试 hook 指定类中的 hide/dismiss/collapse/setVisibility 方法
     */
    private boolean tryHookClass(String className, XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className, false, lpparam.classLoader);
        } catch (ClassNotFoundException e) {
            return false;
        }

        log("找到候选类: " + className);
        boolean hookedAny = false;

        // 遍历所有声明方法，hook 可能与隐藏相关的方法
        for (Method method : clazz.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();

            // 跳过构造方法和静态初始化
            if (name.equals("<init>") || name.equals("<clinit>")) continue;

            // 匹配隐藏/收起/销毁相关方法名
            if (name.contains("hide") || name.contains("dismiss")
                    || name.contains("collapse") || name.contains("remov")
                    || name.contains("detach") || name.contains("setvisible")
                    || name.contains("onhide") || name.contains("disappear")
                    || name.equals("gone") || name.equals("vanish")) {

                try {
                    XposedHelpers.findAndHookMethod(clazz, method.getName(),
                            getParameterTypes(method), new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            forceCapsuleVisible(param.thisObject, clazz);
                        }
                    });
                    log("  ✓ Hook: " + className + "." + method.getName());
                    hookedAny = true;
                } catch (Throwable t) {
                    log("  ✗ Hook 失败: " + className + "." + method.getName()
                            + " - " + t.getMessage());
                }
            }
        }

        // 额外 hook：强制 hook 该类的 onConfigurationChanged 以在横屏切换时重新强制显示
        try {
            XposedHelpers.findAndHookMethod(clazz, "onConfigurationChanged",
                    android.content.res.Configuration.class,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    forceCapsuleVisible(param.thisObject, clazz);
                }
            });
            log("  ✓ Hook: " + className + ".onConfigurationChanged");
            hookedAny = true;
        } catch (Throwable ignored) {}

        return hookedAny;
    }

    /**
     * 强制胶囊视图可见
     */
    private void forceCapsuleVisible(Object target, Class<?> clazz) {
        if (!PrefHelper.isModuleEnabled()) return;
        if (!PrefHelper.isForceShow()) return;

        Context ctx = null;
        try {
            // 尝试获取 Context
            ctx = (Context) XposedHelpers.getObjectField(target, "mContext");
        } catch (Throwable ignored) {}

        if (ctx == null) {
            try {
                ctx = AndroidAppHelper.currentApplication();
            } catch (Throwable ignored) {}
        }

        // 横屏模式下才生效（如果设置了 landscape_only）
        if (PrefHelper.isLandscapeOnly() && ctx != null) {
            if (!PrefHelper.isLandscape(ctx)) return;
        }

        // 通过反射找到并强制设置 View 为 VISIBLE
        forceViewVisible(target);
    }

    /**
     * 通过反射找到目标对象中的 View 字段并强制设为 VISIBLE
     */
    private void forceViewVisible(Object target) {
        if (target == null) return;

        Class<?> clazz = target.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!View.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    View view = (View) field.get(target);
                    if (view != null && view.getVisibility() != View.VISIBLE) {
                        // 直接操作底层 flag 避免递归触发 hook
                        setViewVisibleDirect(view);
                        log("  → 强制显示 View: " + field.getName()
                                + " (" + view.getClass().getSimpleName() + ")");
                    }
                } catch (Throwable ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 直接设置 View 为 VISIBLE（通过修改底层 flag，避免触发 setVisibility hook）
     */
    private void setViewVisibleDirect(View view) {
        try {
            // View.VISIBLE = 0x00000000
            // View.GONE    = 0x00000008
            // View.INVISIBLE = 0x00000004
            Field flagsField = View.class.getDeclaredField("mViewFlags");
            flagsField.setAccessible(true);
            int flags = flagsField.getInt(view);
            // 清除 GONE 和 INVISIBLE flag
            flags &= ~(0x00000008 | 0x00000004);
            flagsField.setInt(view, flags);
            // 触发重新布局
            view.requestLayout();
            view.invalidate();
        } catch (Throwable t) {
            // 降级：直接调用 setVisibility
            try {
                view.setVisibility(View.VISIBLE);
            } catch (Throwable ignored) {}
        }
    }

    // ================================================================
    // 策略 2：Hook Android 框架状态栏收起方法
    // ================================================================

    private void hookStatusBarCollapse(XC_LoadPackage.LoadPackageParam lpparam) {
        log("策略 2：hook 状态栏收起方法...");

        // Hook PhoneStatusBar 的收起方法
        String[] statusBarClasses = {
            "com.android.systemui.statusbar.phone.PhoneStatusBar",
            "com.android.systemui.statusbar.phone.CentralSurfaces",
            "com.android.systemui.statusbar.phone.StatusBar",
        };

        for (String clsName : statusBarClasses) {
            Class<?> cls;
            try {
                cls = Class.forName(clsName, false, lpparam.classLoader);
            } catch (ClassNotFoundException e) {
                continue;
            }

            // Hook animateCollapsePanels
            tryHookMethod(cls, "animateCollapsePanels", new Class<?>[0], lpparam);
            // Hook animateCollapse
            tryHookMethod(cls, "animateCollapse", new Class<?>[0], lpparam);
        }

        // Hook StatusBarManagerService 的 collapse 方法
        try {
            Class<?> smsClass = Class.forName(
                    "com.android.server.statusbar.StatusBarManagerService",
                    false, lpparam.classLoader);
            for (Method m : smsClass.getDeclaredMethods()) {
                if (m.getName().equals("collapsePanels")
                        || m.getName().equals("collapse")) {
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (!PrefHelper.isModuleEnabled()) return;
                                if (!PrefHelper.isForceShow()) return;

                                Context ctx = null;
                                try {
                                    ctx = (Context) XposedHelpers.getObjectField(
                                            param.thisObject, "mContext");
                                } catch (Throwable ignored) {}

                                if (PrefHelper.isLandscapeOnly() && ctx != null) {
                                    if (!PrefHelper.isLandscape(ctx)) return;
                                }

                                log("  → 拦截状态栏收起: " + m.getName());
                                param.setResult(null);
                            }
                        });
                        log("  ✓ Hook: StatusBarManagerService." + m.getName());
                    } catch (Throwable t) {
                        log("   Hook 失败: " + m.getName() + " - " + t.getMessage());
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            log("  StatusBarManagerService 未找到（正常，不在 SystemUI 进程中）");
        }
    }

    private void tryHookMethod(Class<?> clazz, String methodName,
                               Class<?>[] paramTypes, XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, paramTypes,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!PrefHelper.isModuleEnabled()) return;
                    if (!PrefHelper.isForceShow()) return;

                    Context ctx = null;
                    try {
                        ctx = (Context) XposedHelpers.getObjectField(
                                param.thisObject, "mContext");
                    } catch (Throwable ignored) {}

                    if (PrefHelper.isLandscapeOnly() && ctx != null) {
                        if (!PrefHelper.isLandscape(ctx)) return;
                    }

                    log("  → 拦截状态栏收起: " + clazz.getSimpleName()
                            + "." + methodName);
                    param.setResult(null);
                }
            });
            log("  ✓ Hook: " + clazz.getSimpleName() + "." + methodName);
        } catch (Throwable t) {
            log("  ✗ Hook 失败: " + clazz.getSimpleName()
                    + "." + methodName + " - " + t.getMessage());
        }
    }

    // ================================================================
    // 策略 3：Hook Window 可见性变化
    // ================================================================

    private void hookWindowVisibility(XC_LoadPackage.LoadPackageParam lpparam) {
        log("策略 3：hook Window 可见性...");

        try {
            XposedHelpers.findAndHookMethod("android.view.WindowManagerGlobal",
                    lpparam.classLoader, "addView",
                    View.class, ViewGroup.LayoutParams.class,
                    android.view.Display.class, Window.class,
                    new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // 检查是否添加了状态栏相关的 window
                    // 这里作为信息收集，不做拦截
                }
            });
        } catch (Throwable t) {
            log("  WindowManagerGlobal.addView hook 失败（可能签名不匹配）");
        }
    }

    // ================================================================
    // 策略 4：Hook View.setVisibility（安全网）
    // ================================================================

    private void hookViewVisibility(XC_LoadPackage.LoadPackageParam lpparam) {
        log("策略 4：设置 View.setVisibility 安全网 hook...");

        try {
            XposedHelpers.findAndHookMethod("android.view.View",
                    lpparam.classLoader, "setVisibility", int.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!PrefHelper.isModuleEnabled()) return;
                    if (!PrefHelper.isForceShow()) return;

                    int visibility = (int) param.args[0];

                    // 只在尝试隐藏时拦截
                    if (visibility != View.GONE && visibility != View.INVISIBLE) return;

                    View view = (View) param.thisObject;
                    if (!isCapsuleRelatedView(view)) return;

                    Context ctx = null;
                    try {
                        ctx = view.getContext();
                    } catch (Throwable ignored) {}

                    if (PrefHelper.isLandscapeOnly() && ctx != null) {
                        if (!PrefHelper.isLandscape(ctx)) return;
                    }

                    // 直接修改底层 flag 为 VISIBLE，避免递归
                    try {
                        Field flagsField = View.class.getDeclaredField("mViewFlags");
                        flagsField.setAccessible(true);
                        int flags = flagsField.getInt(view);
                        flags &= ~(0x00000008 | 0x00000004);
                        flagsField.setInt(view, flags);
                    } catch (Throwable ignored) {}

                    // 阻止原始方法执行
                    param.setResult(null);
                    log("  → 安全网拦截: " + view.getClass().getName()
                            + " GONE/INVISIBLE → VISIBLE");
                }
            });
            log("  ✓ View.setVisibility 安全网已设置");
        } catch (Throwable t) {
            log("   View.setVisibility hook 失败: " + t.getMessage());
        }
    }

    /**
     * 判断 View 是否与流体云胶囊相关
     */
    private boolean isCapsuleRelatedView(View view) {
        if (view == null) return false;

        String className = view.getClass().getName().toLowerCase();

        // 类名包含关键词
        if (className.contains("capsule") || className.contains("fluidcloud")
                || className.contains("pill") || className.contains("seedling")
                || className.contains("liveactivity") || className.contains("bubble")) {
            return true;
        }

        // 检查父 View 链
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            String parentName = parent.getClass().getName().toLowerCase();
            if (parentName.contains("capsule") || parentName.contains("fluidcloud")
                    || parentName.contains("pill") || parentName.contains("seedling")) {
                return true;
            }
            parent = ((View) parent).getParent();
        }

        return false;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 按关键词在已加载类中搜索
     */
    private List<String> findClassesByPattern(XC_LoadPackage.LoadPackageParam lpparam,
                                               String... keywords) {
        List<String> results = new ArrayList<>();
        try {
            String path = lpparam.appInfo.sourceDir;
            DexFile dexFile = new DexFile(path);
            Enumeration<String> entries = dexFile.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement();
                if (!entry.startsWith("com.android.systemui")
                        && !entry.startsWith("com.oplus.systemui")) continue;

                String lower = entry.toLowerCase();
                for (String kw : keywords) {
                    if (lower.contains(kw)) {
                        results.add(entry);
                        log("  模式匹配: " + entry);
                        break;
                    }
                }
            }
            dexFile.close();
        } catch (Throwable t) {
            log("  类搜索异常: " + t.getMessage());
        }
        return results;
    }

    /**
     * 获取方法的参数类型数组
     */
    private Class<?>[] getParameterTypes(Method method) {
        Class<?>[] types = method.getParameterTypes();
        return types != null ? types : new Class<?>[0];
    }

    private static void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }
}
