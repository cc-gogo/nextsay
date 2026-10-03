# NextSay 自绘复制提示与小尺寸浮球 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用无障碍悬浮层稳定显示“已复制”，并把浮球从 64dp 缩到 52dp。

**Architecture:** 新增纯 Kotlin 的提示定位策略，依据屏幕、浮球和提示尺寸将提示放在浮球靠屏幕内侧并钳制边界。`OverlayWindow` 持有不可触摸的提示窗口，负责显示、800ms 自动移除和生命周期清理；现有快捷复制处理器只把复制成功回调接到该窗口。

**Tech Stack:** Kotlin、Android Accessibility Overlay、JUnit 4、Gradle Android Plugin。

## Global Constraints

- 成功文案必须精确为“已复制”。
- 提示显示约 800ms，不依赖系统 Toast 或通知权限。
- 浮球尺寸必须为 52dp × 52dp。
- 不改变 QQ 系写入、微信只复制以及不自动发送行为。
- 不做 Git commit 或 push。

---

### Task 1: 可测试的悬浮提示定位策略

**Files:**
- Create: `android-app/src/main/java/app/nextsay/overlay/CopyFeedbackPosition.kt`
- Create: `android-app/src/test/java/app/nextsay/overlay/CopyFeedbackPositionTest.kt`

**Interfaces:**
- Produces: `CopyFeedbackPosition.calculate(screenWidth, screenHeight, triggerX, triggerY, triggerSize, feedbackWidth, feedbackHeight, gap): OverlayPosition`。

- [ ] 写失败测试：浮球在右半边时提示位于左侧，在左半边时位于右侧，并且 x/y 不越过屏幕边界。
- [ ] 运行 `:android-app:testDebugUnitTest --tests app.nextsay.overlay.CopyFeedbackPositionTest`，确认因类型缺失而失败。
- [ ] 实现最小纯 Kotlin 定位逻辑：优先屏幕内侧、垂直居中、最终钳制到 `[0, screen-size]`。
- [ ] 重新运行定向测试并确认通过。

### Task 2: 自绘复制反馈窗口与 52dp 浮球

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`

**Interfaces:**
- Consumes: `CopyFeedbackPosition.calculate(...)`。
- Produces: `OverlayViewFactory.copyFeedback(): TextView`、`OverlayWindow.showCopiedFeedback()` 和内部清理逻辑。

- [ ] 在 `OverlayViewFactory` 新增深色半透明圆角、小号白字、紧凑 padding 的“已复制”视图。
- [ ] 在 `OverlayWindow` 创建 `TYPE_ACCESSIBILITY_OVERLAY`、`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_NOT_TOUCH_MODAL` 的独立提示窗口。
- [ ] 测量提示后按定位策略显示，取消旧任务并在 800ms 后移除。
- [ ] 将快捷候选成功回调从系统 Toast 改为 `showCopiedFeedback()`，删除该文件的 Toast import。
- [ ] 在 `hideAllContent()`、离开支持应用和 `dispose()` 路径同步移除提示及回调。
- [ ] 将 `TRIGGER_SIZE_DP` 从 64 改为 52；保持拖动边界、位置恢复和快捷卡定位继续引用该常量。
- [ ] 运行浮球手势、位置、快捷回复和提示定位相关单元测试。

### Task 3: 全量验证和真机安装

**Files:**
- Verify: `android-app/build/outputs/apk/debug/android-app-debug.apk`

**Interfaces:**
- Produces: 已验证并安装的 debug APK。

- [ ] 运行 Android 全量 JVM 单元测试。
- [ ] 运行 Android lint。
- [ ] 构建 debug APK。
- [ ] 通过 USB/无线 ADB 覆盖安装并恢复 `adb reverse tcp:8000 tcp:8000`。
- [ ] 如覆盖安装导致无障碍服务被荣耀关闭，提示用户重新开启后继续。
- [ ] 在 QQ 真机点击浮球和候选，确认：浮球更小、候选收起、自绘“已复制”出现、输入框写入、不自动发送、无 `FATAL EXCEPTION`。
