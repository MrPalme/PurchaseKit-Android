// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import android.content.Context
import android.content.SharedPreferences
import com.markusmock.purchasekit.model.EntitlementState
import com.markusmock.purchasekit.support.PurchaseKitLogger
import com.markusmock.purchasekit.support.w
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * On-disk cache of derived entitlement state, encoded with [JSONObject].
 *
 * The cache is **never** authoritative. Its sole purpose is to paint a
 * plausible paywall during cold start before `BillingClient.queryPurchasesAsync`
 * returns. The manager overwrites every entry from Play's truth on connect /
 * restore / foreground refresh.
 *
 * The codec persists only the discriminator and a couple of small numeric /
 * string fields per option (see ADR-0002). It deliberately does **not**
 * persist purchase tokens, signatures, or any other PII.
 *
 * Threading: all reads and writes happen on [io] (`Dispatchers.IO` by default).
 *
 * @since 0.1.0
 */
internal class PersistenceService(
    context: Context,
    private val prefsName: String,
    private val logger: PurchaseKitLogger = PurchaseKitLogger.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** Reads the full snapshot. Missing / unparseable entries become [EntitlementState.Inactive]. */
    suspend fun snapshot(): Map<String, EntitlementState> = withContext(io) {
        prefs.all.mapNotNull { (key, value) ->
            if (value !is String) return@mapNotNull null
            try {
                key to decode(JSONObject(value))
            } catch (e: JSONException) {
                logger.w(TAG, "Dropping corrupt cache entry for $key", e)
                null
            }
        }.toMap()
    }

    /** Writes a single entry. Atomic against itself; not transactional across multiple writes. */
    suspend fun put(productId: String, state: EntitlementState): Unit = withContext(io) {
        prefs.edit()
            .putString(productId, encode(state).toString())
            .apply()
    }

    /** Replaces the entire cache with [snapshot]. */
    suspend fun replaceAll(snapshot: Map<String, EntitlementState>): Unit = withContext(io) {
        prefs.edit().clear().apply {
            snapshot.forEach { (key, state) ->
                putString(key, encode(state).toString())
            }
        }.apply()
    }

    /** Removes all cached entries. */
    suspend fun clear(): Unit = withContext(io) {
        prefs.edit().clear().apply()
    }

    internal companion object {
        private const val TAG = "PurchaseKit.Persist"
        private const val VERSION = 1
        private const val FIELD_VERSION = "v"
        private const val FIELD_KIND = "kind"
        private const val FIELD_EXPIRES = "expiresAt"
        private const val FIELD_TX = "tx"
        private const val FIELD_REVOKED = "revokedAt"

        private const val KIND_INACTIVE = "inactive"
        private const val KIND_NON_CONSUMABLE = "non_consumable"
        private const val KIND_SUBSCRIPTION_ACTIVE = "subscription_active"
        private const val KIND_SUBSCRIPTION_EXPIRED = "subscription_expired"
        private const val KIND_REVOKED = "revoked"

        internal fun encode(state: EntitlementState): JSONObject {
            val obj = JSONObject().put(FIELD_VERSION, VERSION)
            when (state) {
                EntitlementState.Inactive -> obj.put(FIELD_KIND, KIND_INACTIVE)
                is EntitlementState.NonConsumable -> obj
                    .put(FIELD_KIND, KIND_NON_CONSUMABLE)
                    .put(FIELD_TX, state.transactionId)
                is EntitlementState.SubscriptionActive -> obj
                    .put(FIELD_KIND, KIND_SUBSCRIPTION_ACTIVE)
                    .put(FIELD_EXPIRES, state.expirationEpochMillis)
                    .put(FIELD_TX, state.transactionId)
                is EntitlementState.SubscriptionExpired -> obj
                    .put(FIELD_KIND, KIND_SUBSCRIPTION_EXPIRED)
                    .put(FIELD_EXPIRES, state.expirationEpochMillis)
                is EntitlementState.Revoked -> obj
                    .put(FIELD_KIND, KIND_REVOKED)
                    .put(FIELD_REVOKED, state.revocationEpochMillis)
            }
            return obj
        }

        internal fun decode(obj: JSONObject): EntitlementState {
            val version = obj.optInt(FIELD_VERSION, 0)
            if (version != VERSION) return EntitlementState.Inactive
            return when (obj.optString(FIELD_KIND)) {
                KIND_NON_CONSUMABLE -> EntitlementState.NonConsumable(
                    transactionId = obj.optString(FIELD_TX),
                )
                KIND_SUBSCRIPTION_ACTIVE -> EntitlementState.SubscriptionActive(
                    expirationEpochMillis = obj.optLong(FIELD_EXPIRES),
                    transactionId = obj.optString(FIELD_TX),
                )
                KIND_SUBSCRIPTION_EXPIRED -> EntitlementState.SubscriptionExpired(
                    expirationEpochMillis = obj.optLong(FIELD_EXPIRES),
                )
                KIND_REVOKED -> EntitlementState.Revoked(
                    revocationEpochMillis = obj.optLong(FIELD_REVOKED),
                )
                else -> EntitlementState.Inactive
            }
        }
    }
}
