package com.photostudio.pro.feature.bgremover

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.photostudio.pro.analytics.Events
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ⭐ M1 — BACKGROUND REMOVER. The main hook of the whole app.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ⚠️⚠️ CRITICAL FACT, VERIFIED 2026-10-29 AGAINST GOOGLE'S OFFICIAL DOC
 *     https://developers.google.com/ml-kit/vision/subject-segmentation/android
 *     (that page was last updated 2026-10-28)
 *
 *   "This API requires Android API level 24 or higher and uses an UNBUNDLED
 *    library downloaded via Google Play services."
 *
 *   Sdk name          : play-services-mlkit-subject-segmentation
 *   App size impact   : ~200 KB        (NOT the ~8 MB a bundled model would add)
 *   Initialization    : "Users might have to wait for the model to download
 *                        before first use."
 *
 *   AND THE LINE THAT BITES IF YOU IGNORE IT:
 *   "If you don't enable install-time model downloads or request explicit
 *    download the model is downloaded the first time you run the segmenter.
 *    REQUESTS YOU MAKE BEFORE THE DOWNLOAD HAS COMPLETED PRODUCE NO RESULTS."
 *
 *   → A silent empty result, not a crash. The user sees a blank image and
 *     concludes the app is broken. This is why [ensureModelReady] exists and
 *     why the UI has an honest "setting up" state instead of pretending.
 *
 *   There is NO com.google.mlkit:segmentation-subject artifact. Verified by
 *   querying Maven directly. Do not "fix" this by switching to a bundled
 *   artifact that does not exist.
 *
 *   Selfie Segmentation IS bundled (com.google.mlkit:segmentation-selfie) but
 *   segments ONLY PEOPLE. It cannot cut out a handbag, a shoe or a bottle, so
 *   it is useless for a product-photo app. Do not substitute it.
 * ══════════════════════════════════════════════════════════════════════════
 *
 * The AndroidManifest declares:
 *   <meta-data android:name="com.google.mlkit.vision.DEPENDENCIES"
 *              android:value="subject_segment" />
 * so Play Services fetches the model in the background right after install.
 * By the time the user picks a photo it is usually already there.
 *
 * Also from the official doc, two quality notes baked into this class:
 *   - "the image should be at least 512x512 pixels" for accurate results
 *     -> we downsample to [WORKING_EDGE] but never below 512.
 *   - "the first inference is often slower than subsequent inferences due to
 *     model initialization... consider calling a dummy inference ahead of time"
 *     -> [warmUp] does exactly that.
 *
 * ──────────────────────────────────────────────────────────────────────────
 * 🔎 VERIFY BEFORE FIRST BUILD (I cannot compile here — no Android SDK):
 *    the four `com.google.mlkit.vision.segmentation.subject.*` imports and the
 *    `ModuleInstall*` imports are the beta surface. Open the doc URL above,
 *    copy its import list, and diff it against this file. Everything else in
 *    here (the mask maths, the warm-up, the fallbacks) is plain Kotlin.
 * ──────────────────────────────────────────────────────────────────────────
 */
class SubjectSegmentationEngine(private val context: Context) {

    companion object {
        /**
         * Working resolution. ML Kit wants >= 512x512 for good results, and a
         * 12 MP source is wasted compute plus an OOM risk on mid-range devices.
         * 1280 is a good balance; exports upscale back to the source size.
         */
        const val WORKING_EDGE = 1280
        const val MIN_EDGE = 512
    }

    enum class ModelState { UNKNOWN, READY, DOWNLOADING, NEEDS_NETWORK, UNSUPPORTED }

    data class Result(
        /** Foreground-only bitmap WITH alpha. Null if nothing was found. */
        val foreground: Bitmap?,
        /** Raw confidence mask, at [maskWidth] x [maskHeight]. */
        val mask: FloatBuffer?,
        val maskWidth: Int,
        val maskHeight: Int,
        /** Fraction of pixels that are confidently foreground. */
        val coverage: Float,
    )

    private val options: SubjectSegmenterOptions = SubjectSegmenterOptions.Builder()
        // We want BOTH: the bitmap for the fast happy path, and the confidence
        // mask so MaskOps can feather the edge. A hard cut-out looks like a
        // sticker and is the #1 complaint about cheap cut-out apps.
        .enableForegroundBitmap()
        .enableForegroundConfidenceMask()
        .build()

    private val segmenter: SubjectSegmenter = SubjectSegmentation.getClient(options)

    // ---------------- Model availability ----------------

