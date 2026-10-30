package com.photostudio.pro.core.image

/**
 * PURE layout and pixel maths, with no Android imports at all.
 *
 * Everything here can be — and is — unit-tested on a plain JVM without an
 * emulator, a device or Robolectric. It is split out from CollageOps, PdfOps and
 * WhiteBgOps for the same reason QualitySearch was: these are the few places
 * where an off-by-one produces silently wrong output (a stretched product photo,
 * an upside-down PDF page, a white background that ate the subject) and no crash
 * ever tells you.
 *
 * Rule: if you add maths to a tool, add it here and add a test.
 */
object LayoutMath {

    /** One cell of a collage, in NORMALISED coordinates (0..1 of the canvas). */
    data class Cell(val x: Float, val y: Float, val w: Float, val h: Float) {
        /** Cells must stay inside the unit square or they render off-canvas. */
        val isValid: Boolean
            get() = w > 0f && h > 0f && x >= 0f && y >= 0f && x + w <= 1.0001f && y + h <= 1.0001f
    }

    // ================= COLLAGE TILING =================

    /** Even `cols x rows` grid over the unit square. */
    fun grid(cols: Int, rows: Int): List<Cell> {
        val c = cols.coerceAtLeast(1); val r = rows.coerceAtLeast(1)
        val out = ArrayList<Cell>(c * r)
        val cw = 1f / c; val ch = 1f / r
        for (y in 0 until r) for (x in 0 until c) out.add(Cell(x * cw, y * ch, cw, ch))
        return out
    }

    /** `n` equal horizontal strips (stacked vertically). */
    fun rows(n: Int): List<Cell> = grid(1, n)

    /** `n` equal vertical strips (side by side). */
    fun cols(n: Int): List<Cell> = grid(n, 1)

    /** One large cell on the left plus `n` stacked on the right. */
    fun heroLeft(n: Int, heroFraction: Float = 0.58f): List<Cell> {
        val f = heroFraction.coerceIn(0.2f, 0.8f)
        val k = n.coerceAtLeast(1)
        val out = ArrayList<Cell>(k + 1)
        out.add(Cell(0f, 0f, f, 1f))
        val rh = 1f / k
        for (i in 0 until k) out.add(Cell(f, i * rh, 1f - f, rh))
        return out
    }

    /** Mirror of [heroLeft]. */
    fun heroRight(n: Int, heroFraction: Float = 0.58f): List<Cell> {
        val f = heroFraction.coerceIn(0.2f, 0.8f)
        val k = n.coerceAtLeast(1)
        val out = ArrayList<Cell>(k + 1)
        out.add(Cell(1f - f, 0f, f, 1f))
        val rh = 1f / k
        for (i in 0 until k) out.add(Cell(0f, i * rh, 1f - f, rh))
        return out
    }

    /** Four equal quadrants. */
    fun centreFour(): List<Cell> = grid(2, 2)

    // ================= CENTRE-CROP =================

