package com.photostudio.pro.monetization.ads

import android.content.Context
import java.util.UUID

/**
 * A random local UUID, generated once and stored in DataStore-equivalent prefs.
 *
 * This is NOT an account and NOT personal data. It exists only so that a future
 * Phase 2 SSV endpoint can key redemptions without ever receiving anything
 * identifying. No email, no phone number, no device fingerprint, no advertising
 * id. master-plan section 5 / SPEC.md section 11.
 */
object LocalIdentity {
    private const val PREFS = "local_identity"
    private const val KEY = "anon_id"

    fun anonymousId(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(KEY, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString(KEY, id).apply()
        return id
    }
}
