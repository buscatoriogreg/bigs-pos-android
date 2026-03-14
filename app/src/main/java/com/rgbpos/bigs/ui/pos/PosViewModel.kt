package com.rgbpos.bigs.ui.pos

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rgbpos.bigs.data.api.ApiClient
import com.rgbpos.bigs.data.local.*
import com.rgbpos.bigs.data.model.*
import com.rgbpos.bigs.data.sync.NetworkMonitor
import com.rgbpos.bigs.data.sync.SyncManager
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class PosViewModel(app: Application) : AndroidViewModel(app) {

    private val context get() = getApplication<Application>()
    private val db get() = AppDatabase.get(context)
    private val json = Json { ignoreUnknownKeys = true }

    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var customers by mutableStateOf<List<Customer>>(emptyList()); private set

    val cart = mutableStateListOf<CartItem>()

    var selectedCategoryId by mutableStateOf(0); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var lastOrder by mutableStateOf<OrderResponse?>(null); private set
    var submitting by mutableStateOf(false); private set
    var isOnline by mutableStateOf(true); private set
    var pendingCount by mutableStateOf(0); private set

    init {
        // Load from local DB first (instant, works offline)
        loadFromLocal()
        // Then sync from server if online
        syncFromServer()
        // Observe network changes for auto-sync
        observeNetwork()
    }

    private fun loadFromLocal() {
        viewModelScope.launch {
            try {
                val localCats = db.categoryDao().getAll()
                val localProds = db.productDao().getAll()
                val localCusts = db.customerDao().getAll()
                pendingCount = db.pendingOrderDao().count()

                if (localCats.isNotEmpty()) {
                    categories = localCats.map { Category(it.id, it.name, it.status) }
                }
                if (localProds.isNotEmpty()) {
                    products = localProds.map {
                        Product(
                            id = it.id, categoryId = it.categoryId, name = it.name,
                            description = it.description, price = it.price, image = it.image,
                            imageUrl = it.imageUrl, categoryName = it.categoryName, status = it.status,
                        )
                    }
                }
                if (localCusts.isNotEmpty()) {
                    customers = localCusts.map {
                        Customer(it.id, it.name, it.phone, it.email, it.loyaltyPoints)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun syncFromServer() {
        viewModelScope.launch {
            loading = products.isEmpty() // Only show spinner if no cached data
            error = null
            isOnline = NetworkMonitor.isOnline(context)

            if (!isOnline) {
                loading = false
                if (products.isEmpty()) error = "Offline — no cached data"
                return@launch
            }

            try {
                // Sync pending orders first
                val synced = SyncManager.syncPendingOrders(context)
                if (synced > 0) pendingCount = db.pendingOrderDao().count()

                // Then pull fresh data
                SyncManager.syncDataFromServer(context)
                loadFromLocal()
            } catch (e: Exception) {
                if (products.isEmpty()) error = "Failed to load: ${e.localizedMessage}"
            } finally {
                loading = false
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            NetworkMonitor.observe(context).collect { online ->
                val wasOffline = !isOnline
                isOnline = online
                if (online && wasOffline) {
                    // Came back online — auto-sync
                    syncFromServer()
                }
            }
        }
    }

    fun selectCategory(id: Int) {
        selectedCategoryId = id
    }

    val filteredProducts: List<Product>
        get() = if (selectedCategoryId == 0) products
        else products.filter { it.categoryId == selectedCategoryId }

    fun addToCart(product: Product) {
        val idx = cart.indexOfFirst { it.productId == product.id }
        if (idx >= 0) {
            cart[idx] = cart[idx].copy(quantity = cart[idx].quantity + 1)
        } else {
            cart.add(
                CartItem(
                    productId = product.id,
                    name = product.name,
                    price = product.price,
                    quantity = 1,
                    categoryId = product.categoryId,
                    imageUrl = product.imageUrl,
                )
            )
        }
    }

    fun updateQuantity(productId: Int, qty: Int) {
        val idx = cart.indexOfFirst { it.productId == productId }
        if (idx >= 0) {
            if (qty <= 0) cart.removeAt(idx)
            else cart[idx] = cart[idx].copy(quantity = qty)
        }
    }

    fun removeFromCart(productId: Int) {
        cart.removeAll { it.productId == productId }
    }

    fun clearCart() {
        cart.clear()
    }

    val subtotal: Double get() = cart.sumOf { it.price * it.quantity }
    val itemCount: Int get() = cart.sumOf { it.quantity }

    fun completeOrder(
        orderType: String,
        seniorPwdDiscount: Boolean,
        onSuccess: (OrderResponse) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (cart.isEmpty()) {
            onError("Cart is empty")
            return
        }

        submitting = true
        viewModelScope.launch {
            val orderItems = cart.map { OrderItemRequest(it.productId, it.quantity) }
            val sub = subtotal
            val discountVal = if (seniorPwdDiscount) kotlin.math.round(sub * 0.20 * 100) / 100 else 0.0
            val tot = sub - discountVal
            val custName = "Walk-in"

            // Build receipt items snapshot from cart (for offline receipt)
            val receiptItems = cart.map {
                OrderItemDetail(
                    productId = it.productId,
                    productName = it.name,
                    quantity = it.quantity,
                    unitPrice = it.price,
                    subtotal = it.price * it.quantity,
                )
            }

            if (isOnline) {
                try {
                    val request = OrderRequest(
                        items = orderItems,
                        orderType = orderType,
                        discount = discountVal,
                        seniorPwdDiscount = seniorPwdDiscount,
                    )
                    val resp = ApiClient.service.createOrder(request)
                    if (resp.isSuccessful && resp.body() != null) {
                        val order = resp.body()!!
                        lastOrder = order
                        cart.clear()
                        onSuccess(order)
                    } else {
                        onError("Order failed: ${resp.code()}")
                    }
                } catch (_: Exception) {
                    // Network failed — save offline
                    saveOrderOffline(orderItems, null, orderType, discountVal, receiptItems, sub, tot, custName)
                    val offlineOrder = buildOfflineOrderResponse(orderType, custName, receiptItems, sub, discountVal, tot)
                    lastOrder = offlineOrder
                    cart.clear()
                    onSuccess(offlineOrder)
                }
            } else {
                // Offline — save locally
                saveOrderOffline(orderItems, null, orderType, discountVal, receiptItems, sub, tot, custName)
                val offlineOrder = buildOfflineOrderResponse(orderType, custName, receiptItems, sub, discountVal, tot)
                lastOrder = offlineOrder
                cart.clear()
                onSuccess(offlineOrder)
            }
            submitting = false
        }
    }

    private suspend fun saveOrderOffline(
        items: List<OrderItemRequest>,
        customerId: Int?,
        orderType: String,
        discount: Double,
        receiptItems: List<OrderItemDetail>,
        subtotal: Double,
        total: Double,
        customerName: String,
    ) {
        val itemsJson = json.encodeToString(ListSerializer(OrderItemRequest.serializer()), items)
        val receiptJson = json.encodeToString(ListSerializer(OrderItemDetail.serializer()), receiptItems)
        db.pendingOrderDao().insert(
            PendingOrderEntity(
                itemsJson = itemsJson,
                customerId = customerId,
                orderType = orderType,
                discount = discount,
                receiptJson = receiptJson,
                subtotal = subtotal,
                total = total,
                customerName = customerName,
            )
        )
        pendingCount = db.pendingOrderDao().count()
    }

    private fun buildOfflineOrderResponse(
        orderType: String,
        customerName: String,
        items: List<OrderItemDetail>,
        sub: Double,
        discount: Double,
        total: Double,
    ): OrderResponse {
        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        return OrderResponse(
            orderId = 0,
            orderNumber = "OFFLINE-${System.currentTimeMillis()}",
            orderType = orderType,
            customer = customerName,
            items = items,
            subtotal = sub,
            discount = discount,
            total = total,
            createdAt = ts,
        )
    }

    fun clearLastOrder() {
        lastOrder = null
    }
}
