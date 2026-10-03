# NextSay 0.1.5：独立补充要求编辑窗

日期：2026-10-02，Asia/Shanghai。当前工作目录实现；没有提交、推送或发布。承接 0.1.3 角色识别与 0.1.4 闪烁修正，保留 API 配置、历史和已保存浮球位置。

## 用户批准的交互

- 候选小窗不包含 EditText，始终 `FLAG_NOT_FOCUSABLE`。点“补充要求”才打开独立的输入窗。
- 独立输入窗是模态窗口：外部点击不穿透到微信，避免原来的跨窗口焦点竞争。使用“完成”或“取消”退出，不承诺编辑期间点浮球可以收起。
- “完成”保存要求并返回候选，不自动调用模型；“取消”丢弃本次草稿，保留原要求。下次“生成”或“重试”才使用保存的要求。
- 退出编辑后，点击微信输入框可正常调出键盘。复制候选后小窗保持，明确点浮球才收起。
- 要求文本仍是当前 OverlayWindow 的内存状态，不新增持久化存储，也不覆盖 API 设置。

## 实现

- `QuickReplyViewFactory.kt`：以可点击的 TextView 显示保存要求，移除候选窗中的输入框、输入焦点 listener 与延迟键盘请求。
- `QuickInstructionEditor.kt`：独立输入视图、完成/取消/Back 回调；只在自己的窗口得到焦点后请求 IME；关闭时取消 runnable/listener，并仅释放自己拥有的键盘。
- `OverlayViewFactory.kt`：管理独立窗口的添加/移除，候选参数固定非焦点；收起、加载/重绘、切换详细面板、截图和 dispose 都会关闭编辑窗并取消未完成草稿。
- 多行设置在 `minLines=2 / maxLines=4` 之前执行，避免 `setSingleLine(false)` 重置行数上限。长文本可在输入框内滚动，动作按钮不被挤走。
- 版本 `0.1.5 / versionCode 6`。没有新增 QQ 写入、Provider、后端或角色算法变化。

## 测试驱动与修复记录

- 首先在安装的 0.1.4 上运行新测试，得到明确断言失败：候选窗仍含 editable input。
- 原来的 `singleOutsideTapKeepsUnderlyingEditorKeyboard` 属于已被用户批准替换的同窗输入交互。它不是通过跳过断言变绿：新设计不允许模态编辑期间点击穿透，验收改为“完成后一次点击微信”，并单独测试外部点击被阻止。
- 第一份实验构建包含旧的密度初始化代码，真机在构造视图时空指针，尚未到键盘断言。源码已将 density 提前初始化；重新构建、核对 installed APK 哈希后，该错误不再出现。实验包不作为交付。
- 一次 fixture 检查在生成回调尚未执行时立即读结果，得到空列表；已加入输入事件空闲等待和主线程条件等待，没有放松生成内容断言。
- 审查发现多行设置重置最大行数。先增加 80 行要求的测试，在旧代码中“完成”按钮零尺寸导致断言失败，再修正调用顺序。
- 测试只在内存里调整浮球窗口坐标，不写入用户位置偏好；注入点击前检查目标尺寸、IME 遮挡，以及底层输入与候选/模态窗口不重叠。
- 独立只读代码审查及修正复审没有剩余 Critical/Important 问题。审查不能替代真机测试。

## 最终验证结果

- Android JVM 全量：228 项，0 failure/error/skip，包含已批准截图的几何 fixture。
- 后端全量：18 passed，使用 `PYTHONPATH=D:\agent\codex\nextsay\backend\src`；未改后端代码。
- 主 APK、AndroidTest APK 构建成功。
- 手机上完整 instrumentation：`OK (8 tests)`，19.866 秒；无失败或跳过。
  - 1 项真实 ML Kit OCR：已批准截图中全部七个气泡角色保留。
  - 7 项真实 Android IME/OverlayWindow 本地输入页面测试：完成后一次点击、复制保留、取消保留旧要求、收起清理草稿、80 行要求、模态外部点击、取消待执行键盘请求。
