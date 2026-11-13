package com.mortgee.al_mobile.data.model

data class PropertyListing(
    val id: String,
    val title: String,
    val summary: String? = null,
    val description: String? = null,
    val price: Double,
    val currency: String,
    val imageUrl: String? = null,
    val category: String? = null,
    val isAvailable: Boolean = true,
    val location: String? = null,
    val propertySizeSqm: Double? = null,
    val bedrooms: Int? = null,
    val propertyType: String? = null,
    val berRating: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
