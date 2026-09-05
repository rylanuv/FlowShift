package com.flow.shift.feature.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.shift.core.billing.BillingConstants
import com.flow.shift.core.billing.BillingPurchaseState
import com.flow.shift.core.billing.BillingRepository
import com.flow.shift.core.billing.SubscriptionPlanItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingRepository: BillingRepository
) : ViewModel() {

    val plans: StateFlow<List<SubscriptionPlanItem>> = billingRepository.plans
    val purchaseState: StateFlow<BillingPurchaseState> = billingRepository.purchaseState

    private val _selectedPlanId = MutableStateFlow(BillingConstants.PLAN_YEARLY)
    val selectedPlanId: StateFlow<String> = _selectedPlanId.asStateFlow()

    init {
        viewModelScope.launch {
            billingRepository.queryProducts()
        }
    }

    fun selectPlan(planId: String) {
        _selectedPlanId.value = planId
    }

    fun startPurchase(activity: Activity) {
        billingRepository.launchBillingFlow(activity, _selectedPlanId.value)
    }

    fun restorePurchases() {
        viewModelScope.launch {
            billingRepository.restorePurchases()
        }
    }

    fun resetPurchaseState() {
        billingRepository.resetPurchaseState()
    }
}
