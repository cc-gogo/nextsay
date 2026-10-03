# NextSay 0.1.4：悬浮窗闪烁与键盘焦点

日期：2026-10-02。当前工作目录实现，未提交、推送或发布。承接 0.1.3 微信角色修正，不清空配置、历史或读取 API Key。

后续更新：用户已批准独立补充要求编辑窗，0.1.5 已实现并完成微信真机验收；见 `2026-10-02-independent-instruction-editor-handoff.md`。下文保留 0.1.4 当时的失败证据与历史状态，不是当前交付结论。

## 证据与批准方案

- 0.1.3 真机窗口元数据采样 15 秒：候选和浮球约每 2 秒变为 `mViewVisibility=0x4`，约 0.4–0.6 秒后恢复；PID 始终为 9381。不是已证明的崩溃。
- 源码中微信自动 OCR 每次 `hideForCapture()` 隐藏窗口，截图、识别结束后 `restoreAfterCapture()` 恢复。原刷新策略没有区分内容和窗口可见性事件。确认直接闪烁机制；没有采集完整事件时间线，不能将自身窗口事件反馈链称为已单独实测。
- 用户批准：过滤自身窗口变化引发的自动刷新；候选展开时暂停后台 OCR；默认不抢焦点，只在点击小窗输入框时获取，外部点击/复制交还焦点但不关闭。只查看同一无隐私微信测试聊天，不输入、粘贴或发送消息。

## 实现

- `ForegroundEventPolicy` 分离缓存失效与自动采集：同包 `TYPE_WINDOWS_CHANGED` 不失效/调度；内容变化失效并在窗口关闭时调度；窗口状态变化只失效，不自动截图；进入支持应用允许预采集。
- 候选或详细面板展开时仍记录真实内容变化为 dirty，但不启动后台 OCR。已排队的任务在采集前再次检查窗口状态。下次收起后重新打开会获取最新 dirty 上下文；窗口中的“生成”仍是对已查看上下文的改写，不隐式重新截图。
- 小窗默认 `FLAG_NOT_FOCUSABLE`。只在点其 instruction 输入框时更新窗口参数并等待窗口焦点，再请求输入焦点/键盘；释放时取消待执行 runnable 和窗口焦点 listener，避免迟到抢焦点。
- 复制、提交、关闭只在小窗拥有焦点时主动收起其键盘。点窗口外只交还焦点，不调用主动 hide，避免压过微信的键盘显示请求。窗口始终保留，浮球切换才收起。
- 版本 `0.1.4` / versionCode `5`。QQ 采集/写入、Provider 及角色检测没有新增修改。

## 验证与剩余边界

- TDD：策略与焦点测试先补接口得到可执行断言失败（6 个失败），实现后通过；随后新增“外部交还不隐藏正在请求的微信键盘”测试，先失败再修正。
- 最终全量 Android JVM：228 项，0 fail/error/skip（包含已批准截图的本地几何 fixture）。主 APK 与 AndroidTest APK 构建成功。既有 `SOFT_INPUT_ADJUST_RESIZE` 弃用警告仍存在。
- 后端初次运行由于本机迁移后的导入路径报 4 个 collection error；指定 `PYTHONPATH=D:\agent\codex\nextsay\backend\src` 后，完整 18 项通过。未改后端代码。
- 只读审查未发现 Critical/Important 问题；指出纯策略测试不等于 WindowManager/IME 集成验证，需真机覆盖。
- 第一份 0.1.4 APK 覆盖安装成功。真实微信页浮球静置观察 12 秒无可见性变化；一次用户授权生成成功 `822ms`；候选+浮球静置观察 15 秒无隐藏/恢复。点击复制有反馈，三条候选保持；微信输入框可一次点出真实键盘，候选同时显示；小窗输入框可接管键盘，系统 served view 为 EditText。
- 第一份 APK 发现小窗输入后点回微信，served view 已返回微信但 `mInputShown=false`，需要再点；据此补最后的 `hideKeyboard=false` 外部交还修正。
- 上轮最终 APK 两次 USB 覆盖安装均返回 `INSTALL_FAILED_ABORTED: User rejected permissions`，未绕过、卸载或清空配置。随后重试结果见下节。
- 上轮结束时手机是第一份 0.1.4，base.apk SHA-256 为 `C40E408DED0B57D6A79DAA2DD9B7E22677778D7C65E2E2EB3088129D943D04B8`。
- 用户额外授权最终安装后再生成一次候选，在本轮已使用，不得再次调用 API 测试而不获新授权。
- 测试截图只在电脑临时目录/手机 app 外部测试目录，不加入仓库。未读取原剪贴板；复制测试替换了剪贴板。

