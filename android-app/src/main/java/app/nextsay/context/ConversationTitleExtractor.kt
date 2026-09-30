package app.nextsay.context

class ConversationTitleExtractor {
    fun extract(packageName: String, nodes: List<NodeSnapshot>): String? {
        if (packageName !in QQ_PACKAGES) return null
        return nodes.firstNotNullOfOrNull { node ->
            node.text
                ?.trim()
                ?.takeIf { it.isNotEmpty() && node.viewId?.substringAfter(":id/") in QQ_TITLE_VIEW_IDS }
        }
    }

    private companion object {
        val QQ_PACKAGES = setOf("com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite")
        val QQ_TITLE_VIEW_IDS = setOf("304", "32z")
    }
}
