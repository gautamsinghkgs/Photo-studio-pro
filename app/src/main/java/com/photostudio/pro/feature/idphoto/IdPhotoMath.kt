package com.photostudio.pro.feature.idphoto

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * PURE geometry and spec math for the Passport / ID Photo tool.
 *
 * This file deliberately has NO Android imports so it can be unit-tested on the
 * JVM in seconds — which matters because getting these numbers wrong produces a
 * photo a government office rejects, and that is a 1-star review you cannot
 * argue with. See src/test/.../IdPhotoMathTest.kt
 *
 * Coordinate convention: y increases DOWNWARD from the TOP edge of the photo,
 * which is how Android bitmaps work. Government specs usually quote the eye
 * line measured from the BOTTOM, so [eyeMinPct]/[eyeMaxPct] in countries.json
 * are stored as fractions of height from the bottom and converted here.
 */
object IdPhotoMath {

    /** Millimetres to pixels at a given DPI. 1 inch = 25.4 mm. */
    fun mmToPx(mm: Double, dpi: Int): Int = (mm / 25.4 * dpi).roundToInt()

    /**
     * How many copies of a [photoW]x[photoH] photo fit on a print sheet, with a
     * [gapMm] gutter between copies and a [marginMm] border around the sheet.
     * Returns (columns, rows, total). Pure integer grid packing — good enough for
     * rectangular photos and far easier to verify than a bin-packing algorithm.
     */
    fun fitOnSheet(
        sheetWMm: Double, sheetHMm: Double,
        photoWMm: Double, photoHMm: Double,
        marginMm: Double = 3.0, gapMm: Double = 2.0,
    ): Triple<Int, Int, Int> {
        val usableW = sheetWMm - 2 * marginMm
        val usableH = sheetHMm - 2 * marginMm
        if (usableW < photoWMm || usableH < photoHMm) return Triple(0, 0, 0)
        // n photos need n*w + (n-1)*gap <= usable
        val cols = ((usableW + gapMm) / (photoWMm + gapMm)).toInt().coerceAtLeast(0)
        val rows = ((usableH + gapMm) / (photoHMm + gapMm)).toInt().coerceAtLeast(0)
        return Triple(cols, rows, cols * rows)
    }

    /** Common sheet sizes in millimetres. */
    val SHEETS = linkedMapOf(
        "4x6 in"  to (101.6 to 152.4),
        "5x7 in"  to (127.0 to 177.8),
        "A4"      to (210.0 to 297.0),
        "Letter"  to (215.9 to 279.4),
        "A5"      to (148.0 to 210.0),
    )

    /**
     * Given a detected face box inside the source image and a country spec,
     * compute the crop rectangle that puts the head at the right proportion of
     * the frame.
     *
     * [headTargetPct] is where in the allowed head-height band we aim — 0.75 sits
     * comfortably inside most specs rather than on a boundary, which is where
     * rejections happen.
     *
     * @param faceTop    y of the top of the head, in source pixels
     * @param faceBottom y of the chin, in source pixels
     * @return crop rect [x, y, w, h] in source pixels, or null if the face is too
     *         small / too close to an edge to frame correctly.
     */
    fun computeCrop(
        srcW: Int, srcH: Int,
        faceTop: Int, faceBottom: Int,
        specW: Double, specH: Double,
        headTargetPct: Double = 0.75,
    ): IntArray? {
        val faceH = faceBottom - faceTop
        if (faceH <= 0 || srcW <= 0 || srcH <= 0) return null
        if (headTargetPct <= 0.0 || headTargetPct > 1.0) return null

        // The crop height is whatever makes the head occupy headTargetPct of it.
        val cropH = (faceH / headTargetPct).roundToInt()
        if (cropH < 8 || cropH > srcH * 4) return null
        val aspect = specW / specH
        val cropW = (cropH * aspect).roundToInt()
        if (cropW < 8) return null

        // Vertically: leave a little space above the head. Most specs want the
        // crown between 4% and 12% below the top edge.
        val headRoomPx = (cropH * 0.07).roundToInt()
        var y = faceTop - headRoomPx
        // Horizontally: centre on the face.
        val faceCx = ((faceLeftOf(faceTop, faceBottom, srcW)).let { it })   // see below
        var x = faceCx - cropW / 2

        // Clamp into the source. If the crop cannot fit without shrinking,
        // the caller should ask for a wider photo rather than silently producing
        // a photo with the wrong head proportion.
        x = x.coerceIn(0, (srcW - cropW).coerceAtLeast(0))
        y = y.coerceIn(0, (srcH - cropH).coerceAtLeast(0))
        val w = minOf(cropW, srcW - x)
        val h = minOf(cropH, srcH - y)
        if (w < cropW || h < cropH) return null
        return intArrayOf(x, y, w, h)
    }

    /**
     * Placeholder for the horizontal face centre. The real value comes from the
     * ML Kit Face bounding box and is passed in by the caller via
     * [computeCropWithBox]. Kept as a separate entry point so this function stays
     * pure and the geometry stays obvious in tests.
     */
    private fun faceLeftOf(@Suppress("UNUSED_PARAMETER") top: Int,
                           @Suppress("UNUSED_PARAMETER") bottom: Int,
                           srcW: Int): Int = srcW / 2

