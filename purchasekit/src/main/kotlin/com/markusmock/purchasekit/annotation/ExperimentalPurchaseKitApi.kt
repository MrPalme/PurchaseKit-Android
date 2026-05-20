// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.annotation

/**
 * Marks API surface that may change without a deprecation cycle.
 *
 * Consumers must opt in by annotating call sites with this annotation or by
 * adding `-opt-in=com.markusmock.purchasekit.annotation.ExperimentalPurchaseKitApi`
 * to their compiler flags. Promotion to stable is announced in `CHANGELOG.md`.
 *
 * @since 0.1.0
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This PurchaseKit API is experimental and may change without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalPurchaseKitApi
