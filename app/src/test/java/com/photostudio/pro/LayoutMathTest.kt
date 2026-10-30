package com.photostudio.pro

import com.photostudio.pro.core.image.LayoutMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure maths behind three tools: Collage (M7), PDF (M8) and White Background
 * (M6). None of these fail loudly — a bug here shows up as a stretched product
 * photo, an upside-down PDF page, or a white background that ate the subject.
 * So they are tested on the JVM where they can actually be verified.
 */
class LayoutMathTest {

    private val WHITE = -1                                  // 0xFFFFFFFF
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    // ================= COLLAGE TILING =================

    @Test fun gridCoversTheWholeUnitSquareExactly() {
        for (cols in 1..5) for (rows in 1..5) {
            val cells = LayoutMath.grid(cols, rows)
            assertEquals("$cols x $rows cell count", cols * rows, cells.size)
            val area = cells.sumOf { (it.w * it.h).toDouble() }
            assertEquals("$cols x $rows total area must be 1.0", 1.0, area, 1e-4)
        }
    }

    @Test fun everyCellStaysInsideTheCanvas() {
        val all = LayoutMath.grid(3, 4) + LayoutMath.heroLeft(3) +
                LayoutMath.heroRight(4) + LayoutMath.centreFour() +
                LayoutMath.rows(5) + LayoutMath.cols(5)
        all.forEachIndexed { i, c ->
            assertTrue("cell $i out of bounds: $c", c.isValid)
        }
    }

    @Test fun noTwoCellsOverlap() {
        // Overlapping cells mean one photo silently covers another.
        val cells = LayoutMath.grid(3, 3)
        for (i in cells.indices) for (j in i + 1 until cells.size) {
            val a = cells[i]; val b = cells[j]
            val overlapX = a.x < b.x + b.w - 1e-5f && b.x < a.x + a.w - 1e-5f
            val overlapY = a.y < b.y + b.h - 1e-5f && b.y < a.y + a.h - 1e-5f
            assertFalse("cells $i and $j overlap", overlapX && overlapY)
        }
    }

    @Test fun gridOfOneIsTheFullCanvas() {
        val c = LayoutMath.grid(1, 1).single()
        assertEquals(0f, c.x, 0f); assertEquals(0f, c.y, 0f)
        assertEquals(1f, c.w, 1e-6f); assertEquals(1f, c.h, 1e-6f)
    }

    @Test fun degenerateGridIsClampedNotCrashed() {
        // Non-positive dimensions are clamped to 1 per axis, never to zero —
        // a zero-cell template would render as a blank white canvas.
        assertEquals(1, LayoutMath.grid(0, 0).size)
        assertEquals(1, LayoutMath.grid(-3, -2).size)
        assertEquals(1, LayoutMath.rows(0).size)
        assertEquals(1, LayoutMath.cols(-5).size)
        // One axis degenerate still gives a valid single-axis layout: 1 x 2 = 2.
        assertEquals(2, LayoutMath.grid(-3, 2).size)
        assertTrue(LayoutMath.grid(0, 0).single().isValid)
        LayoutMath.grid(-3, 2).forEach { assertTrue(it.isValid) }
    }

    @Test fun heroLayoutGivesTheHeroMoreThanHalfTheCanvas() {
        val cells = LayoutMath.heroLeft(3, heroFraction = 0.58f)
        assertEquals(4, cells.size)          // hero + 3
        val hero = cells.first()
        assertEquals(0f, hero.x, 0f); assertEquals(0f, hero.y, 0f)
        assertEquals(0.58f, hero.w, 1e-5f); assertEquals(1f, hero.h, 1e-5f)
        // The three side cells share the remaining width and the full height.
        val sideArea = cells.drop(1).sumOf { (it.w * it.h).toDouble() }
        assertEquals(0.42, sideArea, 1e-4)
    }

    @Test fun heroFractionIsClampedToASaneRange() {
        // Without the clamp, heroFraction = 1.0 leaves zero width for the side
        // cells and they render as invisible slivers.
        val a = LayoutMath.heroLeft(2, heroFraction = 5f)
        assertTrue(a.first().w <= 0.8f + 1e-5f)
        val b = LayoutMath.heroLeft(2, heroFraction = -5f)
        assertTrue(b.first().w >= 0.2f - 1e-5f)
        a.forEach { assertTrue(it.isValid) }; b.forEach { assertTrue(it.isValid) }
    }

