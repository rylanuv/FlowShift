package com.flow.shift.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.flow.shift.core.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BillingRepository"

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _purchaseState = MutableStateFlow<BillingPurchaseState>(BillingPurchaseState.Idle)
    val purchaseState: StateFlow<BillingPurchaseState> = _purchaseState.asStateFlow()

    private val _plans = MutableStateFlow<List<SubscriptionPlanItem>>(getDefaultPlans())
    val plans: StateFlow<List<SubscriptionPlanItem>> = _plans.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .enablePrepaidPlans()
        .build()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(pendingPurchasesParams)
        .build()

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    
    private var queryAttempts = 0
    private val maxQueryAttempts = 3

    init {
        startConnection()
    }

    fun startConnection() {
        if (billingClient.isReady) {
            _isConnected.value = true
            return
        }

        Log.d(TAG, "Starting Google Play Billing connection...")
        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Billing setup successful.")
            _isConnected.value = true
            reconnectAttempts = 0
            repositoryScope.launch {
                queryProducts()
                syncPurchasesInternal()
            }
        } else {
            Log.e(TAG, "Billing setup failed with responseCode: ${billingResult.responseCode}, debugMessage: ${billingResult.debugMessage}")
            _isConnected.value = false
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "Billing service disconnected.")
        _isConnected.value = false
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            val delayMillis = (1000L * (1 shl reconnectAttempts)).coerceAtMost(16000L)
            Log.d(TAG, "Scheduling reconnection attempt $reconnectAttempts in $delayMillis ms...")
            repositoryScope.launch {
                delay(delayMillis)
                startConnection()
            }
        }
    }

    suspend fun queryProducts() {
        if (!billingClient.isReady) {
            Log.w(TAG, "BillingClient not ready during queryProducts")
            return
        }

        try {
            // 1. Query Subscriptions
            val subProductList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingConstants.PRODUCT_MONTHLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingConstants.PRODUCT_YEARLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingConstants.PRODUCT_SUBSCRIPTION_GROUP)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val subParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subProductList)
                .build()

            val subResult = billingClient.queryProductDetails(subParams)

            // 2. Query In-App Purchases (Lifetime)
            val inAppProductList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(BillingConstants.PRODUCT_LIFETIME)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )

            val inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inAppProductList)
                .build()

            val inAppResult = billingClient.queryProductDetails(inAppParams)

            if (subResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
                inAppResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "queryProducts failed: sub=${subResult.billingResult.debugMessage}, inapp=${inAppResult.billingResult.debugMessage}")
                if (queryAttempts < maxQueryAttempts) {
                    queryAttempts++
                    repositoryScope.launch {
                        delay(2000L * queryAttempts)
                        queryProducts()
                    }
                }
                return
            }
            queryAttempts = 0

            val subDetails = subResult.productDetailsList.orEmpty()
            val inAppDetails = inAppResult.productDetailsList.orEmpty()

            Log.d(TAG, "Fetched ${subDetails.size} sub products and ${inAppDetails.size} in-app products")

            updatePlansFromDetails(subDetails, inAppDetails)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying products from Google Play", e)
            if (queryAttempts < maxQueryAttempts) {
                queryAttempts++
                repositoryScope.launch {
                    delay(2000L * queryAttempts)
                    queryProducts()
                }
            }
        }
    }

    private fun updatePlansFromDetails(
        subDetails: List<ProductDetails>,
        inAppDetails: List<ProductDetails>
    ) {
        val currentPlans = getDefaultPlans().toMutableList()

        // Gather all offers across all subscription products
        val allSubOffers = subDetails.flatMap { product ->
            product.subscriptionOfferDetails.orEmpty().map { offer ->
                val recurringPhase = offer.pricingPhases.pricingPhaseList.lastOrNull()
                Triple(product, offer, recurringPhase?.formattedPrice)
            }
        }

        // 1. Resolve Monthly Plan
        // Matches basePlanId: flowshift-monthly, monthly, containing "month", or billing period P1M
        val monthlyMatch = allSubOffers.firstOrNull { (_, offer, price) ->
            price != null && (
                offer.basePlanId == BillingConstants.BASE_PLAN_MONTHLY ||
                offer.basePlanId == BillingConstants.ALT_BASE_PLAN_MONTHLY ||
                offer.basePlanId.contains("month", ignoreCase = true) ||
                offer.pricingPhases.pricingPhaseList.any { it.billingPeriod == "P1M" }
            )
        } ?: run {
            val prod = subDetails.find { it.productId == BillingConstants.PRODUCT_MONTHLY }
            val offer = prod?.subscriptionOfferDetails?.firstOrNull()
            val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            if (prod != null && offer != null && price != null) Triple(prod, offer, price) else null
        }

        if (monthlyMatch != null) {
            val (prod, offer, price) = monthlyMatch
            val index = currentPlans.indexOfFirst { it.id == BillingConstants.PLAN_MONTHLY }
            if (index != -1) {
                currentPlans[index] = currentPlans[index].copy(
                    formattedPrice = price ?: currentPlans[index].formattedPrice,
                    productDetails = prod,
                    offerToken = offer.offerToken
                )
                Log.d(TAG, "Monthly plan resolved: ${prod.productId} (${offer.basePlanId}) at $price")
            }
        }

        // 2. Resolve Yearly Plan
        // Matches basePlanId: flowshift-yearly, yearly, containing "year", or billing period P1Y
        val yearlyMatch = allSubOffers.firstOrNull { (_, offer, price) ->
            price != null && (
                offer.basePlanId == BillingConstants.BASE_PLAN_YEARLY ||
                offer.basePlanId == BillingConstants.ALT_BASE_PLAN_YEARLY ||
                offer.basePlanId.contains("year", ignoreCase = true) ||
                offer.pricingPhases.pricingPhaseList.any { it.billingPeriod == "P1Y" }
            )
        } ?: run {
            val prod = subDetails.find { it.productId == BillingConstants.PRODUCT_YEARLY }
            val offer = prod?.subscriptionOfferDetails?.firstOrNull()
            val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            if (prod != null && offer != null && price != null) Triple(prod, offer, price) else null
        }

        if (yearlyMatch != null) {
            val (prod, offer, price) = yearlyMatch
            val index = currentPlans.indexOfFirst { it.id == BillingConstants.PLAN_YEARLY }
            if (index != -1) {
                currentPlans[index] = currentPlans[index].copy(
                    formattedPrice = price ?: currentPlans[index].formattedPrice,
                    productDetails = prod,
                    offerToken = offer.offerToken
                )
                Log.d(TAG, "Yearly plan resolved: ${prod.productId} (${offer.basePlanId}) at $price")
            }
        }

        // 3. Resolve Lifetime Plan
        val lifetimeDetails = inAppDetails.find { it.productId == BillingConstants.PRODUCT_LIFETIME }
        val lifetimePrice = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice

        if (lifetimeDetails != null && lifetimePrice != null) {
            val index = currentPlans.indexOfFirst { it.id == BillingConstants.PLAN_LIFETIME }
            if (index != -1) {
                currentPlans[index] = currentPlans[index].copy(
                    formattedPrice = lifetimePrice,
                    productDetails = lifetimeDetails
                )
                Log.d(TAG, "Lifetime plan resolved: ${lifetimeDetails.productId} at $lifetimePrice")
            }
        }

        _plans.value = currentPlans
    }

    fun launchBillingFlow(activity: Activity, planId: String) {
        val plan = _plans.value.find { it.id == planId }
        if (plan == null) {
            _purchaseState.value = BillingPurchaseState.Error("Invalid plan selected.")
            return
        }

        _purchaseState.value = BillingPurchaseState.Loading("Preparing Google Play purchase...")

        if (!billingClient.isReady) {
            repositoryScope.launch {
                val isDevMode = settingsDataStore.isDeveloperModeEnabled.first()
                if (isDevMode) {
                    settingsDataStore.setIsPremium(true)
                    _purchaseState.value = BillingPurchaseState.Success("Unlocked Pro via Developer Mode (Google Play offline)")
                } else {
                    _purchaseState.value = BillingPurchaseState.Error("Google Play Store is not ready. Please try again.")
                    startConnection()
                }
            }
            return
        }

        val details = plan.productDetails
        if (details == null) {
            repositoryScope.launch {
                val isDevMode = settingsDataStore.isDeveloperModeEnabled.first()
                if (isDevMode) {
                    settingsDataStore.setIsPremium(true)
                    _purchaseState.value = BillingPurchaseState.Success("Unlocked Pro via Developer Mode (Products not published)")
                } else {
                    _purchaseState.value = BillingPurchaseState.Error("Plan not currently available in Google Play. Please check back soon.")
                }
            }
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        if (details.productType == BillingClient.ProductType.SUBS) {
            val token = plan.offerToken ?: details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (token != null) {
                productDetailsParamsBuilder.setOfferToken(token)
            } else {
                _purchaseState.value = BillingPurchaseState.Error("No valid offer found for this subscription.")
                return
            }
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val response = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "launchBillingFlow failed with responseCode: ${response.responseCode}, debug: ${response.debugMessage}")
            _purchaseState.value = BillingPurchaseState.Error("Failed to open Play Store purchase: ${response.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    repositoryScope.launch {
                        handlePurchases(purchases)
                    }
                } else {
                    _purchaseState.value = BillingPurchaseState.Idle
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled the purchase flow.")
                _purchaseState.value = BillingPurchaseState.Cancelled
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned. Synchronizing entitlement...")
                repositoryScope.launch {
                    restorePurchases()
                }
            }
            else -> {
                val errorMsg = billingResult.debugMessage.ifBlank { "Purchase failed with code ${billingResult.responseCode}" }
                Log.e(TAG, "Purchases update error: $errorMsg")
                _purchaseState.value = BillingPurchaseState.Error(errorMsg)
            }
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        var anyPurchased = false
        var hasPending = false

        for (purchase in purchases) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    // Grant premium immediately to avoid locking user out if ack network call fails
                    anyPurchased = true
                    
                    if (!purchase.isAcknowledged) {
                        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        val ackResult = billingClient.acknowledgePurchase(acknowledgeParams)
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Successfully acknowledged purchase: ${purchase.orderId}")
                        } else {
                            Log.e(TAG, "Failed to acknowledge purchase: ${ackResult.debugMessage}. Will retry on next sync.")
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    Log.d(TAG, "Purchase is pending: ${purchase.orderId}")
                    hasPending = true
                }
                else -> {
                    Log.d(TAG, "Unknown purchase state: ${purchase.purchaseState}")
                }
            }
        }

        if (anyPurchased) {
            settingsDataStore.setIsPremium(true)
            _purchaseState.value = BillingPurchaseState.Success("Welcome to FlowShift Pro!")
        } else if (hasPending) {
            _purchaseState.value = BillingPurchaseState.Success("Purchase pending. Pro features will unlock once payment completes.")
        }
    }

    suspend fun restorePurchases() {
        _purchaseState.value = BillingPurchaseState.Loading("Restoring previous purchases...")

        if (!billingClient.isReady) {
            val isDevMode = settingsDataStore.isDeveloperModeEnabled.first()
            if (isDevMode) {
                settingsDataStore.setIsPremium(true)
                _purchaseState.value = BillingPurchaseState.Success("Purchases restored (Developer Mode).")
            } else {
                _purchaseState.value = BillingPurchaseState.Error("Unable to connect to Google Play Store to restore purchases.")
            }
            return
        }

        try {
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            val subsResult = billingClient.queryPurchasesAsync(subsParams)
            val inAppResult = billingClient.queryPurchasesAsync(inAppParams)

            if (subsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
                inAppResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _purchaseState.value = BillingPurchaseState.Error("Failed to query Play Store to restore purchases.")
                return
            }

            val allPurchases = (subsResult.purchasesList.orEmpty() + inAppResult.purchasesList.orEmpty())
            val activePurchases = allPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

            if (activePurchases.isNotEmpty()) {
                handlePurchases(activePurchases)
                _purchaseState.value = BillingPurchaseState.Success("Purchases restored successfully!")
            } else {
                val pretendSubscribed = settingsDataStore.pretendSubscribed.first()
                if (!pretendSubscribed) {
                    settingsDataStore.setIsPremium(false)
                }
                _purchaseState.value = BillingPurchaseState.Error("No active subscriptions or purchases found on this Google account.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring purchases", e)
            _purchaseState.value = BillingPurchaseState.Error("Failed to restore purchases: ${e.localizedMessage}")
        }
    }

    suspend fun initializeAndSyncPurchases() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        syncPurchasesInternal()
    }

    private suspend fun syncPurchasesInternal() {
        try {
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            val subsResult = billingClient.queryPurchasesAsync(subsParams)
            val inAppResult = billingClient.queryPurchasesAsync(inAppParams)

            if (subsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
                inAppResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to query purchases during sync. Response codes: subs=${subsResult.billingResult.responseCode}, inapp=${inAppResult.billingResult.responseCode}")
                return // Do not revoke premium if the query failed
            }

            val allPurchases = (subsResult.purchasesList.orEmpty() + inAppResult.purchasesList.orEmpty())
            val activePurchases = allPurchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

            if (activePurchases.isNotEmpty()) {
                settingsDataStore.setIsPremium(true) // Set premium immediately
                for (purchase in activePurchases) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        val ackResult = billingClient.acknowledgePurchase(acknowledgeParams)
                        if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            Log.e(TAG, "Failed to acknowledge purchase during sync: ${ackResult.debugMessage}")
                        }
                    }
                }
            } else {
                val pretendSubscribed = settingsDataStore.pretendSubscribed.first()
                if (!pretendSubscribed) {
                    settingsDataStore.setIsPremium(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync purchases in background", e)
        }
    }

    fun resetPurchaseState() {
        _purchaseState.value = BillingPurchaseState.Idle
    }

    private fun getDefaultPlans(): List<SubscriptionPlanItem> {
        return listOf(
            SubscriptionPlanItem(
                id = BillingConstants.PLAN_MONTHLY,
                title = "Monthly",
                formattedPrice = "$4.99",
                period = "/month"
            ),
            SubscriptionPlanItem(
                id = BillingConstants.PLAN_YEARLY,
                title = "Yearly",
                formattedPrice = "$15.99",
                period = "/year",
                badgeText = "BEST VALUE"
            ),
            SubscriptionPlanItem(
                id = BillingConstants.PLAN_LIFETIME,
                title = "Lifetime",
                formattedPrice = "$50",
                period = " once"
            )
        )
    }
}
