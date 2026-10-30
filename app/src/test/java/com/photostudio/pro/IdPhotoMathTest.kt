package com.photostudio.pro

import com.photostudio.pro.feature.idphoto.IdPhotoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The maths that decides whether a printed ID photo is the right PHYSICAL size.
 * Getting mm-to-pixel conversion wrong is invisible on screen and fatal at the
 * print shop or the government upload portal.
 */
class IdPhotoMathTest {

    // ---- Unit conversion ----

    @Test fun mmToPxAt300dpi() {
        // 1 inch = 25.4 mm = 300 px  ->  1 mm = 11.811 px
        assertEquals(300, IdPhotoMath.mmToPx(25.4, 300))
        assertEquals(600, IdPhotoMath.mmToPx(50.8, 300))
        assertEquals(118, IdPhotoMath.mmToPx(10.0, 300))
    }

    @Test fun mmToPxAt600dpi() {
        assertEquals(600, IdPhotoMath.mmToPx(25.4, 600))
        assertEquals(1200, IdPhotoMath.mmToPx(50.8, 600))
    }

    @Test fun indiaSpecIs35x45mm() {
        // 35 mm x 45 mm at 300 dpi = 413 x 531 px. These exact numbers appear
        // in the Indian passport application portal, so they must match.
        assertEquals(413, IdPhotoMath.mmToPx(35.0, 300))
        assertEquals(531, IdPhotoMath.mmToPx(45.0, 300))
    }

    @Test fun usSpecIsSquare2x2inches() {
        // 51 mm (2 inch) -> 600 px at 300 dpi. Square, unlike every other spec.
        val w = IdPhotoMath.mmToPx(51.0, 300)
        val h = IdPhotoMath.mmToPx(51.0, 300)
        assertEquals(w, h)
        assertEquals(602, w)
    }

    @Test fun ukSpecIs35x45SameAsIndia() {
        assertEquals(IdPhotoMath.mmToPx(35.0, 300), IdPhotoMath.mmToPx(35.0, 300))
        assertEquals(413, IdPhotoMath.mmToPx(35.0, 300))
    }

    @Test fun zeroAndNegativeAreClamped() {
        assertEquals(0, IdPhotoMath.mmToPx(0.0, 300))
        assertTrue("negative mm must not produce a negative pixel count",
            IdPhotoMath.mmToPx(-5.0, 300) <= 0)
    }

    // ---- Print sheet layout ----

    @Test fun india35x45FitsOn4x6Sheet() {
        // 4x6 in = 101.6 x 152.4 mm. Portrait 35x45 -> 2 cols x 3 rows = 6.
        val (cols, rows, copies) = IdPhotoMath.fitOnSheet(101.6, 152.4, 35.0, 45.0)
        assertTrue("cols=$cols", cols >= 2)
        assertTrue("rows=$rows", rows >= 3)
        assertEquals(cols * rows, copies)
        assertTrue("expected >= 6 copies, got $copies", copies >= 6)
    }

    @Test fun usSquare51x51FitsOn4x6Sheet() {
        // 101.6 / 51 = 1 col (2 would be 102 > 101.6), 152.4 / 51 = 2 rows.
        val (cols, rows, copies) = IdPhotoMath.fitOnSheet(101.6, 152.4, 51.0, 51.0)
        assertEquals(1, cols); assertEquals(2, rows); assertEquals(2, copies)
    }

    @Test fun rotatingTheSheetGivesMoreCopies() {
        // 45 wide x 35 tall fits 2 cols x 4 rows = 8 on a 4x6 sheet, versus 6
        // unrotated. fitOnSheet must take the better of the two orientations —
        // this is a real paper-and-ink saving for the user.
        val portrait = IdPhotoMath.fitOnSheet(101.6, 152.4, 35.0, 45.0).third
        val rotated  = IdPhotoMath.fitOnSheet(101.6, 152.4, 45.0, 35.0).third
        assertTrue("rotated ($rotated) should beat portrait ($portrait)", rotated >= portrait)
        assertTrue(rotated >= 8)
    }

    @Test fun oversizedSpecYieldsZeroNotACrash() {
        val (_, _, copies) = IdPhotoMath.fitOnSheet(101.6, 152.4, 400.0, 400.0)
        assertEquals(0, copies)
    }

    @Test fun sheetTableIsPopulated() {
        assertTrue("sheet table must not be empty", IdPhotoMath.SHEETS.isNotEmpty())
        assertTrue(IdPhotoMath.SHEETS.containsKey("4x6 in"))
        IdPhotoMath.SHEETS.forEach { (name, dims) ->
            assertTrue("$name width must be positive", dims.first > 0.0)
            assertTrue("$name height must be positive", dims.second > 0.0)
        }
    }

