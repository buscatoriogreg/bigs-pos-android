package com.rgbpos.bigs.data.api

import com.rgbpos.bigs.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("logout")
    suspend fun logout(): Response<Unit>

    @GET("categories")
    suspend fun getCategories(): Response<List<Category>>

    @GET("products")
    suspend fun getProducts(): Response<List<Product>>

    @GET("customers")
    suspend fun getCustomers(): Response<List<Customer>>

    @POST("orders")
    suspend fun createOrder(@Body request: OrderRequest): Response<OrderResponse>
}
