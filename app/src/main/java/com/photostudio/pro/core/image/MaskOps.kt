package com.photostudio.pro.core.image

import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Turns an ML Kit confidence mask into a usable alpha channel.
 *
 * ML Kit returns a FloatBuffer of `width * height` confidence values in 0..1
 * where 1 = definitely foreground. Verified against Google's official Subject
 * Segmentation doc (updated 2026-10-28):
 *
 *   val foregroundMask = result.foregroundConfidenceMask
 *   for (i in 0 until image.width * image.height) { if (foregroundMask[i] > 0.5f) ... }
 *
 * Two things this file does that the doc snippet does not, and that matter a
 * lot for review quality:
 *  1. A SOFT RAMP instead of a hard 0.5 threshold. A hard cut looks like a
 *     sticker and is the most common complaint about cheap cut-out apps.
 *  2. A FEATHER (box blur on the alpha) so hair and edges blend. Subject
 *     Segmentation is BETA and weaker than Photoroom on fine edges — we never
 *     promise "perfect" edges, but feathering removes the worst artefacts.
 */
object MaskOps {

    /**
     * @param mask       foreground confidence, `maskW * maskH` entries, 0..1
     * @param softness   width of the confidence ramp. 0.12 gives a subtle
     *                   transition; 0 = hard cut.
     * @param threshold  centre of the ramp.
     */
    fun maskToAlpha(
        mask: FloatBuffer,
        maskW: Int,
        maskH: Int,
        threshold: Float = 0.5f,
        softness: Float = 0.12f,
    ): ByteArray {
        val n = maskW * maskH
        val alpha = ByteArray(n)
        val lo = threshold - softness / 2f
        val hi = threshold + softness / 2f
        val span = max(hi - lo, 1e-4f)
        // Rewind in case the buffer was already read once.
        mask.rewind()
        for (i in 0 until n) {
            val c = mask.get()
            alpha[i] = when {
                c <= lo -> 0
                c >= hi -> 255.toByte()
                else -> (((c - lo) / span) * 255f).toInt().coerceIn(0, 255).toByte()
            }
        }
        return alpha
    }

    /** Separable box blur on a single-channel alpha, [passes] times. */
    fun feather(alpha: ByteArray, w: Int, h: Int, radius: Int, passes: Int = 2): ByteArray {
        if (radius <= 0 || passes <= 0) return alpha
        var src = alpha
        var dst = ByteArray(w * h)
        repeat(passes) {
            boxBlurH(src, dst, w, h, radius); val t1 = src; src = dst; dst = t1
            boxBlurV(src, dst, w, h, radius); val t2 = src; src = dst; dst = t2
        }
        return src
    }

    private fun boxBlurH(s: ByteArray, d: ByteArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (y in 0 until h) {
            var sum = 0
            val row = y * w
            for (x in -r..r) sum += (s[row + x.coerceIn(0, w - 1)]).toInt() and 0xFF
            for (x in 0 until w) {
                d[row + x] = (sum / div).toByte()
                val add = (s[row + min(x + r + 1, w - 1)]).toInt() and 0xFF
                val sub = (s[row + max(x - r, 0)]).toInt() and 0xFF
                sum += add - sub
            }
        }
    }

    private fun boxBlurV(s: ByteArray, d: ByteArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (x in 0 until w) {
            var sum = 0
            for (y in -r..r) sum += (s[y.coerceIn(0, h - 1) * w + x]).toInt() and 0xFF
            for (y in 0 until h) {
                d[y * w + x] = (sum / div).toByte()
                val add = (s[min(y + r + 1, h - 1) * w + x]).toInt() and 0xFF
                val sub = (s[max(y - r, 0) * w + x]).toInt() and 0xFF
                sum += add - sub
            }
        }
    }

