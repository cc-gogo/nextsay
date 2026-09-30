package app.nextsay

class AccessibilityServiceStatus {
    fun isEnabled(enabledComponents: Set<String>, targetComponent: String): Boolean =
        targetComponent in enabledComponents
}
