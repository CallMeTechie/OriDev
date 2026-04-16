package dev.ori.core.billing

sealed class BillingPurchaseOutcome {
    data object Success : BillingPurchaseOutcome()
    data class Pending(val orderId: String?) : BillingPurchaseOutcome()
    data class Error(val code: Int, val message: String) : BillingPurchaseOutcome()
    data object UserCancelled : BillingPurchaseOutcome()
}