- Activity fixture 仅适配 window type/token 为 application panel；不将其结果独自当作微信 accessibility overlay 验收。

复现命令（临时测试包需重新安装，截图 fixture 需重新推送）：

```powershell
$env:JAVA_HOME='D:\agent\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
$env:NEXTSAY_TEST_SCREENSHOT='C:\Users\ASUS\AppData\Local\Temp\nextsay-role-check-20261002\screen.png'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug :android-app:assembleDebugAndroidTest --no-daemon
$env:PYTHONPATH='D:\agent\codex\nextsay\backend\src'
.\.venv\Scripts\python.exe -m pytest backend\tests -q
.\.android-sdk\platform-tools\adb.exe -s A9GVVB2A12014220 install -r -t android-app\build\outputs\apk\androidTest\debug\android-app-debug-androidTest.apk
.\.android-sdk\platform-tools\adb.exe -s A9GVVB2A12014220 push $env:NEXTSAY_TEST_SCREENSHOT /sdcard/Android/data/app.nextsay/files/role-check.png
.\.android-sdk\platform-tools\adb.exe -s A9GVVB2A12014220 shell am instrument -w -r -e approvedWechatFixture true app.nextsay.test/androidx.test.runner.AndroidJUnitRunner
```

## 微信最终真机验收

- 手机 HONOR RMO-AN00 / Android 13，installed APK 与交付哈希一致。
- 用户额外授权同一无隐私微信测试聊天的一次生成，已使用且成功显示三条候选。不得继续调用 API 测试而不获新授权。
- 中途观察到补充要求、候选和聊天画面变化，与用户操作可能重叠；立即暂停点击。获得用户暂不操作手机的确认后，重新检查当前画面并进行独占操作验证。以下验收基于这一段，而不是混合操作。
- 打开独立编辑窗能调出键盘；保留用户已有要求，不输入文本。点“完成”后 modal 退出，候选保持；此时键盘正常收起，`mInputShown=false`。
- 仅点击一次微信输入框，`mShowRequested=true / mInputShown=true`，served view 为 `com.tencent.mm.ui.widget.MMEditText`；截图显示真实键盘和三条候选同时存在。
- 点击候选复制：有“已复制”反馈，候选不关闭，微信键盘仍 `mInputShown=true`。剪贴板被测试候选替换，未读取原剪贴板。
- 约 15 秒窗口元数据采样：两个可见 NextSay 窗口始终 `0x0`，另两个原本隐藏窗口始终 `0x8`，没有可见性切换；未观察到原来的周期闪烁。
- 点浮球：候选收起，微信键盘保持；随后按一次 Back 收起键盘，手机留在同一测试聊天、只显示浮球。
- 助手没有输入、粘贴或发送任何微信消息，没有读取 API Key 或其他聊天。
- 本机截图证据目录：`C:\Users\ASUS\AppData\Local\Temp\nextsay-015-check-20261002`。重点文件：`ui-015-editor-stable.png`、`ui-015-done-stable.png`、`ui-015-wechat-keyboard.png`、`ui-015-copy.png`、`ui-015-collapsed.png`、`ui-015-final.png`。不加入仓库。
- 临时测试包 `app.nextsay.test` 已卸载；主应用、配置、历史保留。手机本轮临时截图和 OCR fixture 清理，电脑测试证据保留。

## 交付安装包

- 路径：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\NextSay-0.1.5-debug.apk`
- 大小：56,721,106 字节。
- SHA-256：`D7692347A58DCB2D9E22E8E69704A0C80BDFFEFBAC6120FAF2654D96C75A60A5`。
- APK v2 签名验证通过，证书 SHA-256：`bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`，与此前相同。
- 这是 debug 安装包，不是正式发行签名。0.1.4 文档中的未解决同窗焦点问题为历史状态，当前结果以本文为准。
