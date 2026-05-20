// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional connectivity-awareness seam.
 *
 * If provided via `PurchaseKitConfig.networkService`, the library exposes
 * `canAttemptNetworkOperations` so paywall UIs can disable buttons before
 * calling Play Billing — Play returns sensible errors when offline, but a
 * fast-fail UX is friendlier.
 *
 * The library does **not** require a `NetworkService`. When `null`, the
 * manager assumes connectivity is fine and always reports `true`.
 *
 * Threading: implementations must publish to [canAttemptNetworkOperations] on
 * any thread. The manager collects the flow on `Dispatchers.Main`.
 *
 * @since 0.1.0
 */
public interface NetworkService {

    /**
     * Stream of "is the network up enough to call Play right now" booleans.
     *
     * The library does not interpret intermediate states (metered, captive
     * portal, restricted background). Implementations should emit `true` when
     * Play is reachable in practice and `false` otherwise.
     */
    public val canAttemptNetworkOperations: StateFlow<Boolean>

    /** Called by the manager when the host enters the foreground. Optional. */
    @MainThread
    public fun onForeground() {}

    /** Called by the manager during shutdown. Implementations should release receivers. */
    @MainThread
    public fun shutdown() {}
}

/**
 * Default [NetworkService] backed by [ConnectivityManager]'s default network
 * callback. Reports `true` while a default network with the
 * [NetworkCapabilities.NET_CAPABILITY_INTERNET] capability is available.
 *
 * Threading: the underlying [ConnectivityManager.NetworkCallback] callbacks
 * arrive on a `ConnectivityManager`-owned thread. The implementation forwards
 * via a [MutableStateFlow] which is safe to publish to from any thread.
 *
 * @since 0.1.0
 */
public class ConnectivityManagerNetworkService(
    context: Context,
) : NetworkService {

    private val appContext = context.applicationContext
    private val state = MutableStateFlow(initialState(appContext))
    private val cm: ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            state.value = true
        }

        override fun onLost(network: Network) {
            state.value = false
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (_: SecurityException) {
            // Edge case: emulator / restricted profile. Fall back to optimistic true.
            state.value = true
        }
    }

    override val canAttemptNetworkOperations: StateFlow<Boolean> = state.asStateFlow()

    @MainThread
    override fun shutdown() {
        try {
            cm?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // Already unregistered — ignore.
        }
    }

    private companion object {
        private fun initialState(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
