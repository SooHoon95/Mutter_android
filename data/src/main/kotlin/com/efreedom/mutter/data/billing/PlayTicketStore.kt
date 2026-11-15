package com.efreedom.mutter.data.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.efreedom.mutter.domain.error.MutterError
import com.efreedom.mutter.domain.model.StorePurchase
import com.efreedom.mutter.domain.model.StorePurchaseResult
import com.efreedom.mutter.domain.model.TicketProduct
import com.efreedom.mutter.domain.repository.SessionProvider
import com.efreedom.mutter.domain.repository.TicketStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Play Billing으로 편지권(소모성 INAPP `mutter.ticket.1`·`mutter.ticket.5`)을 판다.
 *
 * 흐름: [purchase]가 결제 창을 띄우고 PurchasesUpdatedListener 결과를 기다려 구매 토큰을 돌려준다.
 * 서버 검증(verify-purchase)과 소비 순서는 도메인 TicketUseCase가 쥔다 — 여기서는 절대 먼저 소비하지 않는다.
 * 소비하지 않은 거래는 Play가 3일 뒤 자동 환불하므로, 검증이 실패한 거래는 [unconsumedPurchases]로 다시 보낸다.
 *
 * 결제 창은 Activity가 필요한데 도메인 인터페이스에 Activity를 넘길 수 없어, 앱 Activity 수명주기를 구독해
 * 지금 앞에 있는 Activity를 약한 참조로 쥔다(단일 Activity 앱).
 *
 * Play Billing Library 9 — billing-ktx 9.x는 지금 Kotlin 컴파일러(2.0)가 못 읽어 Java API(콜백)를 쓰고,
 * 필요한 세 호출(상품·미소비 거래 조회, 소비)만 아래 await* 함수로 suspend로 감싼다.
 */
