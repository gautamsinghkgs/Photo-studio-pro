package com.photostudio.pro.core.image

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CLASSICAL image processing only. There is NO machine learning in this file.
 *
 * ⭐ NAMING (SPEC.md section 1 / master-plan section 6.3): the UI must call this
 * "Photo Enhance" or "Clarity". It must NEVER be called "AI Enhance",
 * "AI Restore" or anything implying generative capability — Google Play
 * explicitly rejects apps featuring "AI photo enhancers" that lack an in-app
 * offensive-content reporting mechanism, which we do not have and do not need.
 *
 * Equally, none of these operations invent pixels. There is NO scratch removal,
 * NO tear repair, NO colourisation, NO face reconstruction. The listing must
 * not claim any of them.
 */
object EnhanceOps {

    /** Auto-levels: stretch the histogram to its 0.5th and 99.5th percentiles. */
    fun autoLevels(src: Bitmap, clipPct: Float = 0.5f): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        for (c in px) hist[(c shr 16) and 0xFF]++          // red channel as luma proxy
        val total = px.size
        val clip = (total * clipPct / 100f).toInt()
        var acc = 0; var lo = 0; var hi = 255
        for (i in 0..255) { acc += hist[i]; if (acc > clip) { lo = i; break } }
        acc = 0
        for (i in 255 downTo 0) { acc += hist[i]; if (acc > clip) { hi = i; break } }
        if (hi - lo < 12) return src                        // nothing to stretch

