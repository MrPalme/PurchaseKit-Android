// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.sample.compose

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.markusmock.purchasekit.PurchaseKitManager

class SampleApplication : Application() {

    lateinit var purchaseKit: PurchaseKitManager
        private set

    override fun onCreate() {
        super.onCreate()
        purchaseKit = PurchaseKitManager.create(this, AppOption.entries.toList())
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                purchaseKit.onAppEnteredForeground()
            }
        })
    }
}
