# FluidCloud Persist 流体云横屏常驻

一个 Xposed/LSPosed 模块，强制 OPPO ColorOS 的**流体云胶囊**在横屏/沉浸模式下常驻显示。

## 问题

在横屏游戏或视频场景下，状态栏会自动收起隐藏，流体云胶囊也随之消失。用户只能通过下拉状态栏才能短暂看到流体云，松手即消失。

## 解决方案

本模块通过 Hook ColorOS SystemUI 中控制流体云胶囊显隐的类与方法，在状态栏自动收起时强制保持胶囊可见，实现横屏模式下的流体云常驻显示。

## 功能

- **强制显示**：状态栏自动收起时保持流体云胶囊可见
- **横屏模式**：仅在横屏/沉浸模式下生效，竖屏保持系统默认行为
- **多策略 Hook**：四层 hook 策略确保兼容性
  - 策略 1：Hook ColorOS 胶囊控制器类（核心）
  - 策略 2：Hook 状态栏收起方法（兜底）
  - 策略 3：Hook Window 可见性变化
  - 策略 4：View.setVisibility 安全网
- **可配置**：支持开关模块、强制显示模式、仅横屏模式

## 适配

| 系统 | 版本 |
|------|------|
| ColorOS | 14 / 15 |
| realme UI | 5.0 / 6.0 |
| OxygenOS | 14 / 15 |

## 要求

- Root 权限
- LSPosed 或 EdXposed 框架
- ColorOS 14+ / realme UI 5.0+

## 安装

1. 下载最新 Release 的 APK
2. 安装 APK
3. 打开 LSPosed，在模块列表中启用 **FluidCloud Persist**
4. 勾选作用域：**System UI** (`com.android.systemui`)
5. 重启手机
6. 打开 FluidCloud Persist 设置界面，按需调整选项

## 设置项

| 选项 | 说明 | 默认 |
|------|------|------|
| 启用模块 | 总开关 | ✅ |
| 强制显示模式 | 状态栏收起时强制保持胶囊可见 | ✅ |
| 仅横屏模式 | 只在横屏/沉浸模式下生效 | ✅ |

## 技术细节

### Hook 策略

```
─────────────────────────────────────────────┐
│              ColorOS SystemUI               │
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │  策略1: CapsuleController           │   │
│  │  hook hide/dismiss/collapse 方法    │   │
│  │  → afterHook: forceViewVisible()    │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │  策略2: StatusBar collapse          │   │
│  │  hook animateCollapsePanels()       │   │
│  │  → beforeHook: setResult(null)      │   │
│  └─────────────────────────────────────┘   │
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │  策略4: View.setVisibility          │   │
│  │  安全网：拦截 capsule 类 View 的     │   │
│  │  GONE/INVISIBLE，改为直接修改 flag   │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### 构建

```bash
# 依赖 JDK 17 + Android SDK 35
./gradlew assembleDebug
```

## 注意事项

- 本模块通过修改 SystemUI 行为实现，不同 ColorOS 版本的混淆类名可能不同
- 如果流体云胶囊仍然消失，请提交 Issue 并附上 Xposed 日志
- 强制显示模式可能略微影响沉浸体验
- 模块仅在 SystemUI 进程中运行，不影响其他应用

## License

MIT
