package com.mortgee.al_mobile.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class LoanPropertyDto(
    val county: String,
    @Json(name = "floor_area") val floorArea: Double,
    val bedrooms: Int,
    val bathrooms: Int,
    @Json(name = "year_built") val yearBuilt: Int,
    @Json(name = "property_type") val propertyType: String,
    @Json(name = "ber_rating") val berRating: String,
)
