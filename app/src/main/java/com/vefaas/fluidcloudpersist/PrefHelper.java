package com.vefaas.fluidcloudpersist;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * 模块配置读取器
 * 通过 AndroidAppHelper 获取 SystemUI 的 Application Context，
 * 读取 MODE_WORLD_READABLE 的 SharedPreferences。
 */
class PrefHelper {

    static final String PREFS_NAME = "module_prefs";
    static final String KEY_MODULE_ENABLED = "module_enabled";
    static final String KEY_FORCE_SHOW = "force_show";
    static final String KEY_LANDSCAPE_ONLY = "landscape_only";

    private static volatile SharedPreferences prefs;

    /**
     * 通过 AndroidAppHelper 初始化（SystemUI 环境调用）
     */
    static void init() {
        try {
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            if (app != null) {
                prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 通过传入的 Context 初始化（SettingsActivity 调用）
     */
    static void init(Context context) {
        if (context == null) return;
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
    }

    private static void ensureLoaded() {
        if (prefs == null) {
            try {
                init();
            } catch (Throwable ignored) {
            }
        }
    }

    static boolean isModuleEnabled() {
        ensureLoaded();
        if (prefs == null) return true;
        return prefs.getBoolean(KEY_MODULE_ENABLED, true);
    }

    static boolean isForceShow() {
        ensureLoaded();
        if (prefs == null) return true;
        return prefs.getBoolean(KEY_FORCE_SHOW, true);
    }

    static boolean isLandscapeOnly() {
        ensureLoaded();
        if (prefs == null) return true;
        return prefs.getBoolean(KEY_LANDSCAPE_ONLY, true);
    }

    /**
     * 检查当前是否为横屏方向
     */
    static boolean isLandscape(Context ctx) {
        if (ctx == null) return false;
        try {
            Configuration config = ctx.getResources().getConfiguration();
            return config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        } catch (Throwable t) {
            return false;
        }
    }

    static void refresh() {
        prefs = null;
        ensureLoaded();
    }
}
