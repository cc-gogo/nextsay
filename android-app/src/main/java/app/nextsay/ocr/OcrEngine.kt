package app.nextsay.ocr

import android.graphics.Bitmap

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): Result<List<OcrTextBlock>>
    fun warmUp()
    fun close()
}
