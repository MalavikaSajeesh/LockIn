package com.lockin.app.verification

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Free, fully on-device image verification using ML Kit's built-in Image
 * Labeling model. No API key, no network call, no per-use cost.
 *
 * Be realistic about what this is: the base model knows roughly 400 generic
 * categories, so it can tell "food" from "furniture" but cannot confirm you
 * personally did a specific thing, and it cannot tell a live scene from a
 * photo of a photo. Treat it as a nudge toward honesty, not as proof.
 */
object MLKitVerifier {

    private const val CONFIDENCE_THRESHOLD = 0.6f

    data class VerificationResult(
        val passed: Boolean,
        val detectedLabels: List<String>,
        val matchedLabel: String?
    )

    suspend fun verify(bitmap: Bitmap, expectedLabels: List<String>): VerificationResult {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
            .build()
        val labeler = ImageLabeling.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        val labels = suspendCancellableCoroutine<List<String>> { cont ->
            labeler.process(image)
                .addOnSuccessListener { result -> cont.resume(result.map { it.text.lowercase() }) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

        labeler.close()

        if (expectedLabels.isEmpty()) {
            // No specific expectation set: any recognised content counts as
            // "a photo was actually taken".
            return VerificationResult(labels.isNotEmpty(), labels, null)
        }

        val expectedLower = expectedLabels.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val match = labels.firstOrNull { detected ->
            expectedLower.any { expected ->
                detected.contains(expected) || expected.contains(detected)
            }
        }

        return VerificationResult(
            passed = match != null,
            detectedLabels = labels,
            matchedLabel = match
        )
    }
}