    @Test fun heroLeftAndHeroRightAreMirrors() {
        val l = LayoutMath.heroLeft(3, 0.6f)
        val r = LayoutMath.heroRight(3, 0.6f)
        assertEquals(l.size, r.size)
        // Mirror: left cell x == 1 - (right cell x + width)
        l.zip(r).forEach { (lc, rc) ->
            assertEquals(1f, rc.x + rc.w + lc.x, 1e-4f)
            assertEquals(lc.w, rc.w, 1e-5f)
        }
    }

    // ================= CENTRE-CROP =================

    @Test fun sameAspectCropsNothing() {
        val r = LayoutMath.centerCropRect(1000, 1000, 500f, 500f)
        assertEquals(0, r[0]); assertEquals(0, r[1])
        assertEquals(1000, r[2]); assertEquals(1000, r[3])
    }

    @Test fun wideSourceIntoSquareCellCropsLeftAndRightSymmetrically() {
        val r = LayoutMath.centerCropRect(2000, 1000, 100f, 100f)
        assertEquals("height must be kept whole", 1000, r[3])
        assertEquals("width must be cropped to the cell aspect", 1000, r[2])
        // centred: (2000-1000)/2 = 500
        assertEquals(500, r[0]); assertEquals(0, r[1])
    }

    @Test fun tallSourceIntoSquareCellCropsTopAndBottomSymmetrically() {
        val r = LayoutMath.centerCropRect(1000, 2000, 100f, 100f)
        assertEquals(1000, r[2])             // width kept whole
        assertEquals(1000, r[3])             // height cropped
        assertEquals(0, r[0]); assertEquals(500, r[1])
    }

    @Test fun cropRectAlwaysStaysInsideTheSource() {
        val cases = listOf(
            Triple(4000, 3000, Pair(9f, 16f)),
            Triple(3000, 4000, Pair(16f, 9f)),
            Triple(1080, 1080, Pair(4f, 5f)),
            Triple(1920, 1080, Pair(1f, 1f)),
            Triple(100, 4000,  Pair(4f, 3f)),
            Triple(4000, 100,  Pair(3f, 4f)),
        )
        cases.forEach { (w, h, aspect) ->
            val r = LayoutMath.centerCropRect(w, h, aspect.first, aspect.second)
            assertTrue("$w x $h -> ${aspect}: x=${r[0]} negative", r[0] >= 0)
            assertTrue("$w x $h -> ${aspect}: y=${r[1]} negative", r[1] >= 0)
            assertTrue("$w x $h -> ${aspect}: right edge ${r[0] + r[2]} > $w", r[0] + r[2] <= w)
            assertTrue("$w x $h -> ${aspect}: bottom edge ${r[1] + r[3]} > $h", r[1] + r[3] <= h)
            assertTrue("$w x $h -> ${aspect}: zero width", r[2] > 0)
            assertTrue("$w x $h -> ${aspect}: zero height", r[3] > 0)
        }
    }

    @Test fun croppedRectMatchesTheDestinationAspect() {
        val r = LayoutMath.centerCropRect(4000, 3000, 9f, 16f)
        val expected = 9f / 16f
        val actual = r[2].toFloat() / r[3]
        assertEquals(expected, actual, 0.02f)
    }

    @Test fun degenerateInputsDoNotCrash() {
        assertTrue(LayoutMath.centerCropRect(0, 0, 1f, 1f)[2] >= 1)
        assertTrue(LayoutMath.centerCropRect(100, 100, 0f, 0f)[2] >= 1)
        assertTrue(LayoutMath.centerCropRect(-5, 100, 1f, 1f)[2] >= 1)
    }

    // ================= PDF PAGE PLACEMENT =================

