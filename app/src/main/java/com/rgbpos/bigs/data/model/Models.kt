package com.rgbpos.bigs.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val user: User)

@Serializable
data class User(
    val id: Int,
    val username: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    val status: String,
)

@Serializable
data class Category(val id: Int, val name: String, val status: String)

@Serializable
data class Product(
    val id: Int,
    @SerialName("category_id") val categoryId: Int,
    val name: String,
    val description: String? = null,
    val price: Double,
    val image: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val status: String = "active",
)

@Serializable
data class Customer(
    val id: Int,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("loyalty_points") val loyaltyPoints: Int = 0,
)

@Serializable
data class OrderRequest(
    val items: List<OrderItemRequest>,
    @SerialName("customer_id") val customerId: Int? = null,
    @SerialName("order_type") val orderType: String = "dine-in",
    val discount: Double = 0.0,
    @SerialName("senior_pwd_discount") val seniorPwdDiscount: Boolean = false,
)

@Serializable
data class OrderItemRequest(val id: Int, val quantity: Int)

@Serializable
data class OrderResponse(
    @SerialName("order_id") val orderId: Int,
    @SerialName("order_number") val orderNumber: String,
    @SerialName("order_type") val orderType: String,
    val customer: String,
    val items: List<OrderItemDetail>,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class OrderItemDetail(
    @SerialName("product_id") val productId: Int,
    @SerialName("product_name") val productName: String? = null,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: Double,
    val subtotal: Double,
    @SerialName("order_id") val orderId: Int? = null,
)

// Local cart item (not serialized over network)
data class CartItem(
    val productId: Int,
    val name: String,
    val price: Double,
    val quantity: Int,
    val categoryId: Int,
    val imageUrl: String? = null,
)

@Serializable
data class ErrorResponse(val error: String)
