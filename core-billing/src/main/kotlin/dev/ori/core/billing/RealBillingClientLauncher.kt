package dev.ori.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class RealBillingClientLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : BillingClientLauncher {

    /**
     * Continuation for the current purchase flow — set before
     * [BillingClient.launchBillingFlow] and consumed in
     * [PurchasesUpdatedListener].
     */
    private var purchaseContinuation:
        kotlinx.coroutines.CancellableContinuation<BillingPurchaseOutcome>? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        val cont = purchaseContinuation
        purchaseContinuation = null
        if (cont == null || !cont.isActive) return@PurchasesUpdatedListener

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    cont.resume(BillingPurchaseOutcome.Pending(purchase.orderId))
                } else {
                    cont.resume(BillingPurchaseOutcome.Success)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                cont.resume(BillingPurchaseOutcome.UserCancelled)
            else ->
                cont.resume(
                    BillingPurchaseOutcome.Error(
                        result.responseCode,
                        result.debugMessage,
                    ),
                )
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .build()

    // ── Connection ──────────────────────────────────────────────────

    private suspend fun ensureConnected() {
        if (billingClient.isReady) return
        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onBillingServiceDisconnected() {
                    // No-op — the next call will reconnect.
                }
            })
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    override suspend fun queryProductDetails(skus: List<String>): List<ProductDetails> {
        ensureConnected()

        val products = skus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        } + skus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { _, detailsList ->
                if (cont.isActive) cont.resume(detailsList.orEmpty())
            }
        }
    }

    override suspend fun launchPurchaseFlow(
        activity: Activity,
        details: ProductDetails,
    ): BillingPurchaseOutcome {
        ensureConnected()

        return suspendCancellableCoroutine { cont ->
            purchaseContinuation = cont

            val offerToken = details.subscriptionOfferDetails
                ?.firstOrNull()?.offerToken

            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(details)

            if (offerToken != null) {
                productDetailsParamsBuilder.setOfferToken(offerToken)
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                .build()

            val result = billingClient.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                purchaseContinuation = null
                if (cont.isActive) {
                    cont.resume(
                        BillingPurchaseOutcome.Error(
                            result.responseCode,
                            result.debugMessage,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun queryPurchases(productType: String): List<Purchase> {
        ensureConnected()

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(params) { _, purchasesList ->
                if (cont.isActive) cont.resume(purchasesList)
            }
        }
    }

    override suspend fun acknowledgePurchase(token: String): BillingPurchaseOutcome {
        ensureConnected()

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(token)
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(params) { result ->
                if (!cont.isActive) return@acknowledgePurchase
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK ->
                        cont.resume(BillingPurchaseOutcome.Success)
                    else ->
                        cont.resume(
                            BillingPurchaseOutcome.Error(
                                result.responseCode,
                                result.debugMessage,
                            ),
                        )
                }
            }
        }
    }
}