## 本轮继续：安装成功，键盘交还仍未通过

- 用户回复“继续”后，最终文件安装成功，installed SHA-256 确认为 `8AA44724E0677D2E9FF03FF60697EA195DF88F731904779182C7A174C8750B10`；配置保留。手机仍是同一批准微信测试聊天。
- 消耗上轮剩余的一次授权：`GENERATION_SUCCEEDED / deepseek-flash / 1260ms`。候选+浮球从打开起采样 15 秒一直 `view=0x0`，无周期闪烁。
- 小窗编辑器可得到键盘；单击微信输入框后 served view 已返回微信，但 `mShowRequested=false / mInputShown=false`，IME surface 不可见。`hideKeyboard=false` 并未解决系统自动隐藏，不能称最终键盘验收通过。
- 系统安全元数据确认本次隐藏为 `HIDE_UNSPECIFIED_WINDOW`，request/focused window 为微信，不是 NextSay 主动 `HIDE_SOFT_INPUT`；记录中出现小窗到非输入控件的 `inputType=0` 中间状态。没有输出输入内容、API Key 或私人聊天记录。
- 新增 `QuickOverlayKeyboardTest.singleOutsideTapKeepsUnderlyingEditorKeyboard`，用 Activity 内的本地输入页面、合成候选、真正的 `OverlayWindow` 与 Android IME，只有 window type/token 适配为 Activity panel。采用与微信观察一致的 `STATE_UNSPECIFIED / ADJUST_NOTHING`，输入放在 IME 上方，断言防止点到键盘。没有调用 API 或操作微信消息。
- 最初使用运行中无障碍服务对象的测试在 instrumentation 期间未能重连，未达到回归断言，因此删除该无效测试 fixture；本地 fixture 的初始布局/窗口读取与遮挡错误也先修正，不能把这些基础设施失败算成产品回归证据。
- 正确本地 fixture 在当前产品代码上明确失败：`Single outside tap must keep underlying keyboard visible`。第三种局部尝试（外部点击只取消 pending 输入焦点，而不立即 clearFocus）也在同一断言失败。该产品尝试已撤回，代码恢复交付文件对应实现；保留真实失败的回归测试，未跳过或改成通过。
- 按 systematic-debugging 的连续三次局部处理失败规则，不再叠加第四个焦点补丁；需要用户确认是否将候选展示与补充要求编辑分离。当前仅能保证默认不抢键盘；从补充要求输入直接切回微信仍需再次点击。
- 撤回实验后完整 JVM 命令成功（228 项结果未变化）；后端完整 18 项通过。Android 真机新增回归仍失败，不能笼统称“全部测试通过”。
- 用户完成指纹确认，恢复交付 APK 的覆盖安装返回 Success；installed hash 为 `8AA44724E0677D2E9FF03FF60697EA195DF88F731904779182C7A174C8750B10`，已恢复。实验 APK（hash `FB5A688FF21825A41727A92DEEBB51BB3289043F5D3B28EEDA39B5E395E6A762`）不作为交付。
- 临时测试包 `app.nextsay.test` 已卸载，不卸载主应用。测试 APK 可从构建产物重新安装，回归源代码保留。

## 最终安装包

- 路径：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\NextSay-0.1.4-debug.apk`
- 大小：56,699,290 字节。
- SHA-256：`8AA44724E0677D2E9FF03FF60697EA195DF88F731904779182C7A174C8750B10`。
- APK v2 签名验证通过，证书 SHA-256：`bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`，与之前相同。
- 不再无提示重复安装。请用户确认手机允许安装后重试 `adb install -r`；成功后核验 installed hash，再进行剩余已授权的一次候选测试。
