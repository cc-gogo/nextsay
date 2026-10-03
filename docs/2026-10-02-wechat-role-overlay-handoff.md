# NextSay 0.1.3：微信消息归属与候选窗保持

日期：2026-10-02（Asia/Shanghai）。基于 `1bbfc1429bb5e8d7406b4dde1ac18c287304cda4`，本次改动在当前工作目录，未推送或发布。

## 原因与用户批准的方案

- 微信原来逐个 OCR 文字行按左右边缘/中心推断角色。用户批准查看的测试画面中，右侧长气泡的文字左边缘进入“对方”阈值；白色附件卡片也不能只靠颜色判方向。
- 原复制回调明确调用 `closeQuick()`。自动 OCR 的 `hideForCapture()` 又关闭全部内容，结束只恢复浮球。这些是主动收起路径，不是已证明的崩溃。
- 用户批准局部修改：以气泡边界/尖角和头像所在侧的布局位置判双方；气泡内多行统一；最新对方连续消息为回复目标；最后为我时生成补充；归属不清提示检查；复制不收起，浮球切换收起，截图临时隐藏后恢复。

## 实现

- `WechatBubbleDetector` 在本地截图像素上提取标准浅色/深色、绿色气泡连通区域。用左右尖角优先判角色，再用外侧气泡锚点判方向；双侧都可能且没有方向证据时返回 UNKNOWN。头像列用于布局排除/锚点，不识别头像人物身份，不假设消息轮流发送。未增加截图上传。
- 微信每次解析都传入这些区域，OCR 文字至少 80% 落在唯一气泡中才归属，按气泡合并，而非文字行间距。未匹配/多重匹配保留 UNKNOWN、降低置信度。QQ 的原采集和 OCR 回退逻辑保留。
- 先匹配气泡再过滤页面元信息：气泡里的“10:30”“发送”是真实内容，不再消失；多重匹配的这些文字也保留 UNKNOWN。页面外的时间/控件文字仍过滤。
- 最新一条 UNKNOWN 时，repository 本地返回 `CHAT-ROLE-UNKNOWN`，不调用服务。提示长按浮球查看上下文、调整画面后刷新；没有新增手动角色编辑器。
- 提示词明确 me/other/unknown 含义；增加最近一组连续对方消息及其后的我方消息字段。最后 ME 使用 `continue_self`，不是回答自己的问题；快速窗和详细面板都显示补充表达提示。
- 复制成功只显示反馈，候选窗不关闭；外部触摸不关闭；点击浮球关闭并使请求失效。第一次拖动仍收起，也使请求失效。
- 截图临时将已附着内容设为不可见，而非移除，结束仅恢复仍附着的内容；期间用户关闭或离开应用不会被恢复成打开状态。截图期间新渲染也保持隐藏。
- 采集 ticket 和生成 epoch 避免已关闭/被更新的请求晚到后重新打开窗口。读取/生成期间仍允许点击浮球收起。
- 不清空历史、不覆盖 API 配置。版本 `0.1.3` / versionCode `4`。

## 验证证据与边界

- TDD：先观察回复目标、最新 UNKNOWN、外部触摸、气泡检测/多行、迟到采集、补充提示、气泡内时间/控件词及多重匹配时间的失败，再修复。
- 最新全量 Android JVM 测试：218 项，0 失败、0 错误、0 跳过；包含用户批准截图的 7 个气泡方向测试。完整构建和 AndroidTest APK 构建成功。
- 后端：18 项通过。独立只读审查提出的元信息过滤、拖动失效、详细面板提示问题及多重匹配残余问题均已修复并复查。
- APK v2 签名通过，证书与 0.1.2 相同；清单包名/版本/minSdk 核验通过。`git diff --check` 通过；既有 Android 弃用提示未消除。
- 初次 USB 尝试：HONOR RMO-AN00 已授权 ADB，但两次安装（主 APK/测试 APK）均返回 `INSTALL_FAILED_ABORTED: User rejected permissions`。没有绕过系统限制、卸载或清空配置。
- 初次尝试手机仍是 0.1.2 / versionCode 3，instrumentation 因测试包未安装而失败。随后用户明确要求重新尝试，结果见下节。
- 初次交付未调用真实模型 API；USB 重试后的单次真实生成见下节。全程未输入或发送微信消息、未读取 API Key。截图/XML 不提交仓库；手机临时测试文件已清理，电脑临时 fixture 保留以便重试。
- 任意字体缩放、壁纸/主题、媒体消息以及模型输出质量并未全面验证。标准气泡之外/方向证据不足会保守标 UNKNOWN，不能保证所有界面都可生成。

