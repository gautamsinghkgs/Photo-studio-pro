package com.photostudio.pro.core.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.photostudio.pro.analytics.Events
import com.photostudio.pro.core.image.ExportOps

/**
 * ⭐ The single export path for EVERY tool.
 *
 * Before this existed, each of the eight tools implemented its own "check free
 * limit → watermark → encode → save → count the export → ask AdPolicy" sequence.
 * That duplication is exactly how a paying user ends up seeing an ad, or a free
 * user ends up bypassing the daily cap: one tool forgets a step and there is no
 * test that can catch it.
 *
 * Everything commercially sensitive now lives here and nowhere else:
 *   1. the free-export daily cap,
 *   2. the free-tier resolution cap and watermark,
 *   3. SAF-or-share destination handling,
 *   4. counter persistence,
 *   5. the AdPolicy decision via AdCoordinator, on a natural break point.
 *
 * A tool that needs to export calls [run] and supplies only its bitmap and a
 * tool id. Nothing else.
 */
object ExportFlow {

    /** Free tier is capped at this many px on the long edge. Pro exports full res. */
    const val FREE_MAX_EDGE = 1080

    sealed interface Outcome {
        data class Saved(val uri: Uri?, val bytes: Int, val width: Int, val height: Int) : Outcome
        /** The user has used their free exports for today. Caller shows the paywall. */
        data object NeedsPro : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /**
     * @param toolId    analytics + kill-switch id, e.g. "bgremove", "enhance".
     * @param bytes     optional pre-encoded size when the caller already encoded
     *                  (ID photo does, because it has to hit a KB ceiling).
     *                  When null we encode here at [quality].
     * @param treeUri   SAF folder. When null we fall back to a FileProvider share
     *                  Uri, which is correct for "share this image" but NOT for
     *                  "save my 40 resized photos" — Batch must supply a tree.
     * @param fileName  without extension.
     * @param afterSave called on the UI thread once the file is written, whether
     *                  or not an ad is going to show. This is the natural break
     *                  point at which AdPolicy is consulted.
     */
    suspend fun run(
        ctx: Context,
        activity: Activity?,
        source: Bitmap,
        toolId: String,
        fileName: String,
        format: ExportOps.Format = ExportOps.Format.JPEG,
        quality: Int = 92,
        treeUri: Uri? = null,
        preEncoded: ByteArray? = null,
        afterSave: () -> Unit = {},
    ): Outcome {
        val sl = ServiceLocator
        val isPro = sl.entitlements.snapshot().isPro

        // ---- 1. free daily cap ----
        if (!isPro && sl.usage.exportsToday >= sl.flags.freeExportsPerDay) {
            Events.logPaywallShown("${toolId}_export_limit")
            return Outcome.NeedsPro
        }

        return try {
            // ---- 2. free-tier downscale + watermark ----
            val sized = if (isPro) source
                        else BitmapOpsSafe.scaleToLongestEdge(source, FREE_MAX_EDGE)
            val stamped = if (isPro) sized
                          else ExportOps.watermark(sized, "Photo Studio Pro")

            val bytes = preEncoded ?: ExportOps.encode(stamped, format, quality)

            // ---- 3. destination ----
            val full = if (fileName.contains('.')) fileName else "$fileName.${format.ext}"
            val uri: Uri? = if (treeUri != null) {
                ExportOps.saveToTree(ctx, treeUri, full, bytes, format.mime)
            } else {
                ExportOps.toShareUri(ctx, stamped, fileName, format, quality)
            }

            // ---- 4. counters + analytics ----
            sl.prefs.noteExport()
            sl.usage.refresh()
            Events.logExportCompleted(toolId, source.width, source.height, bytes.size, isPro)

            // recycle intermediates, never the caller's source bitmap
            if (stamped !== sized) stamped.recycle()
            if (sized !== source) sized.recycle()

            val out = if (uri != null) Outcome.Saved(uri, bytes.size, source.width, source.height)
                      else Outcome.Failed("save_failed")

            // ---- 5. the ad decision, on a natural break point ----
            if (activity != null) {
                sl.adCoordinator.afterExportCompleted(activity) { afterSave() }
            } else {
                afterSave()
            }
            out
        } catch (t: Throwable) {
            Events.logMlKitError("export_$toolId", t.javaClass.simpleName)
            afterSave()
            Outcome.Failed(t.javaClass.simpleName)
        }
    }

    /** Small indirection so this file has no hard dependency on a Bitmap import cycle. */
    private object BitmapOpsSafe {
        fun scaleToLongestEdge(src: Bitmap, edge: Int): Bitmap =
            com.photostudio.pro.core.image.BitmapOps.scaleToLongestEdge(src, edge, recycleSource = false)
    }
}