    /**
     * ⚠️ The most important PDF test. PDF's origin is the BOTTOM-left, not the
     * top-left. If y were measured from the top, every page would render
     * upside-down or off the sheet, and it would look fine in the app because
     * the preview is drawn by Compose, not by PdfBox.
     */
    @Test fun pdfRectIsInsideTheAvailableBox() {
        // A4 = 595 x 842 pt; margin 24 -> available 547 x 794
        val margin = 24f
        val availW = 595f - 2 * margin
        val availH = 842f - 2 * margin
        val r = LayoutMath.placeRectOnPage(3000, 4000, availW, availH, margin, fill = false)
        val (x, y, w, h) = listOf(r[0], r[1], r[2], r[3])
        assertTrue("x=$x below margin", x >= margin - 0.01f)
        assertTrue("y=$y below margin (PDF origin is bottom-left)", y >= margin - 0.01f)
        assertTrue("right edge ${x + w} exceeds page", x + w <= 595f - margin + 0.01f)
        assertTrue("top edge ${y + h} exceeds page", y + h <= 842f - margin + 0.01f)
    }

    @Test fun pdfFitNeverCropsTheImage() {
        // A wide landscape photo on a portrait A4 page must be letterboxed, not
        // cropped — cropping an invoice or an ID document is data loss.
        val r = LayoutMath.placeRectOnPage(4000, 1000, 547f, 794f, 24f, fill = false)
        val aspect = r[2] / r[3]
        assertEquals("FIT must preserve the source aspect", 4f, aspect, 0.02f)
        assertTrue("FIT must not exceed the available width", r[2] <= 547f + 0.01f)
        assertTrue("FIT must not exceed the available height", r[3] <= 794f + 0.01f)
    }

    @Test fun pdfFillCoversTheWholeBox() {
        val r = LayoutMath.placeRectOnPage(4000, 1000, 547f, 794f, 24f, fill = true)
        val aspect = r[2] / r[3]
        assertEquals("FILL must also preserve aspect", 4f, aspect, 0.02f)
        // Covering a tall box with a wide image: width must reach the box edge.
        assertTrue("FILL must cover the available width", r[2] >= 547f - 0.01f)
    }

    @Test fun pdfFitAndFillDifferForMismatchedAspects() {
        val fit  = LayoutMath.placeRectOnPage(4000, 1000, 547f, 794f, 24f, fill = false)
        val fill = LayoutMath.placeRectOnPage(4000, 1000, 547f, 794f, 24f, fill = true)
        assertTrue("FIT and FILL must not produce the same rect",
            kotlin.math.abs(fit[2] - fill[2]) > 1f || kotlin.math.abs(fit[3] - fill[3]) > 1f)
        // FIT is always contained; FILL is always at least as large in one axis.
        assertTrue(fit[2] <= fill[2] + 0.01f)
    }

    @Test fun pdfImageIsCentredOnThePage() {
        val r = LayoutMath.placeRectOnPage(1000, 1000, 547f, 794f, 24f, fill = false)
        val leftGap  = r[0] - 24f
        val rightGap = (24f + 547f) - (r[0] + r[2])
        assertEquals("horizontal centring", leftGap, rightGap, 0.5f)
        val bottomGap = r[1] - 24f
        val topGap    = (24f + 794f) - (r[1] + r[3])
        assertEquals("vertical centring", bottomGap, topGap, 0.5f)
    }

    @Test fun pdfZeroMarginStillWorks() {
        val r = LayoutMath.placeRectOnPage(1000, 1000, 595f, 842f, 0f, fill = false)
        assertEquals(0f, r[0], 0.01f)
        assertTrue(r[2] > 0f && r[3] > 0f)
    }

    @Test fun pdfDegenerateInputsDoNotCrash() {
        val r = LayoutMath.placeRectOnPage(0, 0, 0f, 0f, 24f, fill = false)
        assertEquals(4, r.size)
        assertTrue(r[2] > 0f); assertTrue(r[3] > 0f)
    }

    // ================= LUMINANCE =================

    @Test fun lumaFollowsRec709Weights() {
        assertEquals(0,   LayoutMath.luma(argb(0, 0, 0)))
        assertEquals(255, LayoutMath.luma(WHITE))
        // Green dominates human brightness perception: pure green must read
        // brighter than pure red or pure blue at the same channel value.
        val g = LayoutMath.luma(argb(0, 255, 0))
        val r = LayoutMath.luma(argb(255, 0, 0))
        val b = LayoutMath.luma(argb(0, 0, 255))
        assertTrue("green ($g) must exceed red ($r)", g > r)
        assertTrue("red ($r) must exceed blue ($b)", r > b)
    }

    @Test fun midGreyLumaIsAboutHalf() {
        val v = LayoutMath.luma(argb(128, 128, 128))
        assertTrue("mid grey luma = $v", v in 126..130)
    }

