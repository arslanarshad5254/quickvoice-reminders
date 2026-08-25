package com.xovrix.quickvoicereminder.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(
    private val context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "BillingManager"
        const val SUB_MONTHLY_AD_FREE = "quickvoice_ad_free_monthly"
        private const val PREFS_NAME = "quickvoice_billing_prefs"
        private const val KEY_IS_ADS_FREE = "key_is_ads_free"
        private const val KEY_EXPIRY_DATE = "key_expiry_date"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAdsFree = MutableStateFlow(prefs.getBoolean(KEY_IS_ADS_FREE, false))
    val isAdsFree: StateFlow<Boolean> = _isAdsFree.asStateFlow()

    private val _formattedPrice = MutableStateFlow("$1.00 / month")
    val formattedPrice: StateFlow<String> = _formattedPrice.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _purchaseStatusMessage = MutableStateFlow<String?>(null)
    val purchaseStatusMessage: StateFlow<String?> = _purchaseStatusMessage.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .enablePrepaidPlans()
        .build()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(pendingPurchasesParams)
        .build()

    init {
        startConnection()
    }

    fun startConnection() {
        if (billingClient.isReady) {
            querySubscriptionDetails()
            queryActivePurchases()
            return
        }

        _isConnecting.value = true
        billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        _isConnecting.value = false
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Billing setup successful")
            querySubscriptionDetails()
            queryActivePurchases()
        } else {
            Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        _isConnecting.value = false
        Log.w(TAG, "Billing service disconnected, will retry when needed")
    }

    fun querySubscriptionDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUB_MONTHLY_AD_FREE)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryProductDetailsList.firstOrNull { it.productId == SUB_MONTHLY_AD_FREE }
                _productDetails.value = details

                details?.subscriptionOfferDetails?.firstOrNull()?.let { offer ->
                    val pricing = offer.pricingPhases.pricingPhaseList.firstOrNull()
                    if (pricing != null) {
                        _formattedPrice.value = "${pricing.formattedPrice} / month"
                    }
                }
            } else {
                Log.w(TAG, "Failed to query product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryActivePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActiveSub = false
                for (purchase in purchases) {
                    if (purchase.products.contains(SUB_MONTHLY_AD_FREE) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        hasActiveSub = true
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
                updateAdsFreeStatus(hasActiveSub)
            } else {
                Log.w(TAG, "Failed to query active purchases: ${billingResult.debugMessage}")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    var activated = false
                    for (purchase in purchases) {
                        if (purchase.products.contains(SUB_MONTHLY_AD_FREE) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        ) {
                            activated = true
                            acknowledgePurchase(purchase)
                        }
                    }
                    if (activated) {
                        updateAdsFreeStatus(true)
                        _purchaseStatusMessage.value = "Thank you! Ads-Free subscription is now active."
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseStatusMessage.value = "Subscription purchase was cancelled."
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                updateAdsFreeStatus(true)
                _purchaseStatusMessage.value = "Your Ads-Free subscription is already active."
            }
            else -> {
                _purchaseStatusMessage.value = "Purchase error: ${billingResult.debugMessage}"
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully: ${purchase.orderId}")
            } else {
                Log.w(TAG, "Purchase acknowledgement failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val details = _productDetails.value
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken

        if (details != null && offerToken != null) {
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            billingClient.launchBillingFlow(activity, billingFlowParams)
        } else {
            // If Play Store product details aren't loaded yet, reconnect & retry
            startConnection()
            _purchaseStatusMessage.value = "Connecting to Google Play Store..."
        }
    }

    // Direct toggle / simulation for testing environments without Play Store account
    fun setManualAdsFree(enabled: Boolean) {
        updateAdsFreeStatus(enabled)
        _purchaseStatusMessage.value = if (enabled) "Ads-Free plan activated!" else "Free plan active."
    }

    private fun updateAdsFreeStatus(active: Boolean) {
        _isAdsFree.value = active
        prefs.edit().putBoolean(KEY_IS_ADS_FREE, active).apply()
    }

    fun clearStatusMessage() {
        _purchaseStatusMessage.value = null
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
