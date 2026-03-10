package com.rgbpos.bigs.data.sync

import android.content.Context
import android.util.Log
import com.rgbpos.bigs.data.api.ApiClient
import com.rgbpos.bigs.data.local.*
import com.rgbpos.bigs.data.model.OrderItemRequest
import com.rgbpos.bigs.data.model.OrderRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/**
 * Handles syncing between local Room DB and remote API.
 * - Downloads categories/products/customers from API into Room
 * - Uploads pending offline orders to the API
 */
object SyncManager {

    private val json = Json { ignoreUnknownKeys = true }
    private const val TAG = "SyncManager"

    /**
     * Pull latest data from API and save to Room.
     * Returns true if sync was successful.
     */
    suspend fun syncDataFromServer(context: Context): Boolean {
        val db = AppDatabase.get(context)
        return try {
            // Categories
            val catResp = ApiClient.service.getCategories()
            if (catResp.isSuccessful && catResp.body() != null) {
                db.categoryDao().replaceAll(catResp.body()!!.map {
                    CategoryEntity(it.id, it.name, it.status)
                })
            }

            // Products
            val prodResp = ApiClient.service.getProducts()
            if (prodResp.isSuccessful && prodResp.body() != null) {
                db.productDao().replaceAll(prodResp.body()!!.map {
                    ProductEntity(
                        id = it.id, categoryId = it.categoryId, name = it.name,
                        description = it.description, price = it.price, image = it.image,
                        imageUrl = it.imageUrl, categoryName = it.categoryName,
                        status = it.status,
                    )
                })
            }

            // Customers
            val custResp = ApiClient.service.getCustomers()
            if (custResp.isSuccessful && custResp.body() != null) {
                db.customerDao().replaceAll(custResp.body()!!.map {
                    CustomerEntity(it.id, it.name, it.phone, it.email, it.loyaltyPoints)
                })
            }

            Log.d(TAG, "Data sync completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Data sync failed: ${e.message}")
            false
        }
    }

    /**
     * Push any pending offline orders to the API.
     * Returns number of orders synced.
     */
    suspend fun syncPendingOrders(context: Context): Int {
        val db = AppDatabase.get(context)
        val pending = db.pendingOrderDao().getAll()
        if (pending.isEmpty()) return 0

        var synced = 0
        for (order in pending) {
            try {
                val items = json.decodeFromString(
                    ListSerializer(OrderItemRequest.serializer()),
                    order.itemsJson,
                )
                val request = OrderRequest(
                    items = items,
                    customerId = order.customerId,
                    orderType = order.orderType,
                    discount = order.discount,
                )
                val resp = ApiClient.service.createOrder(request)
                if (resp.isSuccessful) {
                    db.pendingOrderDao().delete(order)
                    synced++
                    Log.d(TAG, "Synced pending order #${order.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync order #${order.id}: ${e.message}")
                // Stop on first failure — server might be unreachable
                break
            }
        }
        return synced
    }
}