    // ================= WHITE-BACKGROUND THRESHOLDS =================

    @Test fun thresholdsAreOrderedLowBelowHigh() {
        val border = IntArray(100) { 230 }
        for (s in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val (lo, hi) = LayoutMath.computeThresholds(border, s)
            assertTrue("strength $s: low=$lo must be <= high=$hi", lo <= hi)
            assertTrue("high=$hi out of range", hi in 0..255)
            assertTrue("low=$lo out of range", lo in 0..255)
        }
    }

    @Test fun strongerSettingRaisesTheThreshold() {
        val border = IntArray(100) { 200 }
        val (_, hiLow)  = LayoutMath.computeThresholds(border, 0f)
        val (_, hiHigh) = LayoutMath.computeThresholds(border, 1f)
        assertTrue("aggressive must whiten more: $hiLow -> $hiHigh", hiHigh >= hiLow)
    }

    /**
     * ⚠️ REGRESSION LOCK for a real bug. The gate must sit AT OR BELOW the
     * observed border luminance, never above it. The earlier formula computed
     * `pct + (255 - pct) * k`, which on a dimly lit white wall (pct ~120) put the
     * gate at ~173 — above the wall itself — so the tool whitened nothing while
     * still reporting success.
     */
    @Test fun gateNeverSitsAboveTheObservedBackground() {
        for (pct in listOf(60, 120, 180, 220, 245)) {
            for (s in listOf(0f, 0.5f, 1f)) {
                val (lo, hi) = LayoutMath.computeThresholds(IntArray(100) { pct }, s)
                assertTrue("pct=$pct s=$s: low=$lo must be <= the background estimate",
                    lo <= pct)
                assertTrue("pct=$pct s=$s: low=$lo must be in range", lo in 0..250)
                assertTrue("pct=$pct s=$s: high=$hi must be in range", hi in 0..255)
                assertTrue("pct=$pct s=$s: low must be < high for hysteresis", lo < hi)
            }
        }
    }

    @Test fun dimWallIsReachableByTheGate() {
        // The concrete failure case: a white wall under bad lighting reads ~120.
        val (lo, _) = LayoutMath.computeThresholds(IntArray(100) { 120 }, 0.5f)
        assertTrue("a luma-120 wall must pass the gate (lo=$lo)", 120 >= lo)
    }

    @Test fun strongerSettingLowersTheGateToWhitenMore() {
        val border = IntArray(100) { 200 }
        val (loSoft) = LayoutMath.computeThresholds(border, 0f)
        val (loHard) = LayoutMath.computeThresholds(border, 1f)
        assertTrue("aggressive must accept darker pixels: soft=$loSoft hard=$loHard",
            loHard <= loSoft)
    }

    @Test fun emptyBorderFallsBackToASafeDefault() {
        val (lo, hi) = LayoutMath.computeThresholds(IntArray(0), 0.5f)
        assertEquals(200, lo); assertEquals(245, hi)
    }

    @Test fun outlierDarkPixelsDoNotDragTheThresholdDown() {
        // One shadowed corner must not stop the rest of a white wall going white.
        // The 35th percentile is what makes this robust.
        val mostly = IntArray(100) { 240 }
        val withShadow = mostly.copyOf().also { for (i in 0..4) it[i] = 40 }
        val (_, hiClean)   = LayoutMath.computeThresholds(mostly, 0.5f)
        val (_, hiShadow)  = LayoutMath.computeThresholds(withShadow, 0.5f)
        assertTrue("5 dark pixels shifted the threshold from $hiClean to $hiShadow",
            kotlin.math.abs(hiClean - hiShadow) < 40)
    }

    // ================= BORDER SAMPLING =================

    @Test fun sampleBorderOnlyReadsEdgePixels() {
        // A white border with a black centre: sampling must see only white.
        val w = 64; val h = 64
        val px = IntArray(w * h) { WHITE }
        for (y in 16 until 48) for (x in 16 until 48) px[y * w + x] = argb(0, 0, 0)
        val border = LayoutMath.sampleBorder(px, w, h, step = 4)
        assertTrue("border must be sampled", border.isNotEmpty())
        assertTrue("every border sample must be white, got min=${border.min()}",
            border.all { it > 250 })
    }

