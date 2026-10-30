package com.photostudio.pro.core.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.photostudio.pro.analytics.Events
import com.photostudio.pro.core.image.ExportOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ⭐ The single export path for EVERY tool.
 *
 * Before this existed, each of the eight tools implemented its own "check free
 * limit → watermark → encode → save → count the export → ask AdPolicy" sequence.
 * That duplication is exactly how a paying user ends up seeing an ad, or a free
 * user ends up bypassing the daily cap: one tool forgets a step and there is no
 * test that can catch it. It is not hypothetical — bug #11 was a tool (PDF) that
 * forgot the ad hook entirely, and bug #30 was a tool (Batch) that counted exports
 * but never checked the cap.
 *
 * Everything commercially sensitive now lives here and nowhere else:
 *   1. the free-export daily cap,
 *   2. the free-tier resolution cap and watermark,
 *   3. SAF-or-share destination handling,
 *   4. counter persistence,
 *   5. the AdPolicy decision via AdCoordinator, on a natural break point.
 *
 * A tool that needs to export calls [run] (one file) or [runBatch] (many files) and
 * supplies only its bitmap, a tool id and a [Plan]. Nothing else. In particular a
 * caller must NEVER call `AdCoordinator.afterExportCompleted` itself — both entry
 * points already do, so a Screen-level call means two ad decisions per export
 * (README §6 P1, "the double-ad trap").
 */
object ExportFlow {

    /** Free tier is capped at this many px on the long edge. Pro exports full res. */
    const val FREE_MAX_EDGE = 1080

    /** The watermark text stamped on every free-tier export. */
    private const val WATERMARK = "Photo Studio Pro"

    /**
     * How an export is prepared, and therefore what the FREE tier gives up.
     *
     * This is a closed set on purpose. A tool cannot quietly opt out of the
     * watermark or the daily cap — it can only pick one of two documented shapes,
     * and the reason for the second one lives here where it gets audited, instead
     * of in a feature folder where nobody looks.
     *
     * Pro ignores both variants' free-tier parts: no downscale, no watermark.
     */
    sealed interface Plan {
        /**
         * Downscale to [FREE_MAX_EDGE] on the long edge, watermark, encode at
         * [quality]. What BG Remover, Enhance, Collage and White Background use.
         *
         * [quality] applies to BOTH tiers, because the free tier's cost is the
         * downscale + watermark, not extra compression. A tool that wants a
         * different Pro quality passes it in, computed from the entitlement
         * snapshot — that is a preference, not a policy decision.
         */
        data class Standard(val quality: Int = 92) : Plan

        /**
         * Watermark, but NEVER downscale, and encode by searching for a file-size
         * ceiling instead of using a fixed quality.
         *
         * **ID Photo only.** Government portals validate pixel dimensions (35×45 mm
         * at 300 dpi = 413×531) *and* a maximum KB. Shrinking to SD would make the
         * photo fail the very check this tool exists to pass, and a 4×6 print sheet
         * (1200×1800) would come out at the wrong DPI. The KB ceiling already makes
         * these files tiny, so the free tier gives up nothing by skipping the
         * downscale — it still gets the watermark and the daily cap.
         */
        data class FixedDimensions(val maxBytes: Int, val tolerancePct: Int = 8) : Plan
    }

    sealed interface Outcome {
        data class Saved(val uri: Uri?, val bytes: Int, val width: Int, val height: Int) : Outcome
        /** The user has used their free exports for today. Caller shows the paywall. */
        data object NeedsPro : Outcome
        data class Failed(val reason: String) : Outcome
    }

    sealed interface BatchOutcome {
        data class Finished(val done: Int, val failed: Int, val totalBytes: Long) : BatchOutcome
        /** The user has used their free exports for today. Caller shows the paywall. */
        data object NeedsPro : BatchOutcome
        data class Failed(val reason: String) : BatchOutcome
    }