    /**
     * Ask Play Services whether the Subject Segmentation module is present, and
     * start the download if it is not. Returns the state so the UI can show an
     * honest message instead of failing silently.
     */
    suspend fun ensureModelReady(): ModelState = suspendCancellableCoroutine { cont ->
        runCatching {
            val install: com.google.android.gms.common.moduleinstall.ModuleInstallClient =
                ModuleInstall.getClient(context)

            // SubjectSegmenter implements OptionalModuleApi, which is what lets
            // ModuleInstallClient manage it.
            val listener = object : InstallStatusListener {
                override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                    when (update.installState) {
                        ModuleInstallStatusUpdate.InstallState.COMPLETED,
                        ModuleInstallStatusUpdate.InstallState.INSTALLED ->
                            if (cont.isActive) cont.resume(ModelState.READY)
                        ModuleInstallStatusUpdate.InstallState.REQUIRES_USER_CONFIRMATION,
                        ModuleInstallStatusUpdate.InstallState.PENDING,
                        ModuleInstallStatusUpdate.InstallState.DOWNLOADING,
                        ModuleInstallStatusUpdate.InstallState.DOWNLOADED,
                        ModuleInstallStatusUpdate.InstallState.INSTALLING ->
                            Events.logSegmentationModelNotReady("downloading")
                        ModuleInstallStatusUpdate.InstallState.FAILED,
                        ModuleInstallStatusUpdate.InstallState.UNKNOWN ->
                            if (cont.isActive) cont.resume(ModelState.NEEDS_NETWORK)
                    }
                }
            }
            install.registerListener(listener)

            install.installModules(listOf(segmenter))
                .addOnSuccessListener { response ->
                    val already = runCatching { response.areModulesAlreadyInstalled() }.getOrDefault(false)
                    if (already) {
                        install.unregisterListener(listener)
                        if (cont.isActive) cont.resume(ModelState.READY)
                    }
                    // If not already installed the listener above will report
                    // progress. We deliberately do NOT resume here.
                }
                .addOnFailureListener { e ->
                    install.unregisterListener(listener)
                    Events.logMlKitError("subject_segmentation_install", e.javaClass.simpleName)
                    if (cont.isActive) cont.resume(ModelState.NEEDS_NETWORK)
                }

            cont.invokeOnCancellation { runCatching { install.unregisterListener(listener) } }
        }.onFailure {
            // Device without Play Services, or an older Play Services version.
            Events.logMlKitError("subject_segmentation_moduleinstall", it.javaClass.simpleName)
            if (cont.isActive) cont.resume(ModelState.UNSUPPORTED)
        }
    }

    /**
     * Pre-run inference on a tiny synthetic bitmap so the first real photo is
     * not slowed by model initialisation. Google's own tip. Call from a
     * coroutine on Dispatchers.Default after the model reports READY.
     */
    suspend fun warmUp() = runCatching {
        val tiny = Bitmap.createBitmap(MIN_EDGE, MIN_EDGE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.LTGRAY)
        }
        try { processBitmap(tiny) } finally { tiny.recycle() }
    }

    // ---------------- Inference ----------------

    suspend fun segmentUri(uri: Uri): Result {
        val image = InputImage.fromFilePath(context, uri)
        return run(image)
    }

    suspend fun segmentBitmap(source: Bitmap): Result {
        // Downsample to the working edge, but never below MIN_EDGE: below 512px
        // Google documents that segmentation accuracy degrades.
        val longest = maxOf(source.width, source.height)
        val target = longest.coerceAtMost(WORKING_EDGE).coerceAtLeast(MIN_EDGE)
        val working = if (longest > target)
            com.photostudio.pro.core.image.BitmapOps.scaleToLongestEdge(source, target)
        else source
        try {
            return run(InputImage.fromBitmap(working, 0))
        } finally {
            if (working != source) working.recycle()
        }
    }

    private suspend fun processBitmap(bmp: Bitmap): Result = segmentBitmap(bmp)

    private suspend fun run(image: InputImage): Result = suspendCancellableCoroutine { cont ->
        segmenter.process(image)
            .addOnSuccessListener { result ->
                runCatching {
                    val fg = result.foregroundBitmap
                    val mask = result.foregroundConfidenceMask
                    val mw = image.width
                    val mh = image.height

                    var coverage = 0f
                    if (mask != null) {
                        mask.rewind()
                        val n = mw * mh
                        var fgCount = 0
                        for (i in 0 until n) if (mask.get() > 0.5f) fgCount++
                        coverage = if (n > 0) fgCount.toFloat() / n else 0f
                    }
                    cont.resume(Result(fg, mask, mw, mh, coverage))
                }.onFailure { cont.resumeWithException(it) }
            }
            .addOnFailureListener { e ->
                Events.logMlKitError("subject_segmentation", e.javaClass.simpleName)
                cont.resumeWithException(e)
            }
    }

    fun close() = runCatching { segmenter.close() }
}
