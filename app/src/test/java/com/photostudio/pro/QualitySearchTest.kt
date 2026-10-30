package com.photostudio.pro

import com.photostudio.pro.core.image.QualitySearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The target-file-size search. This is the feature that makes an ID photo fit a
 * government portal's hard upload limit, so it must land INSIDE the tolerance
 * and must always prefer the highest quality that does.
 */
class QualitySearchTest {

    /** A realistic monotonic JPEG size curve: quality -> bytes. */
    private val realistic: (Int) -> Int = { q ->
        when {
            q >= 100 -> 480_000
            q >= 95  -> 380_000
            q >= 90  -> 300_000
            q >= 85  -> 240_000
            q >= 80  -> 195_000
            q >= 75  -> 160_000
            q >= 70  -> 130_000
            q >= 60  -> 95_000
            q >= 50  -> 70_000
            q >= 40  -> 52_000
            q >= 30  -> 40_000
            else     -> 30_000
        }
    }

    @Test fun hitsATargetWithinTolerance() {
        val (size, q) = QualitySearch.find(200_000, realistic, tolerancePct = 10)
        assertTrue("size $size should be within 180_000..220_000", size in 180_000..220_000)
        assertTrue("quality $q should be high", q >= 75)
    }

    @Test fun alwaysPrefersTheHighestQualityThatFits() {
        val (_, q) = QualitySearch.find(200_000, realistic, tolerancePct = 10)
        // q=80 gives 195_000 which fits; q=85 gives 240_000 which does not.
        assertEquals(80, q)
    }

    @Test fun maxQualityWhenItAlreadyFits() {
        // Target is generous: no need to drop quality at all.
        val (size, q) = QualitySearch.find(600_000, realistic, tolerancePct = 10)
        assertEquals(100, q)
        assertEquals(480_000, size)
    }

    @Test fun minQualityWhenEvenThatOvershoots() {
        // Target is impossibly small for this image: return the floor rather
        // than looping forever or returning a nonsense quality.
        val (_, q) = QualitySearch.find(1_000, realistic, tolerancePct = 10)
        assertEquals(20, q)
    }

    @Test fun tinyTargetForGovernmentPortal() {
        // Indian passport portal: 200 KB ceiling. This is the real use case.
        val (size, _) = QualitySearch.find(200 * 1024, realistic, tolerancePct = 8)
        assertTrue("size $size must respect the ceiling within tolerance",
            size <= 200 * 1024 * 108 / 100)
    }

    @Test fun terminatesOnAPathologicalEncoder() {
        // A non-monotonic / hostile encodeAt must not hang the export thread.
        var calls = 0
        val chaotic: (Int) -> Int = { q -> calls++; if (q % 3 == 0) 999_999 else 10 }
        val (_, q) = QualitySearch.find(50_000, chaotic, tolerancePct = 10)
        assertTrue("quality $q out of range", q in 20..100)
        assertTrue("binary search must stay logarithmic, made $calls calls", calls <= 14)
    }

    @Test fun respectsCustomQualityRange() {
        val (_, q) = QualitySearch.find(10_000, realistic, tolerancePct = 10,
                                        minQuality = 40, maxQuality = 60)
        assertTrue("quality $q must stay inside 40..60", q in 40..60)
    }

    @Test fun constantEncoderDoesNotLoopForever() {
        val (_, q) = QualitySearch.find(100_000, { 100_000 }, tolerancePct = 10)
        assertEquals(100, q)   // already within tolerance at max quality
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveTarget() {
        QualitySearch.find(0, realistic)
    }
}
