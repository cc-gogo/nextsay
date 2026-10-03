# NextSay 快捷输入与外部点击收起 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在快捷三候选上方增加自由输入与重新生成，并让一次外部点击立即收起浮层而不吞掉聊天应用点击。

**Architecture:** `QuickReplyViews` 改为稳定的输入行加可替换内容区，渲染状态时不重建输入框，因此文字跨加载、结果与错误状态保留。新增纯 Kotlin 交互策略隔离外部触摸判断；`OverlayWindow` 负责可聚焦窗口、软键盘和关闭生命周期，服务层复用现有 `OverlayController.generate(instruction)` 数据流。

**Tech Stack:** Kotlin、Android Accessibility Overlay、WindowManager、JUnit 4、Gradle。

## Global Constraints

- 输入框接受任意文字，不增加模式选择。
- 点击“生成”后保留输入内容，并原地替换三条候选。
- 外部第一次点击立即关闭浮层，同时继续交给 QQ/微信。
- QQ 系仍复制并尝试写入；微信仍只复制；绝不自动发送。
- 保留 52dp 浮球与自绘“已复制”。
- 不做 Git commit 或 push。

---

### Task 1: 快捷交互状态与外部触摸策略

**Files:**
- Create: `android-app/src/main/java/app/nextsay/overlay/QuickReplyInteraction.kt`
- Create: `android-app/src/test/java/app/nextsay/overlay/QuickReplyInteractionTest.kt`

**Interfaces:**
- Produces: `QuickReplyInteraction.submit(text: String): String` 保存并返回当前自由输入；`retryText(): String` 返回最后提交内容；`shouldDismiss(action: Int): Boolean` 仅对 Android `ACTION_OUTSIDE` 数值返回 true。

- [ ] 写失败测试，覆盖任意文本原样提交、空文本可提交、重试保留最后文本、内部触摸不关闭、外部触摸关闭。
- [ ] 运行定向测试并确认缺少类型导致失败。
- [ ] 实现最小纯 Kotlin 状态和策略。
- [ ] 重跑定向测试并确认通过。

### Task 2: 稳定输入行与候选内容区

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/QuickReplyViewFactory.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/QuickReplyPresenterTest.kt`

**Interfaces:**
- Produces: `QuickReplyCallbacks.onGenerate(String)`、`onRetry(String)`、`QuickReplyViews.clearFocus()`；输入框和生成按钮在所有可见状态保持存在。

- [ ] 先更新测试使新回调签名缺失并确认红灯。
- [ ] 将根布局拆为输入行与 `content` 容器；`render()` 只重建内容容器。
- [ ] 输入框提示为“告诉 AI 你想怎么回…”，生成按钮传递全文；空文本不拦截。
- [ ] 加载时禁用按钮，结果与错误状态恢复；重试传递保存的最后提交内容。
- [ ] 渲染状态不得调用 `setText`，确保输入跨状态保留。
- [ ] 运行快捷回复相关测试并确认通过。

### Task 3: 窗口焦点、键盘与外部点击关闭

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`

**Interfaces:**
- Consumes: `QuickReplyInteraction`、`QuickReplyViews.clearFocus()`。
- Produces: `OverlayWindow` 新增 `onQuickGenerate: (String) -> Unit`；快捷窗口接收 `ACTION_OUTSIDE` 并关闭。

- [ ] 快捷窗口去除 `FLAG_NOT_FOCUSABLE`，加入 `FLAG_WATCH_OUTSIDE_TOUCH | FLAG_NOT_TOUCH_MODAL`，配置 `SOFT_INPUT_ADJUST_RESIZE`。
- [ ] 根视图监听触摸；策略判定外部触摸时调用统一 `closeQuick()`。
- [ ] `closeQuick()` 清除输入焦点并通过 `InputMethodManager` 隐藏键盘，保持幂等。
- [ ] 点击生成时保存输入、隐藏键盘但不关闭窗口，再调用 `onQuickGenerate(text)`。
- [ ] 点击重试传递最后输入；拖动、离开应用、捕获、复制和销毁继续走统一关闭路径。
- [ ] 运行悬浮手势、位置、快捷回复与交互策略测试。

### Task 4: 服务层重新生成数据流

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/OverlayControllerTest.kt`（仅复用现有 instruction 断言，无需新增 Android 服务测试）。

**Interfaces:**
- Consumes: `OverlayWindow(onQuickGenerate = ::regenerateQuickReply)`。
- Produces: `regenerateQuickReply(instruction: String)` 在当前 QUICK surface 和已有上下文上调用 `controller.generate(instruction)`。

- [ ] 接入 `onQuickGenerate`；拒绝非支持前台应用或不存在有效上下文的请求。
- [ ] 重新生成不重新截图/OCR，直接复用当前 `OverlayController` 的上下文。
- [ ] 快捷错误重试改为相同输入重新生成，而非重新捕获并清空输入。
- [ ] 运行控制器、服务依赖编译和完整 JVM 测试。

### Task 5: 构建、安装与真机验证

**Files:**
- Verify: `android-app/build/outputs/apk/debug/android-app-debug.apk`

- [ ] 运行全量 JVM 测试、lint 和 debug 构建，并显式传入 `http://127.0.0.1:8000/` 与 `local-dev-token`。
- [ ] 覆盖安装 APK，恢复 `adb reverse tcp:8000 tcp:8000`，确认无障碍服务启用。
- [ ] QQ 验证：打开候选、输入自由文字、键盘出现、生成后文字保留且三候选更新、候选写入但不发送。
- [ ] QQ 验证：点击浮层外部一次，浮层与键盘立即收起，QQ 同一点击正常响应。
- [ ] 微信验证：外部点击收起；自由输入生成可用；候选只复制。
- [ ] 检查日志无 `FATAL EXCEPTION`，且不记录输入或候选正文。