    // ---- Compliance checks ----

    @Test fun headSizeInsideBandPasses() {
        val ok = IdPhotoMath.checkHeadSize(0.65, minPct = 0.50, maxPct = 0.69)
        assertTrue(ok.detail, ok.ok)
    }

    @Test fun headTooLargeFails() {
        val d = IdPhotoMath.checkHeadSize(0.90, 0.50, 0.69)
        assertFalse(d.ok); assertEquals("head", d.key)
    }

    @Test fun headTooSmallFails() {
        assertFalse(IdPhotoMath.checkHeadSize(0.20, 0.50, 0.69).ok)
    }

    @Test fun headOnTheBoundaryIsAccepted() {
        // Inclusive bounds: a photo at exactly the limit is legal and must not
        // be flagged, or honest users get a false warning.
        assertTrue(IdPhotoMath.checkHeadSize(0.50, 0.50, 0.69).ok)
        assertTrue(IdPhotoMath.checkHeadSize(0.69, 0.50, 0.69).ok)
    }

    /**
     * IMPORTANT: checkEyeLine bands are measured FROM THE BOTTOM of the photo
     * (that is how the US State Department states the rule), while the first
     * argument is the eye position FROM THE TOP. The two must not be confused —
     * mixing them up inverts the check and flags every correct photo.
     * US spec: eyes 56-69% up from the bottom == 31-44% down from the top.
     */
    @Test fun eyeLineInsideBandPasses() {
        assertTrue(IdPhotoMath.checkEyeLine(0.38, 0.56, 0.69).ok)   // 62% from bottom
        assertTrue(IdPhotoMath.checkEyeLine(0.44, 0.56, 0.69).ok)   // 56% from bottom (edge)
        assertTrue(IdPhotoMath.checkEyeLine(0.31, 0.56, 0.69).ok)   // 69% from bottom (edge)
        assertFalse(IdPhotoMath.checkEyeLine(0.05, 0.56, 0.69).ok)  // eyes way too high
        assertFalse(IdPhotoMath.checkEyeLine(0.90, 0.56, 0.69).ok)  // eyes way too low
    }

    /** An unconstrained spec (min 0, max 1) must pass everything. */
    @Test fun unconstrainedEyeBandAlwaysPasses() {
        assertTrue(IdPhotoMath.checkEyeLine(0.02, 0.0, 1.0).ok)
        assertTrue(IdPhotoMath.checkEyeLine(0.98, 0.0, 1.0).ok)
    }

    @Test fun whiteBackgroundPassesForWhiteSpec() {
        val white = 0xFFFFFFFF.toInt()
        val d = IdPhotoMath.checkBackground(white, white)
        assertTrue(d.detail, d.ok); assertEquals("bg", d.key)
    }

    @Test fun colouredBackgroundFailsForWhiteSpec() {
        // A red wall behind the subject must be flagged for a white-bg spec.
        val red = 0xFFC0392B.toInt()
        assertFalse(IdPhotoMath.checkBackground(red, 0xFFFFFFFF.toInt()).ok)
    }

    @Test fun nearWhitePassesWithinTolerance() {
        // Camera sensors and JPEG never produce pure 255,255,255. A tight
        // tolerance would flag every honest photo.
        val nearWhite = 0xFFF4F4F4.toInt()
        assertTrue(IdPhotoMath.checkBackground(nearWhite, 0xFFFFFFFF.toInt()).ok)
    }

    @Test fun saturationIsZeroForGreyAndHighForColour() {
        assertEquals(0f, IdPhotoMath.saturation(0xFF808080.toInt()), 0.001f)
        assertEquals(0f, IdPhotoMath.saturation(0xFFFFFFFF.toInt()), 0.001f)
        assertTrue(IdPhotoMath.saturation(0xFFFF0000.toInt()) > 0.9f)
    }

    /**
     * REGRESSION LOCK. Indonesia mandates a RED background and Malaysia a BLUE
     * one. An earlier version applied the "low saturation" gate unconditionally,
     * which produced a false "background is tinted" warning for EVERY Indonesian
     * and Malaysian user on a perfectly compliant photo. The saturation gate
     * must only fire when the SPEC asks for an achromatic background.
     */
    @Test fun redSpecAcceptsRedBackgroundAndRejectsWhite() {
        val red = 0xFFCC0000.toInt()
        val d = IdPhotoMath.checkBackground(red, red)
        assertTrue("exact red-on-red must pass, got: ${d.detail}", d.ok)
        assertFalse("white behind a red-bg spec must fail",
            IdPhotoMath.checkBackground(0xFFFFFFFF.toInt(), red).ok)
        assertFalse("a badly wrong red must fail",
            IdPhotoMath.checkBackground(0xFF661111.toInt(), red).ok)
    }

