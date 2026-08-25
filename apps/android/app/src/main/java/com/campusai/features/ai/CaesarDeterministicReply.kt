package com.campusai.features.ai

/** Stable product facts should not depend on sampling from a local model. */
internal object CaesarDeterministicReply {
    private val identityPatterns = listOf(
        Regex("^(?:你的)?名字是什么[?？]?$"),
        Regex("^你叫什么(?:名字)?[?？]?$"),
        Regex("^你是谁[?？]?$"),
        Regex("^(?:请)?(?:简单)?(?:介绍一下你自己|自我介绍)[。.!！?？]?$"),
    )
    private val capabilityPatterns = listOf(
        Regex("^(?:请)?(?:说说|介绍一下)?你能(?:做|干)什么[?？]?$"),
        Regex("^你会什么[?？]?$"),
        Regex("^你有什么功能[?？]?$"),
        Regex("^你能帮我(?:做)?什么[?？]?$"),
    )

    fun forPrompt(prompt: String, hasImages: Boolean): String? {
        if (hasImages) return null
        val normalized = prompt.trim().replace(Regex("\\s+"), "")
        return when {
            identityPatterns.any { it.matches(normalized) } ->
                "我是 Caesar∞，运行在你设备上的私人 Agent。"
            capabilityPatterns.any { it.matches(normalized) } ->
                "我可以处理文字和图片、接收语音转写，并通过已注册的 App 工具帮你管理时间记录、树洞、心愿墙、消息和健康数据。权限与不可逆操作仍由系统确认。"
            else -> null
        }
    }
}
