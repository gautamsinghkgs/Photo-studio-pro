package com.photostudio.pro.core.image

import android.graphics.Bitmap
import android.graphics.Color

/**
 * M6 — White Background / Product Cleanup.
 *
 * This is the tool Amazon, eBay, Etsy and Shopify sellers actually need: their
 * marketplace rules require a **pure white (255,255,255)** background, and a
 * photo of a product on a wooden table does not qualify.
 *
 * ⚠️ HONEST SCOPE. This is a classical **flood-fill-from-border plus luminance
 * threshold with hysteresis** algorithm. It is NOT a segmentation model and NOT
 * generative. That distinction matters for two reasons:
 *
 *  1. Play's AI & deception policy. Calling this "AI White Background" would be
 *     a false claim about a generative capability we do not have.
 *  2. User expectations. It cannot invent a background behind a subject that
 *     touches the frame edge, and it cannot separate a white product from a
 *     white background. The UI says so.
 *
 * For real subject separation use the Background Remover (Subject Segmentation)
 * and then composite over white — that path handles hair and fine edges far
 * better. This tool exists for the much more common case: a reasonably
 * separated product on a light, unevenly lit wall or table.
 */
object WhiteBgOps {

    /**
     * Compute the luminance cutoff that separates background from subject.
     *
     * PURE — no Android types. Unit-tested.
     *
     * Strategy: sample the border ring (the background is nearly always
     * reachable from an edge in a product photo), take a high percentile of
     * those border luminances, then push the threshold up by `strength` so the
     * user can trade off between leaving grey patches and eating into the
     * subject.
     *
     * Hysteresis matters: a single global threshold leaves speckled grey
     * islands in shadows. We accept anything above `high`, reject anything
     * below `low`, and let the flood fill decide the band in between by
     * connectivity — which is what keeps a dark shadow *attached to the
     * subject* from being blown out to white.
     *
     * @param borderLuma luminance (0..255) of sampled border pixels
     * @param strength   0.0 (conservative) .. 1.0 (aggressive)
     * @return (lowThreshold, highThreshold)
     */
    fun computeThresholds(borderLuma: IntArray, strength: Float): Pair<Int, Int> =
        LayoutMath.computeThresholds(borderLuma, strength)



    /** Relative luminance (Rec. 709), 0..255. PURE. */
    fun luma(argb: Int): Int = LayoutMath.luma(argb)



    /**
     * Sample the border ring of an image. PURE over a flat pixel array.
     *
     * `step` skips pixels so a 4000x3000 photo does not produce a 14,000-entry
     * array; 8 is enough for a stable percentile.
     */
    fun sampleBorder(px: IntArray, w: Int, h: Int, step: Int = 8): IntArray =
        LayoutMath.sampleBorder(px, w, h, step)



    /**
     * Flood-fill from all four borders, whitening every pixel connected to the
     * edge whose luminance stays inside the hysteresis band.
     *
     * Iterative with an explicit stack — a recursive flood fill on a 12-megapixel
     * image blows the JVM stack, and this runs on a phone.
     *
     * PURE over a flat pixel array; mutates `px` in place and returns the number
     * of pixels whitened (useful for reporting "was this even a background?").
     */
    fun floodWhiten(px: IntArray, w: Int, h: Int, low: Int, high: Int): Int =
        LayoutMath.floodWhiten(px, w, h, low, high, Color.WHITE)



    /**
     * Soften the transition so the subject does not get a hard white halo.
     * Applies a small blur to the ALPHA of the whitened region only, at the
     * boundary, using the existing MaskOps feather.
     */
    fun softenEdge(px: IntArray, w: Int, h: Int, radius: Int = 2): IntArray {
        val isWhite = IntArray(w * h)
        for (i in px.indices) isWhite[i] = if (px[i] == Color.WHITE) 0xFF000000.toInt() else 0
        val blurred = MaskOps.featherInt(isWhite, w, h, radius, passes = 1)
        for (i in px.indices) {
            val a = (blurred[i] ushr 24) and 0xFF
            // Only touch pixels that are partly white now — fully white stays
            // pure (marketplaces measure 255,255,255), fully subject is left alone.
            if (a in 1..254) {
                val t = a / 255f
                val r = ((px[i] shr 16 and 0xFF) * (1 - t) + 255 * t).toInt()
                val g = ((px[i] shr 8 and 0xFF) * (1 - t) + 255 * t).toInt()
                val b = ((px[i] and 0xFF) * (1 - t) + 255 * t).toInt()
                px[i] = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
                        (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
            }
        }
        return px
    }

    // ---------------- Android-facing entry point ----------------

    /**
     * @param evenLighting first normalise exposure across the frame, which is
     *        what makes a shadowed corner of a white wall come out white too.
     * @return the whitened bitmap and how many pixels changed, so the UI can
     *         warn honestly when almost nothing happened (i.e. this photo needs
     *         the Background Remover instead).
     */
    fun makeWhite(
        src: Bitmap,
        strength: Float = 0.5f,
        evenLighting: Boolean = true,
        soften: Boolean = true,
    ): Result {
        val base = if (evenLighting) EnhanceOps.autoLevels(src) else src
        val w = base.width; val h = base.height
        val px = IntArray(w * h)
        base.getPixels(px, 0, w, 0, 0, w, h)

        val border = sampleBorder(px, w, h)
        val (low, high) = computeThresholds(border, strength)
        val changed = floodWhiten(px, w, h, low, high)
        if (soften) softenEdge(px, w, h)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (base !== src) base.recycle()

        return Result(out, changed, changed.toFloat() / (w.toFloat() * h))
    }

    data class Result(
        val bitmap: Bitmap,
        val pixelsWhitened: Int,
        /** Fraction of the frame that became white. */
        val coverage: Float,
    ) {
        /**
         * Under ~2% almost nothing happened: the subject fills the frame or the
         * background is dark/coloured. Telling the user this is better than
         * silently returning a photo that looks unchanged.
         */
        val probablyWrongTool: Boolean get() = coverage < 0.02f
    }
}
