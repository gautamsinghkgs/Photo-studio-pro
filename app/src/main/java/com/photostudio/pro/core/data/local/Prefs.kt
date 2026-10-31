package com.photostudio.pro.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed preferences. Backs every daily counter that AdPolicy and
 * RewardGuard read, plus install/session timestamps.
 *
 * No cloud sync, no account, no backup of image data. SPEC.md section 11.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "photo_studio_prefs")

class Prefs(private val context: Context) {

    private object K {
        val INSTALL_TS          = longPreferencesKey("install_ts")
        val SESSION_TS          = longPreferencesKey("session_ts")
        val LAST_INTERSTITIAL   = longPreferencesKey("last_interstitial_ts")
        val ONBOARDING_DONE     = booleanPreferencesKey("onboarding_done")
        val EXPORT_FORMAT       = stringPreferencesKey("export_format")
        val RATE_PROMPTS        = intPreferencesKey("rate_prompts")
        // Daily counters, keyed together with the day they belong to.
        val DAY_KEY             = stringPreferencesKey("day_key")
        val INTERSTITIALS_TODAY = intPreferencesKey("interstitials_today")
        val REWARDS_TODAY       = intPreferencesKey("rewards_today")
        val EXPORTS_TODAY       = intPreferencesKey("exports_today")
        val OCR_TODAY           = intPreferencesKey("ocr_today")
        val BATCH_EXTRA_TODAY   = intPreferencesKey("batch_extra_today")
    }

    /** "2026-10-29" in the device's local timezone. */
    private fun today(): String =
        java.time.LocalDate.now().toString()

    private suspend fun rollDayIfNeeded() {
        val current = context.dataStore.data.map { it[K.DAY_KEY] ?: "" }.first()
        if (current != today()) {
            context.dataStore.edit {
                it[K.DAY_KEY] = today()
                it[K.INTERSTITIALS_TODAY] = 0
                it[K.REWARDS_TODAY] = 0
                it[K.EXPORTS_TODAY] = 0
                it[K.OCR_TODAY] = 0
                it[K.BATCH_EXTRA_TODAY] = 0
            }
        }
    }

    suspend fun ensureInstallTimestamp(): Long {
        val existing = context.dataStore.data.map { it[K.INSTALL_TS] }.first()
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        context.dataStore.edit { it[K.INSTALL_TS] = now }
        return now
    }

    suspend fun markSessionStart() {
        context.dataStore.edit { it[K.SESSION_TS] = System.currentTimeMillis() }
    }

    suspend fun daysSinceInstall(): Int {
        val ts = ensureInstallTimestamp()
        val days = (System.currentTimeMillis() - ts) / 86_400_000L
        return days.toInt().coerceAtLeast(0)
    }

    suspend fun msSinceSessionStart(): Long {
        val ts = context.dataStore.data.map { it[K.SESSION_TS] }.first() ?: System.currentTimeMillis()
        return (System.currentTimeMillis() - ts).coerceAtLeast(0)
    }

    suspend fun msSinceLastInterstitial(): Long {
        val ts = context.dataStore.data.map { it[K.LAST_INTERSTITIAL] }.first() ?: return Long.MAX_VALUE
        return (System.currentTimeMillis() - ts).coerceAtLeast(0)
    }

    suspend fun noteInterstitialShown() {
        rollDayIfNeeded()
        context.dataStore.edit {
            it[K.LAST_INTERSTITIAL] = System.currentTimeMillis()
            it[K.INTERSTITIALS_TODAY] = (it[K.INTERSTITIALS_TODAY] ?: 0) + 1
        }
    }

    suspend fun noteRewardGranted() {
        rollDayIfNeeded()
        context.dataStore.edit { it[K.REWARDS_TODAY] = (it[K.REWARDS_TODAY] ?: 0) + 1 }
    }

    suspend fun noteExport() {
        rollDayIfNeeded()
        context.dataStore.edit { it[K.EXPORTS_TODAY] = (it[K.EXPORTS_TODAY] ?: 0) + 1 }
    }

    suspend fun noteOcrScan() {
        rollDayIfNeeded()
        context.dataStore.edit { it[K.OCR_TODAY] = (it[K.OCR_TODAY] ?: 0) + 1 }
    }

    suspend fun counters(): Counters {
        rollDayIfNeeded()
        return context.dataStore.data.map {
            Counters(
                interstitialsToday = it[K.INTERSTITIALS_TODAY] ?: 0,
                rewardedGrantsToday = it[K.REWARDS_TODAY] ?: 0,
                exportsToday = it[K.EXPORTS_TODAY] ?: 0,
                ocrScansToday = it[K.OCR_TODAY] ?: 0,
                batchExtraToday = it[K.BATCH_EXTRA_TODAY] ?: 0,
            )
        }.first()
    }

    suspend fun addBatchExtra(n: Int) {
        rollDayIfNeeded()
        context.dataStore.edit { it[K.BATCH_EXTRA_TODAY] = (it[K.BATCH_EXTRA_TODAY] ?: 0) + n }
    }

    suspend fun onboardingDone(): Boolean =
        context.dataStore.data.map { it[K.ONBOARDING_DONE] ?: false }.first()

    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.ONBOARDING_DONE] = true }


    data class Counters(
        val interstitialsToday: Int,
        val rewardedGrantsToday: Int,
        val exportsToday: Int,
        val ocrScansToday: Int,
        val batchExtraToday: Int,
    )
}
