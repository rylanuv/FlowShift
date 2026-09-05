package com.flow.shift.core.billing

object BillingConstants {
    // Standard Google Play Product IDs
    // Subscriptions
    const val PRODUCT_MONTHLY = "flowshift_monthly"
    const val PRODUCT_YEARLY = "flowshift_yearly"

    // Alternative: Single subscription product with multiple base plans
    const val PRODUCT_SUBSCRIPTION_GROUP = "flowshift_pro"
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_YEARLY = "yearly"

    // One-time In-App Product (Non-consumable)
    const val PRODUCT_LIFETIME = "flowshift_lifetime"

    // Plan Keys used in UI and state
    const val PLAN_MONTHLY = "MONTHLY"
    const val PLAN_YEARLY = "YEARLY"
    const val PLAN_LIFETIME = "LIFETIME"
}
