package com.rgbpos.bigs.data.local

import androidx.room.*

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val status: String,
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "category_id") val categoryId: Int,
    val name: String,
    val description: String?,
    val price: Double,
    val image: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    val status: String,
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val phone: String?,
    val email: String?,
    @ColumnInfo(name = "loyalty_points") val loyaltyPoints: Int,
)

@Entity(tableName = "pending_orders")
data class PendingOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "items_json") val itemsJson: String, // JSON of OrderItemRequest list
    @ColumnInfo(name = "customer_id") val customerId: Int?,
    @ColumnInfo(name = "order_type") val orderType: String,
    val discount: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    // Snapshot for offline receipt
    @ColumnInfo(name = "receipt_json") val receiptJson: String, // JSON of items with names/prices for receipt
    val subtotal: Double,
    val total: Double,
    @ColumnInfo(name = "customer_name") val customerName: String,
)
