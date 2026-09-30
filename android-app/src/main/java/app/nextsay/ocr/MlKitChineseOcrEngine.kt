package app.nextsay.ocr

import android.graphics.Bitmap
import app.nextsay.context.ScreenRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class MlKitChineseOcrEngine(
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    ),
) : OcrEngine {
    override suspend fun recognize(bitmap: Bitmap): Result<List<OcrTextBlock>> =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val blocks = text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                        val bounds = line.boundingBox ?: return@mapNotNull null
                        val confidences = line.elements.mapNotNull { it.confidence }
                        val confidence = confidences.takeIf { it.isNotEmpty() }
                            ?.average()
                            ?.toFloat()
                            ?: DEFAULT_CONFIDENCE
                        OcrTextBlock(
                            text = line.text,
                            bounds = ScreenRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                            confidence = confidence,
                        )
                    }
                    continuation.resume(Result.success(blocks))
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
                .addOnCanceledListener {
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(IllegalStateException("OCR cancelled")))
                    }
                }
        }

    override fun warmUp() = Unit

    override fun close() = recognizer.close()

    private companion object {
        const val DEFAULT_CONFIDENCE = 0.75f
    }
}
