package app.nextsay.provider

enum class ProviderErrorCode(val wireCode: String, val userMessage: String) {
    CONFIG_MISSING("CFG-MISSING", "请先配置模型服务"),
    CONFIG_INVALID("CFG-INVALID", "模型服务配置无效"),
    NET_DNS("NET-DNS", "无法解析 API 地址"),
    NET_CONNECT("NET-CONNECT", "无法连接模型服务"),
    NET_TLS("NET-TLS", "模型服务安全连接失败"),
    NET_TIMEOUT("NET-TIMEOUT", "模型服务请求超时"),
    API_AUTH("API-AUTH", "API Key 无效或无权限"),
    API_QUOTA("API-QUOTA", "API 额度不足或请求过于频繁"),
    API_MODEL("API-MODEL", "模型不存在或不可用"),
    API_HTTP("API-HTTP", "模型服务返回错误"),
    API_OUTPUT_LIMIT("API-OUTPUT-LIMIT", "模型输出达到上限，未完整返回回答"),
    API_INCOMPATIBLE("API-INCOMPATIBLE", "模型服务返回格式不兼容"),
    CAPTURE_FAILED("APP-CAPTURE", "读取当前对话失败"),
    INSERTION_FAILED("APP-INSERT", "写入输入框失败"),
    APP_INTERNAL("APP-INTERNAL", "NextSay 内部错误"),
}

class ProviderException(
    val code: ProviderErrorCode,
    val diagnosticId: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException("${code.userMessage}（${code.wireCode}）", cause)
