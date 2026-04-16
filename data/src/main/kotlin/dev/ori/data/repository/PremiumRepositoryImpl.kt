package dev.ori.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.core.billing.BillingClientLauncher
import dev.ori.domain.repository.PremiumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val billingLauncher: BillingClientLauncher,
) : PremiumRepository {

    private companion object {
        const val PREFS_NAME = "premium_prefs"
        const val KEY_ENTITLEMENT = "premium_entitlement"
        const val KEY_LAST_REFRESHED_AT = "premium_last_refreshed_at"
        const val GRACE_PERIOD_MS = 72L * 60 * 60 * 1_000 // 72 hours
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(getCachedEntitlementInternal())

    override val isPremium: Flow<Boolean> = _isPremium

    override suspend fun refreshEntitlement() {
        val subs = billingLauncher.queryPurchases(BillingClient.ProductType.SUBS)
        val inapp = billingLauncher.queryPurchases(BillingClient.ProductType.INAPP)

        val entitled = (subs + inapp).any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.isAcknowledged
        }

        cacheEntitlement(entitled)
    }

    override suspend fun cacheEntitlement(value: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ENTITLEMENT, value)
            .putLong(KEY_LAST_REFRESHED_AT, System.currentTimeMillis())
            .apply()
        _isPremium.value = value
    }

    override suspend fun getCachedEntitlement(): Boolean = getCachedEntitlementInternal()

    override suspend fun getLastRefreshedAt(): Long? {
        val ts = prefs.getLong(KEY_LAST_REFRESHED_AT, -1L)
        return if (ts == -1L) null else ts
    }

    private fun getCachedEntitlementInternal(): Boolean =
        prefs.getBoolean(KEY_ENTITLEMENT, false)
}
