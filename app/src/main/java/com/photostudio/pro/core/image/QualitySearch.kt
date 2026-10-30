package com.photostudio.pro.core.image

/**
 * Pure JPEG-quality search. Kept in its own file with no Android imports so it
 * can be unit-tested on the JVM without an instrumented device — this is one of
 * only a handful of places where getting the algorithm wrong silently ships
 * broken output (ID photos rejected by a government portal for being 2 KB over).
 *
 * Used by ExportOps.searchQualityForTargetSize, which delegates to
 * [QualitySearch.find].
 */
object QualitySearch {

    /**
     * Find the highest encode quality whose output size lands within
     * `tolerancePct` of `targetBytes`, using a binary search over quality.
     *
     * Returns (byteSize, quality).
     *
     * @param encodeAt quality (20..100) -> encoded byte count for that quality.
     *        Monotonic non-decreasing in quality for JPEG, which is what makes
     *        the binary search valid.
     * @param maxAttempts guard against a pathological encodeAt implementation.
     */
    fun find(
        targetBytes: Int,
        encodeAt: (Int) -> Int,
        tolerancePct: Int = 10,
        minQuality: Int = 20,
        maxQuality: Int = 100,
        maxAttempts: Int = 12,
    ): Pair<Int, Int> {
        require(targetBytes > 0) { "targetBytes must be positive" }
        val tol = targetBytes * tolerancePct / 100

        // Even the maximum quality already fits: take it, quality wins over size.
        val hiSize = encodeAt(maxQuality)
        if (hiSize <= targetBytes + tol) return hiSize to maxQuality

        // Even the minimum quality overshoots: take it, we cannot do better
        // without changing dimensions. The caller must not claim the target was met.
        val loSize = encodeAt(minQuality)
        if (loSize > targetBytes + tol) return loSize to minQuality

        var lo = minQuality; var hi = maxQuality
        var bestQ = minQuality; var bestSize = loSize
        var attempts = 0
        while (lo < hi && attempts < maxAttempts) {
            attempts++
            val mid = (lo + hi + 1) / 2          // bias upward: prefer higher quality
            val size = encodeAt(mid)
            if (size <= targetBytes + tol) {
                bestQ = mid; bestSize = size
                lo = mid
                // Already inside the tolerance band: stop refining. Going lower
                // would only trade visible quality for no real benefit.
                if (size >= targetBytes - tol) return bestSize to bestQ
            } else {
                hi = mid - 1
            }
        }
        return bestSize to bestQ
    }
}
