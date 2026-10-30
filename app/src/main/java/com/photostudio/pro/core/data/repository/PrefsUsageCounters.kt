package com.photostudio.pro.core.data.repository

import com.photostudio.pro.core.data.local.Prefs
import com.photostudio.pro.monetization.ads.UsageCounters

/**
 * Bridges the persisted Prefs counters into the UsageCounters interface that
 * AdPolicy reads synchronously. A cached snapshot is refreshed at session start
 * and after every mutation, so AdPolicy never has to suspend.
 */
class PrefsUsageCounters(private val prefs: Prefs) : UsageCounters {

    private var cached = Prefs.Counters(0, 0, 0, 0, 0)

    override val interstitialsToday get() = cached.interstitialsToday
    override val rewardedGrantsToday get() = cached.rewardedGrantsToday
    override val exportsToday get() = cached.exportsToday
    override val ocrScansToday get() = cached.ocrScansToday

    val batchExtraToday get() = cached.batchExtraToday

    suspend fun refresh() { cached = prefs.counters() }
}
