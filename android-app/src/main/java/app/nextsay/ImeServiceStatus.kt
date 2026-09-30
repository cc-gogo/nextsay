package app.nextsay

class ImeServiceStatus {
    fun isEnabled(enabledIds: Set<String>, targetId: String): Boolean {
        val normalizedTarget = normalize(targetId)
        return enabledIds.any { normalize(it) == normalizedTarget }
    }

    private fun normalize(componentId: String): String {
        val separator = componentId.indexOf('/')
        if (separator <= 0 || separator == componentId.lastIndex) return componentId
        val packageName = componentId.substring(0, separator)
        val className = componentId.substring(separator + 1)
        val expandedClassName = if (className.startsWith('.')) packageName + className else className
        return "$packageName/$expandedClassName"
    }
}
