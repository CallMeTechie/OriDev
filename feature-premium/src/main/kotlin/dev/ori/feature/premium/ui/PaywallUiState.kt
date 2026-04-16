package dev.ori.feature.premium.ui

sealed class PaywallUiState {
    data object Loading : PaywallUiState()
    data class Ready(
        val skus: List<SkuUi>,
        val selectedIndex: Int = 1,
    ) : PaywallUiState()
    data object Purchasing : PaywallUiState()
    data object Purchased : PaywallUiState()
    data class Error(val message: String) : PaywallUiState()
}

data class SkuUi(
    val productId: String,
    val name: String,
    val price: String,
    val period: String,
    val savingsLabel: String? = null,
    val isMostPopular: Boolean = false,
)

sealed class PaywallEffect {
    data object NavigateBack : PaywallEffect()
    data class ShowSnackbar(val message: String) : PaywallEffect()
}
