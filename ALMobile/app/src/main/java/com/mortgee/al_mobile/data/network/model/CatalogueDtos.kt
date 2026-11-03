package com.mortgee.al_mobile.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CatalogueItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "price") val price: Double,
    @Json(name = "currency") val currency: String,
    @Json(name = "imageUrl") val imageUrl: String? = null,
    @Json(name = "category") val category: String? = null,
    @Json(name = "isAvailable") val isAvailable: Boolean = true,
    @Json(name = "location") val location: String? = null,
    @Json(name = "propertySizeSqm") val propertySizeSqm: Double? = null,
    @Json(name = "bedrooms") val bedrooms: Int? = null,
    @Json(name = "propertyType") val propertyType: String? = null,
    @Json(name = "berRating") val berRating: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CatalogueListResponseDto(
    @Json(name = "properties") val items: List<CatalogueItemDto> = emptyList(),
    @Json(name = "page") val page: Int = 1,
    @Json(name = "pageSize") val pageSize: Int = 20,
    @Json(name = "totalCount") val totalCount: Int = 0,
    @Json(name = "hasMore") val hasMore: Boolean = false
)
