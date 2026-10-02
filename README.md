# NextSay（下一句）

NextSay 是一个 Android 11+ 聊天回复助手。用户在微信或 QQ 中主动点击浮动按钮，NextSay 获取当前可见上下文，并直接调用用户自己配置的 OpenAI 兼容模型服务生成三条回复候选。点击候选只会写入当前输入框，消息始终由用户亲自发送。

安装完成后，NextSay 不需要电脑、ADB 连接或 NextSay 服务器。手机通过自己的 Wi-Fi 或移动数据直接访问用户填写的 API 地址。

## 当前能力

- 支持微信 `com.tencent.mm`、QQ、TIM 和 QQ 轻聊版包名白名单。
- QQ/TIM 优先通过 Android 无障碍节点读取当前可见纯文字；节点为空时回退本地 OCR。
- 微信聊天页变化时可能自动截取单帧屏幕，并通过随 APK 打包的中文 OCR 模型在本机识别；点击浮球也会触发更新。
- 原始截图只存在内存中，识别完成后立即释放，不保存、不上传。
- 按聊天标题隔离并加密保存本地文字历史，再通过连续消息重叠追加新内容。
- 在本机过滤时间、聊天页控件、重复文本和当前草稿。
- 根据消息水平位置推断 `me`、`other` 或低置信度 `unknown`。
- 上传前遮盖大陆手机号、邮箱和 8 位以上连续数字。
- 点击浮球即授权本次识别文字自动上传并生成回复；面板会显示本次使用的具体上下文。
- 每位用户在手机上填写自己的一套 API URL、API Key 和模型名称；API Key 使用 Android Keystore 加密保存。
- 支持 OpenAI 兼容的 `POST /chat/completions` 接口，并可先发送极小请求测试连接。
- 本机诊断日志可复制或导出，无需 ADB，且不记录 API Key、对话正文、提示词、草稿、候选回复或网络正文。
- 仓库中的 FastAPI 后端仅保留为可选的旧版/代理基础设施，不是 Android 默认路径。
- 候选写入使用 `ACTION_SET_TEXT`，没有寻找发送按钮或自动发送代码。

当前的消息位置规则是干净实现的通用启发式。微信和 QQ 会随版本改变无障碍节点结构，因此商用前必须用脱敏真机节点样本补充适配和回归测试。

## 项目结构

```text
nextsay/
|-- android-app/      Android 原生 Kotlin 应用、无障碍浮窗和 JVM 测试
|-- backend/          FastAPI 服务、模型 Provider 和 pytest 测试
|-- docs/             MVP 设计规格与实施计划
|-- gradlew.bat       Windows Gradle Wrapper
`-- README.md
```

## 首次使用

1. 安装并打开 NextSay。
2. 打开“配置模型服务”。
3. 填写 OpenAI 兼容 API 地址、自己的 API Key 和模型名称。
4. 点击“测试连接”；成功后点击“保存”。
5. 开启无障碍服务或 NextSay 输入法后使用。
6. 报错时打开“诊断日志”，复制或导出文件用于排查。

例如，API 地址可以填写服务商给出的基础地址（如 `https://api.example.com/v1`），也可以填写完整的 `/chat/completions` 地址。正式版只接受 HTTPS；调试版允许为本地开发填写 HTTP。

DeepSeek 官方接口可填写 `https://api.deepseek.com`，新配置的模型默认填写 `deepseek-flash`，不覆盖已保存的自定义模型。连接测试只要求回复 OK，最多允许 1024 个输出 token（不是每次都消耗 1024）；DeepSeek 官方域名的测试和正式生成均设置 `thinking.type=disabled`，优先快速回复，不向其他服务发送这一专用参数。输出被截断时显示 `API-OUTPUT-LIMIT`，不再误报成格式不兼容。

连接测试的 HTTP 等待上限为 10 秒；正式生成的 HTTP 连接等待为 10 秒、读取／完整请求上限为 60 秒，浮窗另有 65 秒总时限用于容纳本机处理。超时日志包含安全异常类型，不保存异常消息；取消操作不会再被包装成普通连接／解析失败。网络质量、设备调度或服务端排队仍可能影响实际耗时，不能保证每次请求成功。

## 可选后端

Android 应用不依赖仓库内的 Python 服务。只有在开发旧版协议或自行搭建代理时，才需要启动它：

```powershell
$env:NEXTSAY_PROVIDER = "mock"
$env:NEXTSAY_DEV_TOKEN = "local-dev-token"
.\.venv\Scripts\python.exe -m uvicorn nextsay_backend.app:app --app-dir backend/src --host 0.0.0.0 --port 8000
```

健康检查地址是 `http://127.0.0.1:8000/health`。Mock 模式不需要任何模型密钥。

使用真实模型时，在后端环境变量中设置：

```text
NEXTSAY_PROVIDER=openai
NEXTSAY_BASE_URL=https://模型服务商地址/v1
NEXTSAY_MODEL=模型名称
NEXTSAY_API_KEY=服务端密钥
NEXTSAY_DEV_TOKEN=随机长令牌
```

这个后端不是当前 Android BYOK 流程的默认路径。使用 Android App 时，用户自己的密钥只在手机端加密保存，并由 App 直接发送给用户配置的模型服务。

## 构建 Android APK

项目要求 JDK 17、Android SDK 35 和 Build Tools 35。当前仓库的忽略目录中已经准备了便携开发工具；也可以直接用 Android Studio 打开仓库根目录。

```powershell
$env:JAVA_HOME = "D:\agent\codex\nextsay\.tools\jdk17\extracted\jdk-17.0.16+8"
$env:ANDROID_HOME = "D:\agent\codex\nextsay\.android-sdk"
.\gradlew.bat :android-app:assembleDebug :android-app:testDebugUnitTest
```

生成的调试 APK：

```text
android-app/build/outputs/apk/debug/android-app-debug.apk
```

## 使用步骤

完成首次配置后，打开微信或 QQ 的一对一纯文字聊天。可以点击屏幕右侧的绿色编辑图标，也可以切换到 NextSay 输入法。NextSay 会识别并展示本次使用的上下文，生成三条候选；选择后只写入输入框，由用户检查并手动发送。

NextSay 会优先使用当前已聚焦的安全输入框；没有焦点时，会选择位置最低的非密码编辑框并请求焦点。写入失败时才显示“复制候选”作为显式备用操作。

## 隐私与安全边界

- 无障碍服务在支持的聊天页变化时会自动在本机读取可见节点，必要时截屏做本机 OCR，并更新本地上下文与加密历史；这一过程不调用模型 API。
- 只有用户主动生成时，识别后的文字才会直传到用户配置的第三方模型服务，并受该服务隐私政策约束。
- 微信原始截图不落盘、不进入日志，也不发送给模型服务；只有本机 OCR 得到并经脱敏的文字会在主动生成时发送。
- 本地历史正文使用 Android Keystore 管理的密钥加密；同名联系人无法可靠自动区分，群聊暂不累计长期历史。
- 不支持的包、密码字段、缺少可用文字或窗口变化都会拒绝操作。
- API Key 由 Android Keystore 保护；配置页默认遮盖显示，连接测试成功后才能保存。
- 诊断日志只包含固定的错误代码、事件 ID、API 域名、模型、HTTP 状态、耗时、安全异常类名和设备版本等安全字段。解析后的响应失败还会记录白名单结束原因、回答状态（缺失／非字符串／空／有内容）及是否包含思考输出，不保存回答、思考正文或原始异常消息。
- 不含自动点击发送、通知读取、Root、账号、计费或分析功能。无障碍服务开启期间会监听支持应用的页面变化，以更新本地上下文。

## 验证

```powershell
.\.venv\Scripts\python.exe -m pytest backend\tests -v
.\gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug
```

真机发布前至少验证 Android 11-13 和 Android 14+，覆盖微信与 QQ 的空草稿、已有草稿、切换聊天、切换应用、断网、模型服务超时、鉴权失败和无障碍关闭场景。