        val lut = IntArray(256) { v -> (((v - lo).toFloat() / (hi - lo)) * 255f).toInt().coerceIn(0, 255) }
        applyLut(px, lut)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Auto white balance using the grey-world assumption: the average of R, G, B
     * across the image should be equal. Cheap, effective on product photos shot
     * under mixed indoor lighting.
     */
    fun autoWhiteBalance(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        var sr = 0L; var sg = 0L; var sb = 0L
        for (c in px) { sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
        val n = max(px.size, 1)
        val avgR = sr / n; val avgG = sg / n; val avgB = sb / n
        val grey = (avgR + avgG + avgB) / 3
        val kr = clampGain(grey, avgR); val kg = clampGain(grey, avgG); val kb = clampGain(grey, avgB)
        for (i in px.indices) {
            val c = px[i]
            val r = (((c shr 16) and 0xFF) * kr).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) * kg).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * kb).toInt().coerceIn(0, 255)
            px[i] = (c and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun clampGain(grey: Long, avg: Long): Float =
        if (avg <= 0L) 1f else (grey.toFloat() / avg.toFloat()).coerceIn(0.7f, 1.4f)

    /**
     * Unsharp mask. We build the blurred copy by downsampling then upsampling —
     * much cheaper than a full Gaussian and visually close enough at small radii.
     * (RenderScript is deprecated; RenderEffect needs API 31 and we support 24+.)
     *
     * @param amount 0..2, typically 0.6-1.0. Above ~1.5 you get halos.
     */
    fun unsharpMask(src: Bitmap, amount: Float = 0.8f, radiusPx: Int = 3): Bitmap {
        if (amount <= 0f) return src
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)

        val scale = max(1, radiusPx)
        val sw = max(1, w / (scale * 2)); val sh = max(1, h / (scale * 2))
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        if (small != blurred) small.recycle()
        val bpx = IntArray(w * h); blurred.getPixels(bpx, 0, w, 0, 0, w, h)
        blurred.recycle()

        for (i in px.indices) {
            val c = px[i]; val b = bpx[i]
            val r = ((c shr 16) and 0xFF) + amount * (((c shr 16) and 0xFF) - ((b shr 16) and 0xFF))
            val g = ((c shr 8) and 0xFF) + amount * (((c shr 8) and 0xFF) - ((b shr 8) and 0xFF))
            val bl = (c and 0xFF) + amount * ((c and 0xFF) - (b and 0xFF))
            px[i] = (c and (0xFF shl 24)) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    bl.toInt().coerceIn(0, 255)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Edge-preserving denoise: a 3x3 filter that averages only neighbours whose
     * luma is within [threshold] of the centre pixel. This smooths sensor noise
     * without smearing the subject's outline — important for product photos.
     */
    fun denoise(src: Bitmap, threshold: Int = 24): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                val cl = luma(c)
                var sr = 0; var sg = 0; var sb = 0; var n = 0
                for (dy in -1..1) {
                    val yy = y + dy; if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx; if (xx < 0 || xx >= w) continue
                        val p = px[yy * w + xx]
                        if (kotlin.math.abs(luma(p) - cl) <= threshold) {
                            sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF; n++
                        }
                    }
                }
                if (n == 0) { out[i] = c; continue }
                out[i] = (c and (0xFF shl 24)) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    /**
     * The one call the Enhance screen makes.
     *
     * @param brightness -255..255   @param contrast -1..1
     * @param saturation -1..1       @param gamma    0.2..5
     * @param sharpness  0..1.5      unsharp-mask amount (bug #22: this parameter
     *                               did not exist, so the two sliders on the
     *                               Enhance screen were inert — an inert control
     *                               is the same bug class as #10)
     * @param denoise    0..60       strength of the edge-preserving 3x3 filter
     *
     * Filter ORDER is deliberate: denoise -> colour -> sharpen. Sharpening before
     * denoising amplifies exactly the sensor noise the user asked to remove, and
     * colour maths after sharpening would clip the halos the mask just created.
     */
    fun adjust(src: Bitmap, brightness: Int = 0, contrast: Float = 0f,
               saturation: Float = 0f, gamma: Float = 1f,
               sharpness: Float = 0f, denoise: Int = 0): Bitmap {
        val colourNeutral = brightness == 0 && contrast == 0f &&
                saturation == 0f && gamma == 1f
        if (colourNeutral && sharpness <= 0f && denoise <= 0) return src

        // `cur` is always either `src` (caller-owned, never recycled here) or a
        // bitmap this function created — intermediates are recycled as we go so a
        // 12 MP photo does not hold three full buffers at once (SPEC.md OOM rule).
        var cur = src

        if (denoise > 0) {
            // slider 0..60 -> luma threshold 40..6. A SMALLER threshold means fewer
            // neighbours qualify for the average, i.e. gentler smoothing; 60 is the
            // strongest setting the UI offers.
            val threshold = (40 - denoise.coerceIn(0, 60) * 34 / 60).coerceIn(6, 40)
            // Qualified call: the `denoise` PARAMETER shadows the `denoise` FUNCTION.
            val d = EnhanceOps.denoise(cur, threshold)
            if (d !== cur && cur !== src) cur.recycle()
            cur = d
        }

        if (!colourNeutral) {
            val a = adjustColour(cur, brightness, contrast, saturation, gamma)
            if (a !== cur && cur !== src) cur.recycle()
            cur = a
        }

        if (sharpness > 0f) {
            val s = unsharpMask(cur, sharpness.coerceIn(0f, 1.5f))
            if (s !== cur && cur !== src) cur.recycle()
            cur = s
        }

        return cur
    }

    /** The per-pixel colour pass, split out of [adjust] so it can be chained with
     *  the spatial filters. Returns a NEW bitmap unless every parameter is neutral. */
    private fun adjustColour(src: Bitmap, brightness: Int, contrast: Float,
                             saturation: Float, gamma: Float): Bitmap {
        if (brightness == 0 && contrast == 0f && saturation == 0f && gamma == 1f) return src
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val cFactor = (1f + contrast).coerceIn(0f, 3f)
        val invGamma = 1f / gamma.coerceIn(0.2f, 5f)
        for (i in px.indices) {
            val c = px[i]
            var r = (c shr 16) and 0xFF; var g = (c shr 8) and 0xFF; var b = c and 0xFF
            // contrast around midpoint
            r = ((r - 128) * cFactor + 128).toInt(); g = ((g - 128) * cFactor + 128).toInt(); b = ((b - 128) * cFactor + 128).toInt()
            // brightness
            r += brightness; g += brightness; b += brightness
            // saturation via luma
            if (saturation != 0f) {
                val l = luma((0xFF shl 24) or (r.coerceIn(0,255) shl 16) or (g.coerceIn(0,255) shl 8) or b.coerceIn(0,255))
                r = (l + (r - l) * (1f + saturation)).toInt()
                g = (l + (g - l) * (1f + saturation)).toInt()
                b = (l + (b - l) * (1f + saturation)).toInt()
            }
            // gamma
            if (invGamma != 1f) {
                r = (255f * (r.coerceIn(0,255) / 255f).pow(invGamma)).toInt()
                g = (255f * (g.coerceIn(0,255) / 255f).pow(invGamma)).toInt()
                b = (255f * (b.coerceIn(0,255) / 255f).pow(invGamma)).toInt()
            }
            px[i] = (c and (0xFF shl 24)) or
                    (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** One-tap Auto: white balance -> levels -> mild sharpen -> mild saturation. */
    fun auto(src: Bitmap): Bitmap {
        var b = autoWhiteBalance(src)
        val t1 = if (b !== src) b else null
        b = autoLevels(b); t1?.recycle()
        b = unsharpMask(b, amount = 0.5f, radiusPx = 2)
        b = adjust(b, saturation = 0.08f)
        return b
    }

    private fun applyLut(px: IntArray, lut: IntArray) {
        for (i in px.indices) {
            val c = px[i]
            px[i] = (c and (0xFF shl 24)) or
                    (lut[(c shr 16) and 0xFF] shl 16) or
                    (lut[(c shr 8) and 0xFF] shl 8) or
                    lut[c and 0xFF]
        }
    }

    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
    }

    /** Average colour of a border band — used by the ID photo background check. */
    fun averageBorder(src: Bitmap, bandPct: Float = 0.08f): Int {
        val w = src.width; val h = src.height
        val band = max(1, (min(w, h) * bandPct).toInt())
        var sr = 0L; var sg = 0L; var sb = 0L; var n = 0L
        fun acc(c: Int) { sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF; n++ }
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until h) for (x in 0 until w)
            if (x < band || x >= w - band || y < band || y >= h - band) acc(px[y * w + x])
        if (n == 0L) return 0
        return ((sr / n).toInt() shl 16) or ((sg / n).toInt() shl 8) or (sb / n).toInt()
    }
}