    /**
     * The production entry point: crop using the actual detected face box.
     * @param faceLeft/faceTop/faceRight/faceBottom from ML Kit's Face.boundingBox
     */
    fun computeCropWithBox(
        srcW: Int, srcH: Int,
        faceLeft: Int, faceTop: Int, faceRight: Int, faceBottom: Int,
        specW: Double, specH: Double,
        headTargetPct: Double = 0.75,
    ): IntArray? {
        val faceH = faceBottom - faceTop
        if (faceH <= 0) return null
        val cropH = (faceH / headTargetPct).roundToInt()
        if (cropH < 8) return null
        val cropW = (cropH * (specW / specH)).roundToInt()
        if (cropW < 8) return null
        val faceCx = (faceLeft + faceRight) / 2
        var x = faceCx - cropW / 2
        var y = faceTop - (cropH * 0.07).roundToInt()
        x = x.coerceIn(0, (srcW - cropW).coerceAtLeast(0))
        y = y.coerceIn(0, (srcH - cropH).coerceAtLeast(0))
        if (srcW - x < cropW || srcH - y < cropH) return null
        return intArrayOf(x, y, cropW, cropH)
    }

    // ---------------- Live compliance guidance ----------------

    data class Check(val key: String, val ok: Boolean, val detail: String)

    /**
     * Head-height check. [headPct] is the fraction of the photo height occupied
     * by the head (chin to crown).
     */
    fun checkHeadSize(headPct: Double, minPct: Double, maxPct: Double): Check {
        if (maxPct <= minPct) return Check("head", true, "no band defined")
        val ok = headPct in minPct..maxPct
        val detail = when {
            ok -> "%.0f%%".format(headPct * 100)
            headPct < minPct -> "head too small — move closer"
            else -> "head too large — move back"
        }
        return Check("head", ok, detail)
    }

    /**
     * Eye-line check. Specs quote a band measured from the BOTTOM of the photo;
     * Android measures from the TOP. [eyeFromTopPct] is converted here.
     * A spec with eyeMinPct == 0.0 and eyeMaxPct == 1.0 means "not specified",
     * so we pass it rather than inventing a rule.
     */
    fun checkEyeLine(eyeFromTopPct: Double, minFromBottomPct: Double, maxFromBottomPct: Double): Check {
        if (minFromBottomPct <= 0.0 && maxFromBottomPct >= 1.0)
            return Check("eye", true, "not specified")
        val fromBottom = 1.0 - eyeFromTopPct
        val ok = fromBottom in minFromBottomPct..maxFromBottomPct
        return Check("eye", ok, "%.0f%% from bottom".format(fromBottom * 100))
    }

    /**
     * Background-colour check. Government specs say "white" or "light grey" but
     * a real photo is never exactly #FFFFFF because of lighting, so we compare
     * with a tolerance on each channel AND require low saturation (a coloured
     * wall would otherwise pass a per-channel test).
     */
    fun checkBackground(sampledArgb: Int, requiredArgb: Int, tolerance: Int = 34): Check {
        val dr = abs(((sampledArgb shr 16) and 0xFF) - ((requiredArgb shr 16) and 0xFF))
        val dg = abs(((sampledArgb shr 8) and 0xFF) - ((requiredArgb shr 8) and 0xFF))
        val db = abs((sampledArgb and 0xFF) - (requiredArgb and 0xFF))
        val maxDelta = maxOf(dr, dg, db)
        val sat = saturation(sampledArgb)
        // ⚠️ The saturation gate may ONLY apply when the REQUIRED background is
        // itself achromatic. Indonesia mandates a RED background and Malaysia a
        // BLUE one; with an unconditional `sat <= 0.18f` every single Indonesian
        // and Malaysian user got a false "background is tinted" warning on a
        // perfectly compliant photo. The gate exists to catch a coloured wall
        // behind a user who needs white, not to reject a spec that asks for colour.
        val requiredIsAchromatic = saturation(requiredArgb) <= 0.18f
        val ok = maxDelta <= tolerance && (!requiredIsAchromatic || sat <= 0.18f)
        val detail = when {
            ok -> "matches"
            requiredIsAchromatic && sat > 0.18f ->
                "background is tinted — use the white-background tool first"
            else -> "background colour does not match the spec (Δ$maxDelta)"
        }
        return Check("bg", ok, detail)
    }

    /** 0 = grey, 1 = fully saturated. */
    fun saturation(argb: Int): Float {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        if (mx == 0) return 0f
        return (mx - mn).toFloat() / mx.toFloat()
    }

    /** Would the exported JPEG likely exceed the spec's file-size limit? */
    fun estimateJpegBytes(widthPx: Int, heightPx: Int, quality: Int = 90): Int {
        // Empirical constant for photographic content at quality ~90.
        // Deliberately conservative — better to warn early than to be wrong late.
        val bpp = when {
            quality >= 95 -> 0.62
            quality >= 85 -> 0.45
            quality >= 70 -> 0.30
            else          -> 0.18
        }
        return (widthPx * heightPx * bpp).roundToInt()
    }
}
