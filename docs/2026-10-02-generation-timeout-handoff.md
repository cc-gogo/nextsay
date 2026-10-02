# NextSay 0.1.2：快速回复与生成超时修复

日期：2026-10-02（Asia/Shanghai）

## 手机证据与诊断

用户的 0.1.1 日志显示：

- DeepSeek 连接测试成功，分别耗时 982ms、721ms。
- 正式生成已使用 `deepseek-flash`，有一次成功，耗时 7691ms；因此默认模型提示的旧名字不是本次请求的实际模型。
- 后续出现 `NET-CONNECT`，包括约 4.6s、13.8s、59.2s、42.4s；部分紧接着另有不含服务元数据的浮窗 `NET-TIMEOUT`。
- 旧实现正式生成不关闭 DeepSeek 默认思考，HTTP 完整请求与浮窗都限制 10s，读取也继承 OkHttp 的 10s 默认值。
- OkHttp 完整请求超时可抛 `InterruptedIOException`；旧映射只识别它的子类 `SocketTimeoutException`，导致被归为 `NET-CONNECT`。
- repository 的 `runCatching` 吞掉协程取消；网络／解析异常在取消期间到达时，还可能被记录为另一类失败。

通过本机 11s 延迟响应复现了旧期限与错误分类。不能仅凭旧日志解释手机上所有 42–59s 延迟或较短连接失败；真实网络、设备调度与服务端状态仍需新版诊断确认。

用户明确选择「一起修正，优先快速回复」。

## 本次变更

- 官方精确域名 `api.deepseek.com` 的连接测试和正式生成均使用 `thinking.type=disabled`；其他兼容服务不发送这一扩展。
- 正式生成 HTTP 连接等待 10s、读取和完整请求上限 60s；连接测试仍为 10s。两个 HTTP 客户端由同一个传入客户端派生，共享其连接池与调度资源。
- 浮窗总时限为 65s，给 HTTP 60s 之外的本机处理留小幅余量。操作取消仍可提前终止等待。
- `InterruptedIOException` 正确映射为 `NET-TIMEOUT`。失败日志和复制诊断显示经校验的异常类名，不记录异常消息、密钥、回答或思考正文。
- 在所有网络／解码失败分类前检查协程取消，repository 也重新抛出取消，避免取消被误报为连接／格式错误。
- 新配置的实际模型预填与提示都改为 `deepseek-flash`；已有配置原样保留，密钥仍由用户填写且测试成功后才能保存。没有因默认值自动保存或调用 API。
- versionName `0.1.2`，versionCode `3`。

## 验证

- TDD：初轮 44 项相关测试中 7 项按预期失败；另验证了取消后的 IOException、EOF／JSON／状态异常分类，以及复制诊断显示异常类型的失败用例，修复后通过。
- 完整 Android JVM 测试：198 项、0 失败、0 错误、0 跳过。
- 干净构建与测试：`:android-app:clean :android-app:testDebugUnitTest :android-app:assembleDebug` 成功，46 项任务执行。
- 本机 MockWebServer 11s 延迟的生成请求成功，仍保留三条候选；同样延迟的连接测试按短期限报 `NET-TIMEOUT`。不访问真实服务、无额外付费重试。
- 覆盖了其他服务不接收 DeepSeek 参数、20s 生成不被浮窗提前打断、repository 取消传播、五种取消期间异常不生成误导失败日志、默认模型不保存／调用、已存自定义模型保留。
- 后端：18 项通过（命令设置本机 `PYTHONPATH=D:\agent\codex\nextsay\backend\src`）。
- 独立只读检查提出一个取消分类遗漏，已用失败测试复现并修复；复查无剩余 Critical／Important／Minor 问题。
- APK v2 签名通过，签名证书与 0.1.1 相同；清单核验 `app.nextsay`、版本 `0.1.2`、versionCode `3`、minSdk `30`。
- `git diff --check` 通过。既有弃用与 ML Kit 库打包提示未改变。

## 交付

- APK：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\NextSay-0.1.2-debug.apk`
- 构建原件：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\android-app-debug.apk`
- 大小：56,498,555 字节。
- SHA-256：`06C7EB8024ECD1B759BBDF4ABF1F9FBE4C431CFB14C4E9C13D598490C05EA446`。
- 签名证书 SHA-256：`bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`。
- 源码在 `codex/byok-direct-api`，基于 `a2e2183` 的本次变更；未合并、推送或发布。

优先覆盖安装，不要先卸载。已有官方地址和 `deepseek-flash` 配置无需改动，先复测实际生成；如仍失败，复制 0.1.2 的诊断或导出日志，关注错误代码、耗时与异常类型。不得为排查索取用户 API Key。

自动验证不能替代用户手机和真实 DeepSeek 的验收；延长等待与关闭思考不保证每次请求成功。本次不修改聊天采集／OCR／回填流程，也不覆盖自定义服务配置。
