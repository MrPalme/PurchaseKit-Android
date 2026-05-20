// SPDX-License-Identifier: Apache-2.0
package com.markusmock.purchasekit.sample.views;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.android.billingclient.api.ProductDetails;
import com.markusmock.purchasekit.PurchaseKitConfig;
import com.markusmock.purchasekit.PurchaseKitDelegate;
import com.markusmock.purchasekit.PurchaseKitManager;
import com.markusmock.purchasekit.api.AnyPurchasableOption;
import com.markusmock.purchasekit.api.PurchasableOption;
import com.markusmock.purchasekit.api.PurchaseType;
import com.markusmock.purchasekit.model.EntitlementState;
import com.markusmock.purchasekit.model.PurchaseError;
import com.markusmock.purchasekit.model.PurchaseFlowState;

import java.util.Arrays;
import java.util.List;

/**
 * Pure-Java proof that the PurchaseKit public API is callable from Java
 * without any Kotlin-specific syntactic sugar. Compiles into
 * {@code :sample-views}, which has zero Compose dependencies.
 *
 * <p>Demonstrates:</p>
 * <ul>
 *   <li>Factory: {@link PurchaseKitManager#create(Context, Iterable)}</li>
 *   <li>Factory with config: {@link PurchaseKitManager#create(Context, Iterable, PurchaseKitConfig)}</li>
 *   <li>Listener registration via {@link PurchaseKitManager#addListener(PurchaseKitDelegate)}</li>
 *   <li>Companion-style helper: {@link PurchaseKitManager#openSubscriptionManagement(Context)}</li>
 *   <li>Implementing {@link PurchaseKitDelegate} with default callbacks</li>
 *   <li>Implementing {@link PurchasableOption} as a Java class</li>
 * </ul>
 */
public final class JavaInteropDemo {

    private final PurchaseKitManager manager;

    public JavaInteropDemo(@NonNull Context context, @Nullable LifecycleOwner owner) {
        List<PurchasableOption> catalogue = Arrays.<PurchasableOption>asList(
                JavaOption.MONTHLY,
                JavaOption.LIFETIME
        );

        // Factory with explicit config — exercises @JvmStatic and @JvmOverloads.
        this.manager = PurchaseKitManager.create(context, catalogue, new PurchaseKitConfig());

        // Callback path — Java consumers don't need StateFlow / coroutines.
        manager.addListener(new PurchaseKitDelegate() {
            @Override
            public void onEntitlementUpdated(@NonNull AnyPurchasableOption option,
                                             @NonNull EntitlementState state) {
                // Toggle feature gate in host code.
            }

            @Override
            public void onPurchaseFlowStateChanged(@NonNull PurchaseFlowState state,
                                                    @Nullable AnyPurchasableOption option) {
                // Update CTA spinners.
            }

            @Override
            public void onProductsLoaded(@NonNull java.util.Map<AnyPurchasableOption, ProductDetails> products) {
                // No-op.
            }

            @Override
            public void onProductsLoadFailed(@NonNull PurchaseError error) {
                // No-op.
            }

            @Override
            public void onRestoreCompleted(@NonNull java.util.Map<AnyPurchasableOption, ? extends EntitlementState> entitlements) {
                // No-op.
            }

            @Override
            public void onRestoreFailed(@NonNull PurchaseError error) {
                // No-op.
            }
        }, owner);
    }

    /** Exercise the non-suspend public API from Java. */
    public void buy(@NonNull Activity activity, @NonNull PurchasableOption option) {
        manager.purchase(option, activity);
    }

    public void restore() {
        manager.restorePurchases();
    }

    public boolean isProActive(@NonNull PurchasableOption option) {
        return manager.isEntitled(option);
    }

    public void openManageSubscription(@NonNull Context context) {
        PurchaseKitManager.openSubscriptionManagement(context);
    }

    public void teardown() {
        manager.shutdown();
    }

    /** Java implementation of the host {@link PurchasableOption} contract. */
    enum JavaOption implements PurchasableOption {
        MONTHLY("sample.pro.monthly", PurchaseType.AUTO_RENEWING_SUBSCRIPTION, "Pro Monthly", 0),
        LIFETIME("sample.lifetime", PurchaseType.NON_CONSUMABLE, "Lifetime", 1);

        private final String productId;
        private final PurchaseType type;
        private final String title;
        private final int sortOrder;

        JavaOption(String productId, PurchaseType type, String title, int sortOrder) {
            this.productId = productId;
            this.type = type;
            this.title = title;
            this.sortOrder = sortOrder;
        }

        @NonNull @Override public String getProductId() { return productId; }
        @NonNull @Override public PurchaseType getPurchaseType() { return type; }
        @NonNull @Override public String getTitle() { return title; }
        @Override public int getSortOrder() { return sortOrder; }

        @NonNull @Override public String getId() { return productId; }
        @Nullable @Override public String getSubtitle() { return null; }
        @Nullable @Override public String getOfferingId() { return null; }
        @Nullable @Override public com.markusmock.purchasekit.api.TierBadge getBadge() { return null; }
        @Override public int getTierRank() { return sortOrder; }
        @Nullable @Override public String getSubscriptionGroup() { return null; }
    }
}
