package com.photostudio.pro.feature.idphoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.photostudio.pro.analytics.Events
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * M2 — Passport / ID Photo. Face geometry via ML Kit Face Detection.
 *
 * ✅ This one IS BUNDLED: `com.google.mlkit:face-detection:16.1.7` (~6.9 MB).
 *    It works offline from the very first run, unlike Subject Segmentation.
 *    Verified against Maven on 2026-10-29 and against Google's Face Detection
 *    doc (updated 2026-02-18), which lists:
 *      Unbundled ~800 KB / Bundled ~6.9 MB / minSdk 21
 *
 * We use PERFORMANCE_MODE_FAST. Google's own guidance (and Banuba's comparison)
 * is that FAST is accurate enough for detection-only work, and ID-photo framing
 * needs the box and the eyes, not 3D contour meshes. FAST also keeps the model
 * path smaller and the latency near-instant, which matters because the first 60
 * seconds decide the Play Store rating.
 *
 * ML Kit has NO face recognition and NO liveness detection — that is fine, we
 * are only measuring geometry. Do not let an AI tool "helpfully" add KYC.
 */
class FaceEngine(context: Context) {

    data class FaceGeometry(
        /** Chin-to-crown box in source pixels. */
        val box: Rect,
        /** Best estimate of the eye line, in source pixels from the TOP. */
        val eyeLineY: Int,
        /** 0..1 per eye; null if classification was off or the eye is occluded. */
        val leftEyeOpen: Float?,
        val rightEyeOpen: Float?,
        /** Head height as a fraction of the crop height it will end up in. */
        val headHeightPx: Int,
        val smiling: Float?,
    )

    private val options: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        // Landmarks give us the eyes, which the eye-line check needs.
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        // Classification gives eyes-open / smiling — useful guidance, not required.
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        // Ignore tiny background faces; an ID photo subject fills the frame.
        .setMinFaceSize(0.20f)
        .build()

    private val detector: FaceDetector = FaceDetection.getClient(options)

    suspend fun detectUri(context: Context, uri: Uri): List<FaceGeometry> {
        val image = InputImage.fromFilePath(context, uri)
        return run(image)
    }

    suspend fun detectBitmap(bmp: Bitmap): List<FaceGeometry> =
        run(InputImage.fromBitmap(bmp, 0))

    private suspend fun run(image: InputImage): List<FaceGeometry> =
        suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces ->
                    runCatching { cont.resume(faces.map { it.toGeometry() }) }
                        .onFailure { cont.resumeWithException(it) }
                }
                .addOnFailureListener { e ->
                    Events.logMlKitError("face_detection", e.javaClass.simpleName)
                    cont.resumeWithException(e)
                }
        }

    private fun Face.toGeometry(): FaceGeometry {
        val b = boundingBox
        // Eye line: average of the two eye landmarks if present, else 40% down
        // the box (the anthropometric average for a frontal face).
        val le: PointF? = getLandmark(FaceLandmark.LEFT_EYE)?.position
        val re: PointF? = getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val eyeY = when {
            le != null && re != null -> ((le.y + re.y) / 2f).toInt()
            le != null -> le.y.toInt()
            re != null -> re.y.toInt()
            else -> b.top + (b.height() * 0.40f).toInt()
        }
        return FaceGeometry(
            box = Rect(b),
            eyeLineY = eyeY,
            // ML Kit 17.1.0 has NO Face.UNCOMPUTED_PROBABILITY constant (javap-verified
            // against play-services-mlkit-face-detection): the probability getters
            // return a nullable Float, and null already means "not computed" (bug #23).
            leftEyeOpen = leftEyeOpenProbability,
            rightEyeOpen = rightEyeOpenProbability,
            headHeightPx = b.height(),
            smiling = smilingProbability,
        )
    }

    fun close() = runCatching { detector.close() }
}
