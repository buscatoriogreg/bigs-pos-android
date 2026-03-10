package com.rgbpos.bigs.data.local

import androidx.room.*

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE status = 'active' ORDER BY name")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        deleteAll()
        insertAll(categories)
    }
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE status = 'active' ORDER BY name")
    suspend fun getAll(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(products: List<ProductEntity>) {
        deleteAll()
        insertAll(products)
    }
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name")
    suspend fun getAll(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(customers: List<CustomerEntity>) {
        deleteAll()
        insertAll(customers)
    }
}

@Dao
interface PendingOrderDao {
    @Query("SELECT * FROM pending_orders ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingOrderEntity>

    @Insert
    suspend fun insert(order: PendingOrderEntity): Long

    @Delete
    suspend fun delete(order: PendingOrderEntity)

    @Query("SELECT COUNT(*) FROM pending_orders")
    suspend fun count(): Int
}
