package app.nextsay.ocr

import app.nextsay.context.ScreenRect

data class OcrTextBlock(
    val text: String,
    val bounds: ScreenRect,
    val confidence: Float,
)