    /**
     * Centre-crop source rect for a destination of the given aspect, as
     * [x, y, w, h] in SOURCE pixels.
     *
     * An error here shows up as a stretched product photo or a visible seam in a
     * collage, and never as a crash — so it is tested exhaustively.
     */
    fun centerCropRect(srcW: Int, srcH: Int, dstW: Float, dstH: Float): IntArray {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0f || dstH <= 0f)
            return intArrayOf(0, 0, maxOf(srcW, 1), maxOf(srcH, 1))
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW / dstH
        return if (srcAspect > dstAspect) {
            // source wider than the cell -> crop left and right
            val w = (srcH * dstAspect).toInt().coerceIn(1, srcW)
            val x = (srcW - w) / 2
            intArrayOf(x, 0, w, srcH)
        } else {
            // source taller than the cell -> crop top and bottom
            val h = (srcW / dstAspect).toInt().coerceIn(1, srcH)
            val y = (srcH - h) / 2
            intArrayOf(0, y, srcW, h)
        }
    }

    // ================= PDF PAGE PLACEMENT =================

    /**
     * Where to place an image inside a PDF page's available box, as
     * [x, y, w, h] in PDF points.
     *
     * ⚠️ PDF's origin is the BOTTOM-left, not the top-left. Getting this wrong
     * puts every page upside-down or off the sheet, and it is the single most
     * common bug in image-to-PDF code. Because the returned rect is centred,
     * a symmetric FILL crop also relies on this being right.
     *
     * @param fill true = cover the box and crop the overflow;
     *             false = contain it and letterbox. Contain is the default
     *             because cropping an ID document or an invoice is data loss.
     */
    fun placeRectOnPage(
        imgW: Int, imgH: Int,
        availW: Float, availH: Float,
        marginPt: Float,
        fill: Boolean,
    ): FloatArray {
        if (imgW <= 0 || imgH <= 0 || availW <= 0f || availH <= 0f)
            return floatArrayOf(marginPt, marginPt, availW.coerceAtLeast(1f), availH.coerceAtLeast(1f))
        val imgAspect = imgW.toFloat() / imgH
        val boxAspect = availW / availH
        val w: Float; val h: Float
        if (fill) {
            if (imgAspect > boxAspect) { h = availH; w = availH * imgAspect }
            else                       { w = availW; h = availW / imgAspect }
        } else {
            if (imgAspect > boxAspect) { w = availW; h = availW / imgAspect }
            else                       { h = availH; w = availH * imgAspect }
        }
        val x = marginPt + (availW - w) / 2f
        val y = marginPt + (availH - h) / 2f
        return floatArrayOf(x, y, w, h)
    }

    // ================= WHITE BACKGROUND =================

    /** Relative luminance (Rec. 709), 0..255. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
    }

    /**
     * Luminance cutoffs that separate background from subject, using hysteresis.
     *
     * A single global threshold leaves speckled grey islands in shadows. We
     * accept anything at or above `high`, reject anything below `low`, and let
     * the flood fill decide the band in between by connectivity — which is what
     * stops a dark shadow ATTACHED TO THE SUBJECT from being blown out to white.
     *
     * @param borderLuma luminance of sampled border pixels
     * @param strength   0.0 conservative .. 1.0 aggressive
     */
    fun computeThresholds(borderLuma: IntArray, strength: Float): Pair<Int, Int> {
        if (borderLuma.isEmpty()) return 200 to 245
        val sorted = borderLuma.sortedArray()
        // 35th percentile: robust against a few dark edge pixels (a shadow in one
        // corner) dragging the estimate of "what the background looks like" down.
        val pct = sorted[((sorted.size - 1) * 0.35f).toInt()].coerceIn(0, 255)
        val s = strength.coerceIn(0f, 1f)

        // The border percentile IS our estimate of the background luminance, so
        // both gates must sit AT OR BELOW it, never above.
        //
        // ⚠️ The previous formula lifted the gate to pct + (255-pct)*k, which put
        // it ABOVE the observed background. On a dimly lit white wall (pct ~120)
        // the gate came out at ~173, so the wall itself failed the test and the
        // tool whitened nothing while reporting success. That is the worst kind of
        // bug: no crash, no error, just a photo that looks unchanged.
        //
        // low  = connectivity gate. Set below the background estimate so uneven
        //        lighting across the SAME wall still qualifies. Strength widens
        //        the gap, trading subject safety for coverage.
        // high = "definitely background" gate used to SEED the flood. Anything at
        //        or above it is background regardless of connectivity, which is
        //        what makes the two-gate scheme real hysteresis rather than one
        //        threshold with extra steps.
        val tolerance = (20 + 60 * s).toInt()              // 20..80 below the estimate
        val low = (pct - tolerance).coerceIn(0, 250)
        val high = (pct + 8).coerceIn(low + 1, 255)
        return low to high
    }

    /** Sample the border ring of a flat pixel buffer. `step` keeps it small. */
    fun sampleBorder(px: IntArray, w: Int, h: Int, step: Int = 8): IntArray {
        if (w <= 0 || h <= 0 || px.size < w * h) return IntArray(0)
        val out = ArrayList<Int>((w + h) * 2 / step.coerceAtLeast(1) + 4)
        val s = step.coerceAtLeast(1)
        for (x in 0 until w step s) { out.add(luma(px[x])); out.add(luma(px[(h - 1) * w + x])) }
        for (y in 0 until h step s) { out.add(luma(px[y * w])); out.add(luma(px[y * w + (w - 1)])) }
        return out.toIntArray()
    }

    /**
     * Flood-fill from all four borders, whitening every edge-connected pixel
     * inside the hysteresis band. Mutates `px` in place, returns pixels changed.
     *
     * Iterative with an explicit stack: a recursive flood fill on a
     * 12-megapixel image blows the stack, and this runs on a phone.
     */
    fun floodWhiten(px: IntArray, w: Int, h: Int, low: Int, high: Int,
                    whiteArgb: Int = -1): Int {
        if (w <= 0 || h <= 0 || px.size < w * h) return 0
        val lo = low.coerceIn(0, 255)
        val hi = high.coerceIn(lo, 255)
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0; var count = 0

        // Propagation gate: anything at or above `lo` may be whitened, but only
        // if it is reachable from something already whitened. This is what keeps
        // a mid-grey shadow ATTACHED TO THE SUBJECT from being blown out, while
        // still allowing a shadowed corner of the wall to go white.
        fun pushIfBg(idx: Int) {
            if (visited[idx]) return
            if (luma(px[idx]) < lo) return
            visited[idx] = true
            stack[sp++] = idx
        }
        // Seed gate: border pixels at or above `hi` are unambiguously background
        // and start the flood. Border pixels between `lo` and `hi` may still join
        // once a seed reaches them.
        fun pushSeed(idx: Int) {
            if (visited[idx]) return
            if (luma(px[idx]) < hi) { pushIfBg(idx); return }
            visited[idx] = true
            stack[sp++] = idx
        }
        for (x in 0 until w) { pushSeed(x); pushSeed((h - 1) * w + x) }
        for (y in 0 until h) { pushSeed(y * w); pushSeed(y * w + (w - 1)) }

        while (sp > 0) {
            val idx = stack[--sp]
            if (luma(px[idx]) >= lo) {
                px[idx] = whiteArgb
                count++
                val x = idx % w; val y = idx / w
                if (x > 0)     pushIfBg(idx - 1)
                if (x < w - 1) pushIfBg(idx + 1)
                if (y > 0)     pushIfBg(idx - w)
                if (y < h - 1) pushIfBg(idx + w)
            }
        }
        return count
    }
}
