package dev.ori.feature.premium.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.billing.BillingClientLauncher
import dev.ori.core.billing.BillingPurchaseOutcome
import dev.ori.domain.usecase.PurchaseUseCase
import dev.ori.domain.usecase.RestorePurchasesUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingLauncher: BillingClientLauncher,
    private val purchaseUseCase: PurchaseUseCase,
    private val restorePurchasesUseCase: RestorePurchasesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<PaywallUiState>(PaywallUiState.Loading)
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PaywallEffect>()
    val effects: SharedFlow<PaywallEffect> = _effects.asSharedFlow()

    init {
        loadSkus()
    }

    private fun loadSkus() {
        viewModelScope.launch {
            _state.value = PaywallUiState.Loading
            try {
                val details = billingLauncher.queryProductDetails(
                    listOf(
                        SKU_MONTHLY,
                        SKU_YEARLY,
                        SKU_LIFETIME,
                    ),
                )
                val skus = details.map { product ->
                    val offer = product.subscriptionOfferDetails?.firstOrNull()
                    val oneTime = product.oneTimePurchaseOfferDetails
                    SkuUi(
                        productId = product.productId,
                        name = product.name,
                        price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                            ?: oneTime?.formattedPrice
                            ?: "–",
                        period = when (product.productId) {
                            SKU_MONTHLY -> "/ Monat"
                            SKU_YEARLY -> "/ Jahr"
                            else -> "einmalig"
                        },
                        savingsLabel = if (product.productId == SKU_YEARLY) "Spare 33 %" else null,
                        isMostPopular = product.productId == SKU_YEARLY,
                    )
                }
                _state.value = PaywallUiState.Ready(skus = skus)
            } catch (e: Exception) {
                _state.value = PaywallUiState.Error(
                    message = e.message ?: "Produkte konnten nicht geladen werden",
                )
            }
        }
    }

    fun selectSku(index: Int) {
        val current = _state.value
        if (current is PaywallUiState.Ready) {
            _state.value = current.copy(selectedIndex = index)
        }
    }

    fun purchase(activity: Activity) {
        val current = _state.value
        if (current !is PaywallUiState.Ready) return
        val sku = current.skus.getOrNull(current.selectedIndex) ?: return

        viewModelScope.launch {
            _state.value = PaywallUiState.Purchasing
            try {
                val details = billingLauncher.queryProductDetails(listOf(sku.productId))
                val product = details.firstOrNull()
                if (product == null) {
                    _state.value = PaywallUiState.Error("Produkt nicht gefunden")
                    return@launch
                }
                when (val outcome = billingLauncher.launchPurchaseFlow(activity, product)) {
                    is BillingPurchaseOutcome.Success -> {
                        purchaseUseCase(purchaseSucceeded = true)
                        _state.value = PaywallUiState.Purchased
                        _effects.emit(PaywallEffect.ShowSnackbar("Premium aktiviert!"))
                        _effects.emit(PaywallEffect.NavigateBack)
                    }
                    is BillingPurchaseOutcome.UserCancelled -> {
                        _state.value = current
                        _effects.emit(PaywallEffect.ShowSnackbar("Kauf abgebrochen"))
                    }
                    is BillingPurchaseOutcome.Pending -> {
                        _state.value = current
                        _effects.emit(PaywallEffect.ShowSnackbar("Kauf ausstehend"))
                    }
                    is BillingPurchaseOutcome.Error -> {
                        _state.value = PaywallUiState.Error(outcome.message)
                    }
                }
            } catch (e: Exception) {
                _state.value = PaywallUiState.Error(
                    message = e.message ?: "Kauf fehlgeschlagen",
                )
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            try {
                restorePurchasesUseCase()
                _effects.emit(PaywallEffect.ShowSnackbar("Käufe wiederhergestellt"))
                _effects.emit(PaywallEffect.NavigateBack)
            } catch (e: Exception) {
                _effects.emit(
                    PaywallEffect.ShowSnackbar(
                        e.message ?: "Wiederherstellung fehlgeschlagen",
                    ),
                )
            }
        }
    }

    fun dismissError() {
        loadSkus()
    }

    private companion object {
        const val SKU_MONTHLY = "oridev_premium_monthly"
        const val SKU_YEARLY = "oridev_premium_yearly"
        const val SKU_LIFETIME = "oridev_premium_lifetime"
    }
}
