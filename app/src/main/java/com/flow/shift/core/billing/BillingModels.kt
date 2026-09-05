package com.flow.shift.core.billing

import com.android.billingclient.api.ProductDetails

data class SubscriptionPlanItem(
    val id: String,
    val title: String,
    val formattedPrice: String,
    val period: String,
    val badgeText: String? = null,
    val productDetails: ProductDetails? = null,
    val offerToken: String? = null
)

sealed interface BillingPurchaseState {
    data object Idle : BillingPurchaseState
    data class Loading(val message: String? = null) : BillingPurchaseState
    data class Success(val message: String) : BillingPurchaseState
    data object Cancelled : BillingPurchaseState
    data class Error(val message: String) : BillingPurchaseState
}
