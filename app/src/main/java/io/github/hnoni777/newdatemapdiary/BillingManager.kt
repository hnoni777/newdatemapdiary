package io.github.hnoni777.newdatemapdiary

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product

/**
 * 💰 진짜 구글 플레이 결제 매니저 (Real Billing Manager)
 * premium_stickers_all 상품을 실제로 결제하고 처리합니다.
 */
class BillingManager(private val activity: Activity, private val onPurchaseSuccess: (isInitialCheck: Boolean) -> Unit) {

    private val billingClient: BillingClient = BillingClient.newBuilder(activity)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
        .enablePendingPurchases()
        .build()

    companion object {
        const val STICKER_SKU = "premium_stickers_all"
    }

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BILLING", "구글 결제 서비스 연결 성공")
                    checkPurchasedStickers()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("BILLING", "구글 결제 서비스 연결 끊김, 재시도 중...")
                startConnection()
            }
        })
    }

    fun checkPurchasedStickers() {
        if (!billingClient.isReady) return
        
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            Log.d("BILLING", "구매 내역 조회 결과: ${billingResult.responseCode}, 개수: ${purchases.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isEmpty()) {
                    Log.d("BILLING", "구매 내역이 비어있습니다. (정상 환불됨)")
                }
                for (purchase in purchases) {
                    Log.d("BILLING", "발견된 상품: ${purchase.products}, 상태: ${purchase.purchaseState}")
                    if (purchase.products.contains(STICKER_SKU) && 
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        onPurchaseSuccess(true) // Initial check / Restore
                        Log.d("BILLING", "이미 구매한 상품 확인됨: $STICKER_SKU")
                    }
                }
            }
        }
    }

    fun launchPurchaseFlow() {
        if (!billingClient.isReady) {
            Log.e("BILLING", "결제 서비스가 준비되지 않았습니다.")
            return
        }

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(Product.newBuilder()
                    .setProductId(STICKER_SKU)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build())
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val productDetailsParamsList = listOf(
                    ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
            )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                billingClient.launchBillingFlow(activity, billingFlowParams)
            } else {
                Log.e("BILLING", "상품 정보를 불러올 수 없습니다: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d("BILLING", "구매 승인 완료: $STICKER_SKU")
                        onPurchaseSuccess(false) // New purchase
                    }
                }
            } else {
                onPurchaseSuccess(false) // New purchase (already acknowledged or edge case)
            }
        }
    }
}
