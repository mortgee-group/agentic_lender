package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.network.model.CatalogueItemDto
import com.mortgee.al_mobile.data.network.model.CatalogueListResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogueApiService {
    @GET("api/v1/properties")
    suspend fun getItems(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sort") sort: String? = "createdAt,desc",
        @Query("query") query: String? = null
    ): CatalogueListResponseDto

    @GET("api/v1/properties/{id}")
    suspend fun getItemDetail(
        @Path("id") id: String
    ): CatalogueItemDto
}
