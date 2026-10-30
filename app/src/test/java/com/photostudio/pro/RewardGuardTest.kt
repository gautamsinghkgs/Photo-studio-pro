package com.photostudio.pro

import com.photostudio.pro.monetization.ads.RewardGuard
import com.photostudio.pro.monetization.ads.RewardGuard.RewardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * There is NO server-side verification (SSV) at launch, so the reward is granted
 * from a client callback and a determined user can forge it. The defence is
 * therefore to make the VALUE of a forgery small: a hard daily cap, and an
 * allowlist containing nothing worth stealing.
 *
 * At ~150 DAU the abuse loss is roughly Rs 50-100/month. Building SSV costs
 * 2-4 days. That trade is deliberate. Revisit only if the abuse detector in
 * master-plan section 9.3 shows B/A > 3.0.
 */
class RewardGuardTest {

    @Test fun capIsEightPerDay() {
        assertEquals(8, RewardGuard.DEFAULT_DAILY_CAP)
    }

    @Test fun grantsUpToCapThenStops() {
        for (n in 0 until RewardGuard.DEFAULT_DAILY_CAP) {
            assertTrue("grant #$n should be allowed", RewardGuard.canGrant(n))
        }
        assertFalse(RewardGuard.canGrant(RewardGuard.DEFAULT_DAILY_CAP))
        assertFalse(RewardGuard.canGrant(RewardGuard.DEFAULT_DAILY_CAP + 1))
        assertFalse("a forged huge counter must never grant", RewardGuard.canGrant(999_999))
    }

    /**
     * REGRESSION LOCK. A negative counter must fail CLOSED. This is reachable
     * in practice: DataStore corruption, or a user rolling the device clock
     * backwards to reset the daily counters. `grantsToday < cap` alone returns
     * true for -1, which would hand out unlimited rewards.
     */
    @Test fun negativeCounterNeverGrants() {
        assertFalse(RewardGuard.canGrant(-1))
        assertFalse(RewardGuard.canGrant(-8))
        assertFalse(RewardGuard.canGrant(Int.MIN_VALUE))
        assertFalse("a negative cap must not grant either", RewardGuard.canGrant(0, cap = -5))
    }

    @Test fun customCapIsHonoured() {
        assertTrue(RewardGuard.canGrant(2, cap = 3))
        assertFalse(RewardGuard.canGrant(3, cap = 3))
    }

    /**
     * ⭐ The allowlist. A rewarded ad may only ever grant something small and
     * temporary. NEVER Pro, a subscription, a trial extension or lifetime —
     * those are exactly the things worth forging.
     */
    @Test fun onlySmallTemporaryRewardsExist() {
        val names = RewardKind.entries.map { it.name }.toSet()
        assertEquals(
            setOf("REMOVE_WATERMARK_ONCE", "TEMPLATE_24H", "BATCH_PLUS_5",
                  "HD_CREDIT_3", "FULL_RES_EXPORT_ONCE"),
            names,
        )
    }

    @Test fun noDangerousRewardKindCanEverBeAdded() {
        val forbidden = listOf("PRO", "PRO_1_MONTH", "LIFETIME", "LIFETIME_UNLOCK",
                               "TRIAL_EXTEND", "SUBSCRIPTION", "SUBSCRIPTION_FREE", "UNLIMITED")
        RewardKind.entries.forEach { kind ->
            forbidden.forEach { bad ->
                assertFalse(
                    "RewardKind.${kind.name} contains the forbidden token '$bad' — " +
                    "a rewarded ad must never grant this",
                    kind.name.uppercase().contains(bad),
                )
            }
        }
    }

    /** Every reward is either single-use (0) or expires within 24h. */
    @Test fun noRewardLastsLongerThan24Hours() {
        val day = 24L * 60 * 60 * 1000
        RewardKind.entries.forEach {
            assertTrue("${it.name} duration ${it.durationMs}ms is too long", it.durationMs <= day)
        }
    }

    @Test fun singleUseRewardsHaveZeroDuration() {
        assertEquals(0L, RewardKind.REMOVE_WATERMARK_ONCE.durationMs)
        assertEquals(0L, RewardKind.BATCH_PLUS_5.durationMs)
        assertEquals(0L, RewardKind.HD_CREDIT_3.durationMs)
        assertEquals(0L, RewardKind.FULL_RES_EXPORT_ONCE.durationMs)
    }

    @Test fun templateUnlockIsExactly24Hours() {
        assertEquals(86_400_000L, RewardKind.TEMPLATE_24H.durationMs)
    }

    /** Defence in depth: a bogus kind string from a forged callback is refused. */
    @Test fun unknownKindStringsAreRefused() {
        assertNull(RewardGuard.isGrantable(null))
        assertNull(RewardGuard.isGrantable(""))
        assertNull(RewardGuard.isGrantable("PRO_LIFETIME"))
        assertNull(RewardGuard.isGrantable("batch_plus_5"))   // case-sensitive on purpose
        assertEquals(RewardKind.BATCH_PLUS_5, RewardGuard.isGrantable("BATCH_PLUS_5"))
    }

    @Test fun everyKindHasALabelKeyForTheUnlockToast() {
        RewardKind.entries.forEach { assertTrue(it.labelKey.isNotBlank()) }
    }
}