    @Test fun blueSpecAcceptsBlueBackground() {
        // Malaysia: 35x50 mm on a BLUE background.
        val blue = 0xFF0033A0.toInt()
        val d = IdPhotoMath.checkBackground(blue, blue)
        assertTrue("exact blue-on-blue must pass, got: ${d.detail}", d.ok)
        assertFalse(IdPhotoMath.checkBackground(0xFFFFFFFF.toInt(), blue).ok)
    }

    @Test fun saturationGateStillFiresForAchromaticSpecs() {
        // The gate must not have been removed wholesale — a coloured wall behind
        // a white-background spec is still the single most common user error.
        assertFalse(IdPhotoMath.checkBackground(0xFF3366CC.toInt(), 0xFFFFFFFF.toInt()).ok)
        assertTrue(IdPhotoMath.checkBackground(0xFFE8E8E8.toInt(), 0xFFFFFFFF.toInt()).ok)
    }

    @Test fun lightGreySpecAcceptsGreyButRejectsColour() {
        // UK / Schengen accept white OR light grey.
        val grey = 0xFFDDDDDD.toInt()
        assertTrue(IdPhotoMath.checkBackground(grey, grey).ok)
        assertTrue(IdPhotoMath.checkBackground(0xFFCCCCCC.toInt(), grey).ok)
        assertFalse(IdPhotoMath.checkBackground(0xFFCC0000.toInt(), grey).ok)
    }

    // ---- Crop geometry ----

    @Test fun cropIsInsideSourceBounds() {
        val r = IdPhotoMath.computeCropWithBox(
            srcW = 3000, srcH = 4000,
            faceLeft = 1200, faceTop = 900, faceRight = 1800, faceBottom = 1700,
            specW = 35.0, specH = 45.0)
        assertTrue("expected a crop rect", r != null)
        val arr = r!!
        val x = arr[0]; val y = arr[1]; val w = arr[2]; val h = arr[3]
        assertTrue("x=$x", x >= 0); assertTrue("y=$y", y >= 0)
        assertTrue("w=$w", w > 0);  assertTrue("h=$h", h > 0)
        assertTrue("crop must fit inside the source width", x + w <= 3000)
        assertTrue("crop must fit inside the source height", y + h <= 4000)
    }

    @Test fun cropMatchesTargetAspectRatio() {
        val arr = IdPhotoMath.computeCropWithBox(3000, 4000, 1200, 900, 1800, 1700, 35.0, 45.0)!!
        val w = arr[2]; val h = arr[3]
        // 35:45 = 0.7778. Allow 1px of rounding slack.
        val expected = 35.0 / 45.0
        val actual = w.toDouble() / h
        assertTrue("ratio $actual vs expected $expected",
            kotlin.math.abs(actual - expected) < 0.02)
    }

    @Test fun faceNearTopEdgeStillProducesAValidCrop() {
        // A selfie taken at arm's length puts the face high in the frame. The
        // crop must clamp rather than return negative coordinates.
        val r = IdPhotoMath.computeCropWithBox(2000, 2000, 800, 20, 1200, 500, 35.0, 45.0)
        if (r != null) {
            val x = r[0]; val y = r[1]; val w = r[2]; val h = r[3]
            assertTrue("y=$y must be >= 0", y >= 0)
            assertTrue("x=$x must be >= 0", x >= 0)
            assertTrue(x + w <= 2000); assertTrue(y + h <= 2000)
        }
    }

    @Test fun degenerateFaceBoxReturnsNullNotACrash() {
        val r = IdPhotoMath.computeCropWithBox(1000, 1000, 500, 500, 500, 500, 35.0, 45.0)
        assertTrue("a zero-area face box must not produce a crop", r == null)
    }

    // ---- Size estimation ----

    @Test fun estimateJpegBytesScalesWithArea() {
        val small = IdPhotoMath.estimateJpegBytes(413, 531)
        val large = IdPhotoMath.estimateJpegBytes(1240, 1748)
        assertTrue("larger image must estimate more bytes", large > small)
        assertTrue("estimate must be positive", small > 0)
    }

    @Test fun estimateIsInTheRightOrderOfMagnitude() {
        // A 413x531 JPEG at q90 is realistically 40-150 KB. If this is off by
        // 10x the "will it fit the 200 KB portal limit" hint misleads users.
        val est = IdPhotoMath.estimateJpegBytes(413, 531) / 1024
        assertTrue("est=$est KB", est in 5..400)
    }
}