    /**
     * Export ONE file.
     *
     * @param toolId    analytics + kill-switch id, e.g. "bgremove", "enhance".
     * @param treeUri   SAF folder. When null we fall back to a FileProvider share
     *                  Uri, which is correct for "share this image" but NOT for
     *                  "save my 40 resized photos" — Batch must supply a tree.
     * @param fileName  without extension; the right one is appended from [format].
     * @param afterSave called on the UI thread once the file is written, whether or
     *                  not an ad is going to show.
     */
    suspend fun run(
        ctx: Context,
        activity: Activity?,
        source: Bitmap,
        toolId: String,
        fileName: String,
        format: ExportOps.Format = ExportOps.Format.JPEG,
        treeUri: Uri? = null,
        plan: Plan = Plan.Standard(),
        afterSave: () -> Unit = {},
    ): Outcome {
        val sl = ServiceLocator
        val isPro = sl.entitlements.snapshot().isPro

        // ---- 1. free daily cap ----
        if (!isPro && sl.usage.exportsToday >= sl.flags.freeExportsPerDay) {
            // logPaywallShown(trigger, variant) — the variant is mandatory because
            // a paywall A/B comparison is worthless without it.
            Events.logPaywallShown("${toolId}_export_limit", sl.flags.paywallVariant)
            return Outcome.NeedsPro
        }

        return try {
            // ---- 2. free-tier transform + encode (one place, see encodeFor) ----
            val bytes = encodeFor(source, isPro, plan, format)

            // ---- 3. destination ----
            val full = if (fileName.contains('.')) fileName else "$fileName.${format.ext}"
            val uri: Uri? = if (treeUri != null) {
                ExportOps.saveToTree(ctx, treeUri, full, bytes, format.mime)
            } else {
                // Share the EXACT bytes we counted. Re-encoding from the bitmap here
                // would throw away an ID photo's carefully searched quality and could
                // put the file back over a government portal's KB limit.
                ExportOps.toShareUri(ctx, bytes, fileName, format)
            }

            // ---- 4. counters + analytics ----
            sl.prefs.noteExport()
            sl.usage.refresh()
            Events.logExportCompleted(toolId, source.width, source.height, bytes.size, isPro)

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

    /**
     * Export MANY files as ONE commercial event: a single daily-cap check, a single
     * export counted, a single analytics event and a single ad decision at the end.
     *
     * That is deliberate. A 40-photo batch is one user action on one natural break
     * point; firing 40 ad decisions would either spam the user or — far more likely —
     * be denied 39 times by AdPolicy's 180 s gap while still burning the daily
     * interstitial budget, which would then deny the *next* tool its ad.
     *
     * [write] runs once per file, in order, on [Dispatchers.IO]. It returns the number
     * of bytes written, or a negative value if that file failed. The tool owns its own
     * per-file transforms (Batch has four resize modes and a KB target); ExportFlow
     * owns the parts that are commercially sensitive.
     *
     * A run in which every file failed does NOT count against the daily cap — charging
     * a free user an export for nothing is the kind of thing that earns a 1-star review.
     */
    suspend fun runBatch(
        activity: Activity?,
        toolId: String,
        fileCount: Int,
        afterSave: () -> Unit = {},
        // LAST on purpose. Kotlin binds a trailing lambda to the final parameter, so if
        // this came before [afterSave] the caller's `{ index -> … }` would silently attach
        // to `afterSave` and produce "No value passed for parameter 'write'" plus a cascade
        // of type-inference errors at the call site. That cost one build round to find.
        write: suspend (index: Int) -> Long,
    ): BatchOutcome {
        val sl = ServiceLocator
        val isPro = sl.entitlements.snapshot().isPro

        // ---- 1. the same daily cap a single export faces ----
        // Batch counted an export on every run but never checked the cap, so a free
        // user could resize unlimited photos by running one-file batches all day
        // (bug #30). One run = one export, gated exactly like every other tool.
        if (!isPro && sl.usage.exportsToday >= sl.flags.freeExportsPerDay) {
            Events.logPaywallShown("${toolId}_export_limit", sl.flags.paywallVariant)
            return BatchOutcome.NeedsPro
        }

        return try {
            var done = 0
            var failed = 0
            var total = 0L
            for (i in 0 until fileCount) {
                val n = withContext(Dispatchers.IO) { write(i) }
                if (n < 0) failed++ else { done++; total += n }
            }
            if (done == 0) {
                afterSave()
                return BatchOutcome.Failed(if (failed > 0) "all_files_failed" else "nothing_to_do")
            }

            // ---- 2. counters + analytics: ONCE per run ----
            // Width/height stay 0 for batch, as they always have: the analytics schema
            // reads them as "count of files" and "unused" for this tool id.
            sl.prefs.noteExport()
            sl.usage.refresh()
            Events.logExportCompleted(toolId, done, 0, total.toInt(), isPro)

            // ---- 3. the ad decision, ONCE, on the natural break point ----
            if (activity != null) {
                sl.adCoordinator.afterExportCompleted(activity) { afterSave() }
            } else {
                afterSave()
            }
            BatchOutcome.Finished(done, failed, total)
        } catch (t: Throwable) {
            Events.logMlKitError("export_$toolId", t.javaClass.simpleName)
            afterSave()
            BatchOutcome.Failed(t.javaClass.simpleName)
        }
    }

    /**
     * The free-tier transform, in exactly one place: downscale (unless the plan
     * forbids it) → watermark → encode. Pro skips the first two.
     *
     * Intermediates are recycled here; the caller's [source] is NEVER recycled, and
     * nothing is handed back that the caller has to clean up.
     */
    private fun encodeFor(
        source: Bitmap,
        isPro: Boolean,
        plan: Plan,
        format: ExportOps.Format,
    ): ByteArray {
        val sized = if (isPro || plan is Plan.FixedDimensions) source
                    else BitmapOpsSafe.scaleToLongestEdge(source, FREE_MAX_EDGE)
        val stamped = if (isPro) sized else ExportOps.watermark(sized, WATERMARK)

        val bytes = when (plan) {
            is Plan.Standard -> ExportOps.encode(stamped, format, plan.quality)
            is Plan.FixedDimensions -> {
                val (_, quality) = ExportOps.searchQualityForTargetSize(
                    targetBytes = plan.maxBytes,
                    tolerancePct = plan.tolerancePct,
                ) { q -> ExportOps.encode(stamped, format, q).size }
                ExportOps.encode(stamped, format, quality)
            }
        }

        // `watermark` always copies, so `stamped !== sized` on the free path.
        if (stamped !== sized) stamped.recycle()
        if (sized !== source) sized.recycle()
        return bytes
    }

    /** Small indirection so this file has no hard dependency on a Bitmap import cycle. */
    private object BitmapOpsSafe {
        fun scaleToLongestEdge(src: Bitmap, edge: Int): Bitmap =
            com.photostudio.pro.core.image.BitmapOps.scaleToLongestEdge(src, edge, recycleSource = false)
    }
}