@Singleton
class PlayTicketStore @Inject constructor(
    @ApplicationContext context: Context,
    private val session: SessionProvider,
) : TicketStore {

    private var resumedActivity: WeakReference<Activity>? = null
    private val connectMutex = Mutex()

    /** 지금 진행 중인 결제 한 건의 결과 대기. 결제 창은 한 번에 하나라 단일 슬롯이면 충분하다. */
    private var inFlight: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        // 대기 중인 결제가 없으면(앱 밖에서 완료된 대기 결제 등) 버린다 — 다음 화면 진입의 재전송이 처리한다.
        inFlight?.complete(result to purchases)
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // PBL 8+ — 호출 시점에 서비스가 끊겨 있으면 라이브러리가 다시 붙인다(SERVICE_DISCONNECTED 감소).
        .enableAutoServiceReconnection()
        .build()

    /** 결제 창을 띄울 때 쓰려고 상품 상세를 기억한다(launchBillingFlow는 ProductDetails를 요구). */
    private val detailsCache = mutableMapOf<String, ProductDetails>()

    init {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumedActivity = WeakReference(activity)
                }
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) {
                    if (resumedActivity?.get() === activity) resumedActivity = null
                }
            },
        )
    }

    override suspend fun products(productIds: List<String>): List<TicketProduct> {
        // 스토어를 못 쓰는 기기(Play 없음)·상품 미등록이면 빈 목록 — 화면은 버튼을 끈 채로 둔다.
        if (!connect()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        // PBL 8+ — 결과가 QueryProductDetailsResult로 바뀌었다. 못 찾은 상품은 unfetchedProductList로 따로 오는데,
        // 여기서는 찾은 것만 쓴다(못 찾은 상품 = 버튼을 끈 채로 둔다).
        val (billingResult, details) = client.awaitProductDetails(params)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return emptyList()
        details.forEach { detailsCache[it.productId] = it }
        return productIds.mapNotNull { id ->
            val d = details.firstOrNull { it.productId == id } ?: return@mapNotNull null
            TicketProduct(id = id, price = d.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty())
        }
    }

    override suspend fun purchase(productId: String): StorePurchaseResult {
        if (!connect()) throw MutterError.Server(STORE_UNAVAILABLE)
        val details = detailsCache[productId]
            ?: run { products(listOf(productId)); detailsCache[productId] }
            ?: throw MutterError.Server(STORE_UNAVAILABLE)
        val activity = resumedActivity?.get() ?: throw MutterError.Server(STORE_UNAVAILABLE)

        val waiter = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>()
        inFlight = waiter
        try {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
                )
                // 구매를 로그인 계정에 묶는다 — 서버가 Google 조회 결과의 obfuscatedExternalAccountId와 로그인 사용자를
                // 비교해 다르면 지급하지 않는다(403 account_mismatch). Supabase user id(UUID 36자)는 개인정보가 아니고
                // 64자 제한 안이다. 세션이 없으면(결제 화면은 로그인 뒤에만 열린다) 넣지 않는다.
                .apply { session.currentUserId?.let(::setObfuscatedAccountId) }
                .build()
            val launch = client.launchBillingFlow(activity, flowParams)
            if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                return handleFailure(launch, productId)
            }
            val (result, purchases) = waiter.await()
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return handleFailure(result, productId)
            val purchase = purchases.orEmpty().firstOrNull { productId in it.products }
                ?: return StorePurchaseResult.Cancelled
            return when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED ->
                    StorePurchaseResult.Purchased(StorePurchase(productId, purchase.purchaseToken))
                Purchase.PurchaseState.PENDING -> StorePurchaseResult.Pending
                else -> StorePurchaseResult.Cancelled
            }
        } finally {
            if (inFlight === waiter) inFlight = null
        }
    }

    override suspend fun unconsumedPurchases(): List<StorePurchase> {
        if (!connect()) return emptyList()
        val (billingResult, purchases) = client.awaitPurchases(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return emptyList()
        // 대기(PENDING) 거래는 아직 돈이 안 들어왔다 — 결제가 끝나 PURCHASED가 된 것만 보낸다.
        return purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { p -> p.products.map { StorePurchase(productId = it, purchaseToken = p.purchaseToken) } }
            .distinctBy { it.purchaseToken }
    }

    override suspend fun consume(purchaseToken: String) {
        if (!connect()) throw MutterError.Server(STORE_UNAVAILABLE)
        val code = client.awaitConsume(ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()).responseCode
        // ITEM_NOT_OWNED = 이미 소비됨 — 목표 상태라 성공으로 본다.
        if (code != BillingClient.BillingResponseCode.OK && code != BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
            throw MutterError.Server(STORE_UNAVAILABLE)
        }
    }

    /**
     * 결제 실패 정리. 사용자가 닫았으면 취소, 이미 가진 상품(이전 거래 미소비)이면 그 거래를 돌려줘
     * 호출부가 검증·소비를 마치게 한다. 그 외는 throw.
     */
    private suspend fun handleFailure(result: BillingResult, productId: String): StorePurchaseResult =
        when (result.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> StorePurchaseResult.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                unconsumedPurchases().firstOrNull { it.productId == productId }
                    ?.let { StorePurchaseResult.Purchased(it) }
                    ?: throw MutterError.Server(STORE_UNAVAILABLE)
            else -> throw MutterError.Server(STORE_UNAVAILABLE)
        }

    /** 결제 서비스 연결(끊겼으면 다시). 연결 못 하면 false. */
    private suspend fun connect(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // 자동 재연결이 켜져 있어 다시 붙이는 건 라이브러리 몫이다. 설정 도중 끊기면 이번 연결은 실패로 끝낸다.
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    private companion object {
        const val STORE_UNAVAILABLE = "지금은 결제를 진행할 수 없어요. 잠시 후 다시 시도해 주세요."
    }
}

// 콜백 API → suspend. 결제 라이브러리는 콜백을 한 번만 부른다. 취소된 뒤 온 결과는 버린다.

private suspend fun BillingClient.awaitProductDetails(
    params: QueryProductDetailsParams,
): Pair<BillingResult, List<ProductDetails>> = suspendCancellableCoroutine { cont ->
    queryProductDetailsAsync(params) { result, details ->
        if (cont.isActive) cont.resume(result to details.productDetailsList)
    }
}

private suspend fun BillingClient.awaitPurchases(
    params: QueryPurchasesParams,
): Pair<BillingResult, List<Purchase>> = suspendCancellableCoroutine { cont ->
    queryPurchasesAsync(params) { result, purchases ->
        if (cont.isActive) cont.resume(result to purchases)
    }
}

private suspend fun BillingClient.awaitConsume(params: ConsumeParams): BillingResult =
    suspendCancellableCoroutine { cont ->
        consumeAsync(params) { result, _ ->
            if (cont.isActive) cont.resume(result)
        }
    }
