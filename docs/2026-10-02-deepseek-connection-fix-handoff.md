# NextSay 0.1.1：DeepSeek 连接测试修复交付

日期：2026-10-02（Asia/Shanghai）

## 问题与证据边界

手机诊断记录两次 `CONNECTION_TEST_FAILED`：`api.deepseek.com`、`deepseek-flash`、HTTP 200、`API-INCOMPATIBLE`。旧连接测试要求「仅回复 OK」，但限制 `max_tokens=4`，没有控制思考模式；空／缺失回答统一归为格式不兼容。

DeepSeek 当前官方文档确认 `deepseek-flash` 有效，且默认开启思考模式。因此「思考耗尽输出额度，尚未输出最终回答」是高度可疑原因。旧诊断没有响应正文或结束原因，不能据此宣称已经确认手机收到的具体响应。

- 官方入门：https://api-docs.deepseek.com/quick_start
- 思考模式：https://api-docs.deepseek.com/guides/thinking_mode
- Chat Completions：https://api-docs.deepseek.com/api/create-chat-completion

## 修复范围

- 连接测试输出额度上限从 4 改为 1024 token；提示仍只要求 OK，没有自动付费重试。额度是上限，不是每次固定消耗。
- 仅对经过配置校验的精确官方域名 `api.deepseek.com`，连接测试发送 `thinking.type=disabled`。其他域名不发送该扩展，正常候选生成的思考模式保持不变。
- `finish_reason=length` 单独归为 `API-OUTPUT-LIMIT`，提示输出达到上限；覆盖思考后空答案与截断候选 JSON，不把思考正文当成答案。
- 解析后的响应失败记录固定白名单结束原因、回答状态（`missing/non_string/blank/present`）、是否包含思考输出。正文、思考文字、API Key 均不进入日志。日志写入、旧记录读取、展示／导出仍做白名单校验。
- 新增 nullable 诊断字段，旧日志可继续读取。
- 应用版本升级为 `0.1.1`，versionCode 为 `2`。聊天读取、OCR、回填、保存前必须通过连接测试的规则不变。

## 自动验证

- TDD：旧代码下，修改／新增的回归测试出现 8 项预期断言或行为失败；修复后通过。测试 HTTP 层使用本机 MockWebServer，不访问真实用户账号。
- 完整 Android JVM 测试：189 项，0 失败、0 错误、0 跳过。
- `:android-app:clean :android-app:testDebugUnitTest :android-app:assembleDebug`：成功，46 项任务执行。补充测试预算上限断言后再次执行完整测试与构建：成功。
- 后端：18 项通过。首次运行因本机虚拟环境的旧源码路径产生 4 个收集错误（`nextsay_backend` 模块未找到）；为本次命令设置 `PYTHONPATH=D:\agent\codex\nextsay\backend\src` 后重跑通过，未改动后端。
- 独立只读代码检查：无 Critical／Important 问题；预算上限测试的 Minor 建议已处理。
- APK 签名验证：v2 通过，1 个 Android Debug 签名者。
- APK 清单核验：`app.nextsay`，versionCode `2`，versionName `0.1.1`，minSdk `30`。
- `git diff --check`：通过。

现有构建提示未改变：`SOFT_INPUT_ADJUST_RESIZE` 弃用提示，以及 ML Kit 原生库按原样打包。

## 安装包

- 交付：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\NextSay-0.1.1-debug.apk`
- 构建原件：`D:\agent\codex\nextsay\android-app\build\outputs\apk\debug\android-app-debug.apk`
- 大小：56,498,555 字节。
- SHA-256：`F108E5566AEA90E7458F4B66BE94827717FBE2FCBD3596D858A6DA0BE9309D31`。
- 签名证书 SHA-256：`bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`。
- 代码在 `codex/byok-direct-api`，基于 `1127591` 的本次修复；未合并、推送或发布。

两个 APK 的哈希一致。版本化文件名用于区分旧包，不改变 APK 内容。这仍是调试签名测试包。

## 手机验收

优先覆盖安装，不要先卸载，以免清空本机配置、历史和日志。如果手机提示签名冲突，先保留数据并反馈，不要为解决冲突直接卸载。

1. 确认新版诊断中的 App 版本为 `0.1.1`。
2. API 基础地址填写 `https://api.deepseek.com`，模型保持 `deepseek-flash`，自己的 API Key 只在手机填写。
3. 点击测试连接，成功后保存。
4. 再测试一次实际候选生成；本次没有改变正常生成的思考模式、10 秒请求超时等既有行为。
5. 如有错误，复制新版诊断或导出日志回传，检查新增结束原因、回答状态、是否包含思考输出字段。

尚未执行用户手机或真实 DeepSeek 账号的请求，不能宣称真实接口／设备已经验收通过。也未改变代理服务上的思考开关；这些服务只能通过通用预算改善与新诊断进一步排查。