## 交付与后续

- APK：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\NextSay-0.1.3-debug.apk`
- 大小：56,693,838 字节。
- SHA-256：`B982BB56E3ED99ED90F906513EF4D673C04C57D1D787BA47AD7FF320531D1E9B`。
- 签名证书 SHA-256：`bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`。
- 用户准备好手机安装确认/USB 安装后再重试 `adb install -r`，不要先卸载。
- 真正 OCR 测试位于 `src/androidTest/.../ApprovedWechatOcrTest.kt`；仅在明确参数 `approvedWechatFixture=true` 且目标 app 外部文件目录有 `role-check.png` 时运行，使用用户批准的同一测试截图、核验角色列表，不调用 API 或操作聊天。
- 本机可设置 `NEXTSAY_TEST_SCREENSHOT` 为该批准截图路径重跑几何测试；不设置时该可选 fixture 测试跳过，其余合成/业务测试照常运行。
- 安装后需验证：长按查看双方角色；一次快速生成并确认补充提示；复制后候选仍在；触摸聊天区仍在；截图/输入状态变化后恢复；点浮球收起；拖动/切换应用后不会被晚到结果重开。测试期间仍不得发送消息。

## USB 重试：手机 OCR 已验证

- 用户再次连接 USB 并要求重试；同一 serial 的主 APK 和 AndroidTest APK 两次 `install -r` 均成功，不卸载主应用、不清空配置。
- `dumpsys package app.nextsay` 确认 `versionName=0.1.3`、`versionCode=4`；所装 APK SHA-256 与上方交付文件相同。
- 手机执行 `am instrument -w -e approvedWechatFixture true -e class app.nextsay.ocr.ApprovedWechatOcrTest app.nextsay.test/androidx.test.runner.AndroidJUnitRunner`：`OK (1 test)`，测试耗时 0.65s。
- 该测试在手机上真实运行 ML Kit，再按气泡解析；批准截图中七条消息角色全部符合预期（ME, ME, OTHER, ME, OTHER, ME, ME）。不仅是合成坐标或电脑像素测试。
- instrumentation 开始/结束会强制停止目标测试进程。系统 exit-info 明确为 `USER REQUESTED / FORCE STOP / start instr / finished inst`，不是本次 App 异常崩溃；重新打开 NextSay 后无障碍服务自动重连，`Crashed services` 为空，无需修改系统权限。
- 用户再次确认同一无隐私测试聊天可以继续。长按浮球查看真实采集预览：七条消息的角色与截图左右归属一致，最后两条长消息均为 ME。
- 仅生成一次候选：安全诊断中 `0.1.3 / OVERLAY / api.deepseek.com / deepseek-flash / GENERATION_SUCCEEDED / durationMillis=1158`。候选窗显示 ME 补充表达提示。
- 点击候选两次（不重复生成）验证：出现“已复制”反馈，三条候选一直保留。点击微信气泡外时间标签区域后候选仍在，页面时间标签展开。点击浮球后窗口收起，等待后没有重新弹出。
- 未输入、粘贴或发送微信消息。复制测试会将剪贴板替换成候选文本；没有读取原剪贴板。
- 新发现的验收问题：候选窗打开时两次点击微信输入框均未显示键盘；系统记录 `mShowRequested=true / mInputShown=true`，但 InputMethod 窗口 `mViewVisibility=0x8 / mHasSurface=false / isVisible=false`。收起候选窗后，点击同一位置出现键盘及微信输入光标，表明这是窗口展开相关的行为差异。暂不能宣称键盘共存/截图恢复验收通过。
- 根因候选：`quickParams()` 持续允许焦点，外部触摸改为不关闭后，没有交还焦点机制。尚未通过最小变更 A/B 确认具体根因，不能把推测当作已完成修复。本轮未修改产品代码。
- 下一步局部设计待用户确认：候选窗默认不抢输入焦点；仅点击小窗输入框时获取焦点/键盘；复制或点微信区域时交还焦点但不关闭候选。增加自动测试，再以真实键盘操作验收；不扩大到自动发送。
- 本轮手机临时 fixture 和单独测试 APK 已清理，主应用 0.1.3 保留。
