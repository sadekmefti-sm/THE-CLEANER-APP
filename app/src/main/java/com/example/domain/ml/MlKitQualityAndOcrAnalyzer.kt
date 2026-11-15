package com.example.domain.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class FaceAnalysisResult(
    val faceCount: Int,
    val faceQualityScore: Float, // 0..100 (neutral 65f when no faces present so landscapes aren't penalized)
    val leftEyeOpenProb: Float,
    val rightEyeOpenProb: Float,
    val smilingProb: Float
)

data class OcrGuardrailResult(
    val isDocumentOrReceipt: Boolean,
    val textBlockCount: Int,
    val characterCount: Int,
    val detectedSummary: String?
)

/**
 * 100% On-Device ML Kit Face Quality & OCR Document Protection Guardrail Analyzer.
 * Operates strictly on memory-safe 512x512 subsampled bitmaps.
 */
class MlKitQualityAndOcrAnalyzer {

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // open eyes + smile
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.10f)
            .build()
        FaceDetection.getClient(options)
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    companion object {
        private val DOCUMENT_GUARDRAIL_KEYWORDS = listOf(
            "receipt", "invoice", "subtotal", "total", "tax", "vat", "payment",
            "visa", "mastercard", "transaction", "account", "iban", "routing",
            "passport", "driver", "license", "identification", "national id",
            "serial", "warranty", "prescription", "medical", "boarding pass",
            "itinerary", "contract", "agreement", "certificate", "statement",
            "balance", "amount due", "order #", "ref #"
        )
    }

    /**
     * Evaluates facial expression quality for portrait and group clusters:
     * - Prioritizes both eyes open (leftEyeOpenProbability & rightEyeOpenProbability > 0.75)
     * - Rewards natural smiles (smilingProbability)
     * - Penalizes extreme head turns (headEulerAngleY / headEulerAngleZ)
     * - Rewards prominent face bounding box area
     */
    suspend fun analyzeFaces(bitmap512: Bitmap): FaceAnalysisResult {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap512, 0)
            val faces = faceDetector.process(image).await()
            if (faces.isEmpty()) {
                return FaceAnalysisResult(
                    faceCount = 0,
                    faceQualityScore = 65f,
                    leftEyeOpenProb = 0f,
                    rightEyeOpenProb = 0f,
                    smilingProb = 0f
                )
            }

            val totalImageArea = max(1f, (bitmap512.width * bitmap512.height).toFloat())
            var weightedScoreSum = 0f
            var weightSum = 0f
            var bestLeftEye = 0f
            var bestRightEye = 0f
            var bestSmile = 0f

            for (face in faces) {
                val box = face.boundingBox
                val faceArea = max(1f, (box.width() * box.height()).toFloat())
                val areaRatio = (faceArea / totalImageArea).coerceIn(0.01f, 0.60f)

                val leftEye = (face.leftEyeOpenProbability ?: 0.78f).coerceIn(0f, 1f)
                val rightEye = (face.rightEyeOpenProbability ?: 0.78f).coerceIn(0f, 1f)
                val smile = (face.smilingProbability ?: 0.50f).coerceIn(0f, 1f)

                if (leftEye + rightEye > bestLeftEye + bestRightEye) {
                    bestLeftEye = leftEye
                    bestRightEye = rightEye
                    bestSmile = smile
                }

                // Heavy bonus when both eyes are clearly open (> 0.75), steep penalty for blinks (< 0.35)
                val eyeScore = when {
                    leftEye > 0.75f && rightEye > 0.75f -> 45f + ((leftEye + rightEye - 1.5f) * 20f)
                    leftEye < 0.35f || rightEye < 0.35f -> 8f
                    else -> ((leftEye + rightEye) / 2f) * 40f
                }

                // Natural smile bonus (up to 25 pts)
                val smileScore = 10f + (smile * 15f)

                // Forward head orientation bonus (up to 15 pts)
                val yawPenalty = (abs(face.headEulerAngleY) / 45f).coerceIn(0f, 1f) * 10f
                val tiltPenalty = (abs(face.headEulerAngleZ) / 45f).coerceIn(0f, 1f) * 5f
                val poseScore = max(0f, 15f - yawPenalty - tiltPenalty)

                // Bounding box prominence bonus (up to 15 pts)
                val sizeScore = min(15f, (areaRatio / 0.15f) * 15f)

                val individualScore = (eyeScore + smileScore + poseScore + sizeScore).coerceIn(5f, 100f)
                val faceWeight = max(0.05f, areaRatio)
                weightedScoreSum += individualScore * faceWeight
                weightSum += faceWeight
            }

            val finalFaceScore = if (weightSum > 0f) {
                (weightedScoreSum / weightSum).coerceIn(5f, 100f)
            } else {
                65f
            }

            FaceAnalysisResult(
                faceCount = faces.size,
                faceQualityScore = finalFaceScore,
                leftEyeOpenProb = bestLeftEye,
                rightEyeOpenProb = bestRightEye,
                smilingProb = bestSmile
            )
        }.getOrDefault(
            FaceAnalysisResult(
                faceCount = 0,
                faceQualityScore = 65f,
                leftEyeOpenProb = 0f,
                rightEyeOpenProb = 0f,
                smilingProb = 0f
            )
        )
    }

    /**
     * On-Device ML Kit Text Recognition (OCR) Guardrail:
     * Protects receipts, invoices, IDs, passports, serial numbers, and dense whiteboard/page notes
     * from ever being queued into "Blurry/Bad Quality" auto-deletion lists.
     */
    suspend fun evaluateOcrGuardrail(
        bitmap512: Bitmap,
        displayName: String
    ): OcrGuardrailResult {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap512, 0)
            val visionText = textRecognizer.process(image).await()

            val blocks = visionText.textBlocks
            val fullText = visionText.text.trim()
            val charCount = fullText.count { !it.isWhitespace() }
            val lowerText = fullText.lowercase()
            val lowerName = displayName.lowercase()

            val matchedKeywords = DOCUMENT_GUARDRAIL_KEYWORDS.filter { keyword ->
                lowerText.contains(keyword) || lowerName.contains(keyword)
            }

            // Currency or financial numeric patterns (e.g. $24.99, €19.00, £45.50, 2026-11-15)
            val hasCurrencyOrInvoicePattern = Regex("""[$€£¥]\s*\d+[.,]\d{2}""").containsMatchIn(fullText) ||
                Regex("""\b\d{2}[/-]\d{2}[/-]\d{2,4}\b""").containsMatchIn(fullText)

            val isDocOrReceipt = blocks.size >= 8 ||
                charCount >= 110 ||
                matchedKeywords.isNotEmpty() ||
                (blocks.size >= 4 && hasCurrencyOrInvoicePattern)

            val snippet = if (fullText.isNotEmpty()) {
                val cleanSingleLine = fullText.replace(Regex("\\s+"), " ").take(90)
                if (matchedKeywords.isNotEmpty()) {
                    "[${matchedKeywords.take(2).joinToString(", ").uppercase()}] $cleanSingleLine"
                } else {
                    cleanSingleLine
                }
            } else if (matchedKeywords.isNotEmpty()) {
                "Protected: ${matchedKeywords.first().uppercase()}"
            } else {
                null
            }

            OcrGuardrailResult(
                isDocumentOrReceipt = isDocOrReceipt,
                textBlockCount = blocks.size,
                characterCount = charCount,
                detectedSummary = snippet
            )
        }.getOrDefault(
            OcrGuardrailResult(
                isDocumentOrReceipt = false,
                textBlockCount = 0,
                characterCount = 0,
                detectedSummary = null
            )
        )
    }
}