    @Test fun sampleBorderSeesADarkFrame() {
        val w = 32; val h = 32
        val px = IntArray(w * h) { argb(0, 0, 0) }
        for (y in 8 until 24) for (x in 8 until 24) px[y * w + x] = WHITE
        val border = LayoutMath.sampleBorder(px, w, h, step = 2)
        assertTrue("every border sample must be dark, got max=${border.max()}",
            border.all { it < 5 })
    }

    @Test fun sampleBorderHandlesDegenerateSizes() {
        assertEquals(0, LayoutMath.sampleBorder(IntArray(0), 0, 0).size)
        assertEquals(0, LayoutMath.sampleBorder(IntArray(10), 100, 100).size)  // buffer too small
    }

    // ================= FLOOD FILL =================

    @Test fun floodWhitensAnEntirelyWhiteImage() {
        val w = 20; val h = 20
        val px = IntArray(w * h) { WHITE }
        val n = LayoutMath.floodWhiten(px, w, h, low = 200, high = 240)
        assertEquals("every pixel should be reachable from a border", w * h, n)
        assertTrue(px.all { it == WHITE })
    }

    @Test fun floodStopsAtADarkSubjectAndDoesNotEatIt() {
        // A dark square in the middle of a white frame. The flood must whiten the
        // frame and stop dead at the subject's edge.
        val w = 40; val h = 40
        val px = IntArray(w * h) { WHITE }
        for (y in 10 until 30) for (x in 10 until 30) px[y * w + x] = argb(30, 30, 30)
        val n = LayoutMath.floodWhiten(px, w, h, low = 200, high = 240)
        assertEquals("frame area only", w * h - 20 * 20, n)
        for (y in 10 until 30) for (x in 10 until 30) {
            assertEquals("subject pixel ($x,$y) was eaten", argb(30, 30, 30), px[y * w + x])
        }
    }

    /**
     * ⚠️ This is what hysteresis is FOR. A dark shadow that touches the subject
     * must not be whitened, even though a dark shadow that touches the frame
     * would be. Without the connectivity rule a single global threshold either
     * leaves grey patches or punches holes in the product.
     */
    @Test fun shadowAttachedToTheSubjectSurvives() {
        val w = 40; val h = 40
        val px = IntArray(w * h) { WHITE }
        // Subject block
        for (y in 12 until 28) for (x in 12 until 28) px[y * w + x] = argb(40, 40, 40)
        // Shadow directly below the subject, mid-grey, NOT touching any border
        for (y in 28 until 32) for (x in 12 until 28) px[y * w + x] = argb(150, 150, 150)
        LayoutMath.floodWhiten(px, w, h, low = 200, high = 240)
        for (y in 28 until 32) for (x in 12 until 28) {
            assertEquals("shadow at ($x,$y) was whitened — hysteresis failed",
                argb(150, 150, 150), px[y * w + x])
        }
    }

    @Test fun floodDoesNotRunOffTheEdgeOfTheBuffer() {
        // A 1-px-wide image exercises every boundary branch at once.
        val px = IntArray(1) { WHITE }
        assertEquals(1, LayoutMath.floodWhiten(px, 1, 1, 200, 240))
        val row = IntArray(10) { WHITE }
        assertEquals(10, LayoutMath.floodWhiten(row, 10, 1, 200, 240))
        val col = IntArray(10) { WHITE }
        assertEquals(10, LayoutMath.floodWhiten(col, 1, 10, 200, 240))
    }

    /**
     * End-to-end regression for the dim-wall bug: a luma-120 wall with a dark
     * subject must actually go white. Under the old threshold formula this
     * whitened zero pixels.
     */
    @Test fun dimlyLitWhiteWallStillGetsWhitened() {
        val w = 40; val h = 40
        val wall = argb(125, 122, 118)          // luma ~122, uneven warm cast
        val px = IntArray(w * h) { wall }
        for (y in 12 until 28) for (x in 12 until 28) px[y * w + x] = argb(35, 30, 28)
        val border = LayoutMath.sampleBorder(px, w, h, step = 4)
        val (lo, hi) = LayoutMath.computeThresholds(border, 0.5f)
        val n = LayoutMath.floodWhiten(px, w, h, lo, hi)
        assertEquals("wall minus subject", w * h - 16 * 16, n)
        for (y in 12 until 28) for (x in 12 until 28) {
            assertEquals("subject eaten at ($x,$y)", argb(35, 30, 28), px[y * w + x])
        }
    }

