// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.support

import android.content.Context
import android.content.pm.PackageManager

internal object PlayStoreAvailability {

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(PLAY_STORE_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
