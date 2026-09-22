package app.nextsay.overlay

object CopyFeedbackPosition {
    fun calculate(
        screenWidth: Int,
        screenHeight: Int,
        triggerX: Int,
        triggerY: Int,
        triggerSize: Int,
        feedbackWidth: Int,
        feedbackHeight: Int,
        gap: Int,
    ): PixelPosition {
        val preferredX = if (triggerX + triggerSize / 2 > screenWidth / 2) {
            triggerX - feedbackWidth - gap
        } else {
            triggerX + triggerSize + gap
        }
        val centeredY = triggerY + (triggerSize - feedbackHeight) / 2
        return PixelPosition(
            x = preferredX.coerceIn(0, (screenWidth - feedbackWidth).coerceAtLeast(0)),
            y = centeredY.coerceIn(0, (screenHeight - feedbackHeight).coerceAtLeast(0)),
        )
    }
}