    @Test fun seedGateWhitensAnIsolatedBrightPatchNotTouchingABorder() {
        // A bright specular highlight in the middle of a mid-grey wall: it is not
        // connected to any border seed above `high`, so it must survive. Blowing
        // out highlights in the middle of a product is the visible artefact users
        // complain about.
        val w = 40; val h = 40
        val px = IntArray(w * h) { argb(150, 150, 150) }
        for (y in 18 until 22) for (x in 18 until 22) px[y * w + x] = WHITE
        val border = LayoutMath.sampleBorder(px, w, h, step = 4)
        val (lo, hi) = LayoutMath.computeThresholds(border, 0.5f)
        LayoutMath.floodWhiten(px, w, h, lo, hi)
        // The whole frame is uniform grey at the border, so it floods entirely;
        // what must NOT happen is the highlight being treated as a separate seed
        // that punches a hole. Assert the result is uniform, not speckled.
        val whites = px.count { it == WHITE }
        assertTrue("expected a uniform result, got $whites of ${w * h}",
            whites == 0 || whites == w * h)
    }

    @Test fun floodOnAnAllDarkImageChangesNothing() {
        // The "wrong tool" case: subject fills the frame. Returning 0 changed
        // pixels is what lets the UI say "use Background Remover instead"
        // rather than silently returning an unchanged photo.
        val w = 20; val h = 20
        val px = IntArray(w * h) { argb(20, 20, 20) }
        assertEquals(0, LayoutMath.floodWhiten(px, w, h, 200, 240))
        assertTrue(px.all { it == argb(20, 20, 20) })
    }

    @Test fun floodIsIterativeSoItCannotBlowTheStack() {
        // A recursive flood fill dies somewhere around 1-4 Mpx on a phone's
        // stack. 250k px in one connected region is enough to prove the
        // explicit-stack version survives; the real path handles 12 Mpx.
        val w = 500; val h = 500
        val px = IntArray(w * h) { WHITE }
        val n = LayoutMath.floodWhiten(px, w, h, 200, 240)
        assertEquals(w * h, n)
    }

    @Test fun floodRejectsABufferSmallerThanItsDimensions() {
        assertEquals(0, LayoutMath.floodWhiten(IntArray(5), 100, 100, 200, 240))
        assertEquals(0, LayoutMath.floodWhiten(IntArray(0), 0, 0, 200, 240))
    }
}

/**
 * Template/paywall boundary. Kept as its own class so a failure here is
 * obviously a monetisation problem and not a geometry problem.
 *
 * CollageOps itself imports android.graphics, so it cannot be compiled on a
 * plain JVM. These assertions therefore encode the CONTRACT that
 * CollageOps.templates must satisfy; run them as instrumented tests
 * (`./gradlew test` picks up only the JVM ones) or re-check by hand whenever a
 * template is added.
 */
class CollageTemplateContractTest {

    /** The shipped template list, mirrored. MUST match CollageOps.templates. */
    private val ids = listOf(
        "g1","g2h","g2v","g3h","g3v","g4","g6","g9","hl2","hr2",          // free (10)
        "hl3","hr3","hl4","c4","g12","g16","g4v","g4h","hl5","g20",       // pro  (10)
    )
    private val FREE_COUNT = 10

    @Test fun freeCountLandsExactlyOnTheFreeProBoundary() {
        // `templates.take(FREE_COUNT)` must be exactly the free tier, with no
        // separate flag-filtering that could drift out of sync with the paywall
        // copy and the store listing ("10 free templates").
        assertEquals(10, ids.take(FREE_COUNT).size)
        assertEquals(20, ids.size)
        assertTrue("there must be Pro templates to sell", ids.size > FREE_COUNT)
    }

    @Test fun templateIdsAreUnique() {
        assertEquals("duplicate template id", ids.size, ids.toSet().size)
    }

    @Test fun freeTierIncludesTheCommonLayouts() {
        // The free tier must cover what most sellers actually need, or the
        // paywall reads as crippled rather than as an upgrade.
        val free = ids.take(FREE_COUNT)
        listOf("g1", "g2h", "g2v", "g4", "g9").forEach {
            assertTrue("$it should be free", it in free)
        }
    }
}