    /**
     * Apply an alpha channel to [source], producing a transparent PNG-ready
     * bitmap. The mask may be at a different resolution than the source (ML Kit
     * often returns it at the InputImage size), so we resample with bilinear.
     */
    fun applyAlpha(source: Bitmap, alpha: ByteArray, maskW: Int, maskH: Int): Bitmap {
        val w = source.width; val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        source.getPixels(px, 0, w, 0, 0, w, h)

        val sameSize = (maskW == w && maskH == h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = if (sameSize) {
                    (alpha[y * w + x]).toInt() and 0xFF
                } else {
                    bilinearSample(alpha, maskW, maskH, x.toFloat() * maskW / w, y.toFloat() * maskH / h)
                }
                val c = px[y * w + x]
                // Multiply existing alpha by the mask so we never resurrect
                // transparency that was not there.
                val srcA = (c ushr 24) and 0xFF
                px[y * w + x] = (min(srcA, a) shl 24) or (c and 0x00FFFFFF)
            }
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun bilinearSample(buf: ByteArray, w: Int, h: Int, fx: Float, fy: Float): Int {
        val x0 = fx.toInt().coerceIn(0, w - 1); val y0 = fy.toInt().coerceIn(0, h - 1)
        val x1 = min(x0 + 1, w - 1);            val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0; val ty = fy - y0
        fun at(x: Int, y: Int) = (buf[y * w + x]).toInt() and 0xFF
        val top = at(x0, y0) * (1 - tx) + at(x1, y0) * tx
        val bot = at(x0, y1) * (1 - tx) + at(x1, y1) * tx
        return (top * (1 - ty) + bot * ty).toInt().coerceIn(0, 255)
    }

    /**
     * Is the mask effectively empty? ML Kit returns an all-zero mask when it
     * cannot find a subject, and silently producing a fully transparent image
     * would look like the app is broken. We detect it and show guidance instead.
     */
    fun foregroundCoverage(alpha: ByteArray): Float {
        if (alpha.isEmpty()) return 0f
        var fg = 0
        for (b in alpha) if ((b.toInt() and 0xFF) > 127) fg++
        return fg.toFloat() / alpha.size
    }

    /** Replace near-[target] pixels with [replacement]. Used by White Background. */
    fun replaceColor(bmp: Bitmap, targetArgb: Int, tolerance: Int, replacementArgb: Int): Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val tr = (targetArgb shr 16) and 0xFF; val tg = (targetArgb shr 8) and 0xFF; val tb = targetArgb and 0xFF
        val rr = (replacementArgb shr 16) and 0xFF; val rg = (replacementArgb shr 8) and 0xFF; val rb = replacementArgb and 0xFF
        val tol2 = tolerance * tolerance * 3
        for (i in px.indices) {
            val c = px[i]
            val dr = ((c shr 16) and 0xFF) - tr
            val dg = ((c shr 8) and 0xFF) - tg
            val db = (c and 0xFF) - tb
            if (dr * dr + dg * dg + db * db <= tol2) {
                px[i] = (0xFF shl 24) or (rr shl 16) or (rg shl 8) or rb
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b
    private fun max(a: Int, b: Int) = if (a > b) a else b
    private fun abs(a: Int) = if (a < 0) -a else a

    /**
     * Integer-array variant of [feather] for callers that already hold a packed
     * 0xAARRGGBB buffer (WhiteBgOps) instead of a float alpha mask.
     *
     * Blurs only the ALPHA channel, separable box blur, `passes` times. PURE —
     * no Android types, so it is unit-testable on the JVM.
     */
    fun featherInt(argb: IntArray, w: Int, h: Int, radius: Int, passes: Int = 2): IntArray {
        if (w <= 0 || h <= 0 || argb.size < w * h || radius <= 0) return argb
        val r = radius.coerceAtMost(minOf(w, h) / 2).coerceAtLeast(1)
        var a = IntArray(w * h) { (argb[it] ushr 24) and 0xFF }
        val tmp = IntArray(w * h)
        val div = 2 * r + 1
        repeat(passes.coerceAtLeast(1)) {
            // horizontal
            for (y in 0 until h) {
                val row = y * w
                var sum = 0
                for (i in -r..r) sum += a[row + (i.coerceIn(0, w - 1))]
                for (x in 0 until w) {
                    tmp[row + x] = sum / div
                    sum += a[row + ((x + r + 1).coerceAtMost(w - 1))]
                    sum -= a[row + ((x - r).coerceAtLeast(0))]
                }
            }
            // vertical
            for (x in 0 until w) {
                var sum = 0
                for (i in -r..r) sum += tmp[(i.coerceIn(0, h - 1)) * w + x]
                for (y in 0 until h) {
                    a[y * w + x] = sum / div
                    sum += tmp[((y + r + 1).coerceAtMost(h - 1)) * w + x]
                    sum -= tmp[((y - r).coerceAtLeast(0)) * w + x]
                }
            }
        }
        val out = IntArray(w * h)
        for (i in out.indices) out[i] = ((a[i].coerceIn(0, 255)) shl 24) or (argb[i] and 0x00FFFFFF)
        return out
    }
}
