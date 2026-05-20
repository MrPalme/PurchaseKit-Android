// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.support

import android.util.Log

/**
 * Minimal logging seam.
 *
 * Wraps [android.util.Log] behind a one-method interface so consumers can
 * redirect PurchaseKit's diagnostic output into their existing logging
 * pipeline (Timber, OSLog bridge, file logging, etc.) without depending on
 * Logcat directly. Library code never calls [Log] outside the [Default]
 * implementation.
 *
 * Threading: implementations must be safe to call from any thread. The
 * library calls [log] from a mix of `Dispatchers.Main` and `Dispatchers.IO`.
 *
 * @since 0.1.0
 */
public fun interface PurchaseKitLogger {

    /**
     * Emits a log line at [level] under [tag] with [message] and an optional
     * [throwable].
     *
     * @param level     One of [Level].
     * @param tag       Subsystem identifier (e.g. `"PurchaseKit.Transaction"`).
     * @param message   Human-readable line. Non-null.
     * @param throwable Optional cause attached to the line.
     */
    public fun log(level: Level, tag: String, message: String, throwable: Throwable?)

    /** Severity levels exposed to a [PurchaseKitLogger]. */
    public enum class Level { Debug, Info, Warn, Error }

    public companion object {
        /** Default implementation that forwards to [android.util.Log]. */
        public val Default: PurchaseKitLogger = PurchaseKitLogger { level, tag, message, throwable ->
            when (level) {
                Level.Debug -> Log.d(tag, message, throwable)
                Level.Info -> Log.i(tag, message, throwable)
                Level.Warn -> Log.w(tag, message, throwable)
                Level.Error -> Log.e(tag, message, throwable)
            }
        }

        /** No-op logger, useful in tests. */
        public val NoOp: PurchaseKitLogger = PurchaseKitLogger { _, _, _, _ -> }
    }
}

internal fun PurchaseKitLogger.d(tag: String, message: String, t: Throwable? = null): Unit =
    log(PurchaseKitLogger.Level.Debug, tag, message, t)

internal fun PurchaseKitLogger.i(tag: String, message: String, t: Throwable? = null): Unit =
    log(PurchaseKitLogger.Level.Info, tag, message, t)

internal fun PurchaseKitLogger.w(tag: String, message: String, t: Throwable? = null): Unit =
    log(PurchaseKitLogger.Level.Warn, tag, message, t)

internal fun PurchaseKitLogger.e(tag: String, message: String, t: Throwable? = null): Unit =
    log(PurchaseKitLogger.Level.Error, tag, message, t)
