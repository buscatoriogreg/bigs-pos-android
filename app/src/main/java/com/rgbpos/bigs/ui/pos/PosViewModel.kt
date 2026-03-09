package com.rgbpos.bigs.ui.pos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbpos.bigs.data.api.ApiClient
import com.rgbpos.bigs.data.model.*
import kotlinx.coroutines.launch

class PosViewModel : ViewModel() {

    var categories by mutableStateOf<List<Category>>(emptyList()); private set
    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var customers by mutableStateOf<List<Customer>>(emptyList()); private set

    val cart = mutableStateListOf<CartItem>()

    var selectedCategoryId by mutableStateOf(0); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var lastOrder by mutableStateOf<OrderResponse?>(null); private set
    var submitting by mutableStateOf(false); private set

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val catResp = ApiClient.service.getCategories()
                if (catResp.isSuccessful) categories = catResp.body() ?: emptyList()

                val prodResp = ApiClient.service.getProducts()
                if (prodResp.isSuccessful) products = prodResp.body() ?: emptyList()

                val custResp = ApiClient.service.getCustomers()
                if (custResp.isSuccessful) customers = custResp.body() ?: emptyList()
            } catch (e: Exception) {
                error = "Failed to load data: ${e.localizedMessage}"
            } finally {
                loading = false
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
        customerId: Int?,
        orderType: String,
        discount: Double,
        onSuccess: (OrderResponse) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (cart.isEmpty()) {
            onError("Cart is empty")
            return
        }

        submitting = true
        viewModelScope.launch {
            try {
                val request = OrderRequest(
                    items = cart.map { OrderItemRequest(it.productId, it.quantity) },
                    customerId = customerId,
                    orderType = orderType,
                    discount = discount,
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
            } catch (e: Exception) {
                onError("Connection error: ${e.localizedMessage}")
            } finally {
                submitting = false
            }
        }
    }

    fun clearLastOrder() {
        lastOrder = null
    }
}
