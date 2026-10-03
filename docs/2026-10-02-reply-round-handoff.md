# NextSay 0.1.6：单击刷新与补充要求轮次

日期：2026-10-02，Asia/Shanghai。当前工作目录实现；没有提交、推送或发布。承接 0.1.5 的独立编辑窗，保留配置、历史、角色识别、复制后保留候选和后台 OCR 暂停规则。

## 行为

- 候选打开时点浮球，先读取当前聊天，再判断是否有新一轮对方消息。识别到新一轮时一次点击生成，不必先收起再点击。
- 同一轮且没有新对方消息时，浮球收起候选；再次打开仍保留已完成保存的补充要求。
- 新轮次或不同聊天同时清空界面要求和实际请求要求；同轮改写、失败重试保留。自己发出消息本身不启动下一轮。
- 小窗“生成”和“重试”也重新读取当前聊天，不再直接使用旧 reviewed context。
- 草稿、置信度、历史截断、键盘导致的消息子集以及增加更早历史前缀，不单独启动新轮次。
- 读取和生成期间禁用要求编辑和生成入口，防止读取中完成的新编辑被旧快照覆盖。浮球仍可取消。不同入口共享 busy epoch，旧请求的 finally 不会解锁更新的请求。

## 实现与隐私

- `QuickReplyFlow.kt`：用户触发的读取、消息锚点比较、收起/生成和要求同步。按角色与去掉多余空白的文字匹配，不使用 draft/confidence 作为消息身份。最多保留 100 条内存锚点。
- `ReplyRoundSnapshot`：聊天标题、当前可见消息和滚动 revision 都是本机瞬态信息，`ChatContext.replyRound` 标记为 `@Transient`。标题和未截断画面不进入 Gson 序列化，也不进入显式构造的 `ReplyRequestDto`。
- coordinator 保留原始可见消息，不拿合并/截断后的历史判断轮次；滚动 revision 在 history merge 之前取值。页面 epoch 防止读取期间画面变化后使用旧结果。
- `ScrollRevisionTracker` 独立记录 `TYPE_VIEW_SCROLLED`。列表条数相同、减少或未知时按画面移动处理；正数条数增长视为可能的新消息自动滚动。切换应用/已识别的聊天会重置条数基线；首次绑定标题保留同应用已有的条数证据。
- 滚动事件令缓存失效，但不启动后台 OCR。用户点击时强制读取，避免遗漏事件时继续使用旧缓存。
- 未改 Provider、API Key 保存、历史数据库结构或 QQ 写入机制。

## 识别边界

这是基于当前画面和事件的启发式判断，不是微信/QQ 消息 ID 或到达时间：

- 同一聊天的画面完全无重叠时无法可靠区分新回复和旧记录。保留补充要求及原有锚点，直到重叠或不同聊天建立依据；“生成”仍使用当前读取的内容。
- 滚动事件没有可靠列表条数时，也可能是新消息引发的自动滚动。此时优先不误清空要求，可能需要手动点“生成”，必要时自行清空要求。
- 正数列表条数增长只是可能新增的证据，不是唯一消息身份。相同标题的不同聊天、重复的相同文字仍有识别限制。
- 不承诺任意滚动/任意 OCR 条件下都能无条件一次点击判定新消息。

## 测试驱动与审查

- 首先证明不同标题的相同聊天文字会丢失本机身份，回归断言失败后增加瞬态 metadata。
- 轮次流程回归在尚未加入判定的基线下有 9 项断言失败；包括新轮次清空、同轮收起、新聊天、最新上下文和轮次锚点。
- 审查发现读取中编辑的竞争。在原安装代码上新增手机测试，明确得到 `Capture must prevent a late editor...` 断言失败，随后加入 busy/Loading 联合入口锁。
- 审查发现向下滚动 `[A,B,C] → [C,D,E]` 不能仅凭重叠认定新到达。新增滚动依据与回归；保留要求，再在同 revision 的真正追加消息上清空。
- 无重叠且 revision 改变时仍保留旧锚点；滚动使缓存失效但不自动截图；首次标题绑定保留列表证据。三项后续回归先失败再修正。
- 独立只读审查及复审无剩余 Critical/Important 问题；审查明确要求交付说明识别边界。

## 最终验证

- Android JVM 全量：250 tests，0 failure/error/skip，包含之前批准的截图几何 fixture。
- 后端全量：18 passed；没有修改后端。
- 主 APK 与 AndroidTest APK 构建成功。
- HONOR RMO-AN00 / Android 13 instrumentation：`OK (13 tests)`，30.569 秒，无失败或跳过。
  - 1 项真实 ML Kit OCR，使用之前批准的本地截图 fixture，七个气泡角色仍正确。
  - 12 项真实 Android IME/OverlayWindow 本地模拟页面测试：保留原有键盘/复制/取消/长文本/模态/待执行键盘请求测试，增加触发器先委托检查、一次点击新消息、同轮收起重开、生成按钮更新上下文和读取期入口锁。
- 本地轮次 UI 测试使用真实 flow/controller/window，读取和生成边界使用合成聊天与本地候选，未访问真实模型。
- Activity fixture 只适配窗口 token/type，不替代真实微信 accessibility overlay 的整链路验收。
- 本轮没有读取微信画面、API Key 或无关聊天；没有输入、粘贴或发送微信消息；没有调用真实 API。因此真实微信新消息整链路尚未验收。
- 复制回归将剪贴板替换为本地测试候选，未读取原剪贴板。
- 临时包 `app.nextsay.test` 和手机上的 `role-check.png` 已清理；可由电脑上的测试 APK/原 fixture 恢复。主应用和已有数据保留。

复现构建/电脑测试：

```powershell
$env:JAVA_HOME='D:\agent\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
$env:NEXTSAY_TEST_SCREENSHOT='C:\Users\ASUS\AppData\Local\Temp\nextsay-role-check-20261002\screen.png'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug :android-app:assembleDebugAndroidTest --no-daemon
$env:PYTHONPATH='D:\agent\codex\nextsay\backend\src'
.\.venv\Scripts\python.exe -m pytest backend\tests -q
```

手机测试需重新安装测试 APK，并推送之前批准的 fixture；不涉及真实聊天或模型生成：

```powershell
.\.android-sdk\platform-tools\adb.exe -s A9GVVB2A12014220 install -r -t android-app\build\outputs\apk\androidTest\debug\android-app-debug-androidTest.apk
.\.android-sdk\platform-tools\adb.exe -s A9GVVB2A12014220 push $env:NEXTSAY_TEST_SCREENSHOT /sdcard/Android/data/app.nextsay/files/role-check.png
.\.android-sdk\platform-tools\adb.exe -s A9GVVB2A12014220 shell am instrument -w -r -e approvedWechatFixture true app.nextsay.test/androidx.test.runner.AndroidJUnitRunner
```

## 交付

- 版本：0.1.6 / versionCode 7，debug 包，非正式发行签名。
- 路径：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\NextSay-0.1.6-debug.apk`。
- 大小：56,734,462 字节。
- SHA-256：`3AD8F1DBBDD9550DB4625F4634C1356B12A08260E1414FFA7B3E6577D00B41DD`。
- APK v2 签名验证通过；证书 SHA-256：`bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`，与此前相同。
- 已覆盖安装。手机 `pm` 显示 0.1.6/code7，installed base.apk SHA-256 与交付文件一致。
