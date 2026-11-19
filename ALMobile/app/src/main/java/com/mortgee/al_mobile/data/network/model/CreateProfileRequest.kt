package com.mortgee.al_mobile.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data transfer object for the profile creation request body (POST /users/me).
 *
 * Maps to the backend `UserProfileCreate` contract:
 * @property role Wire string for the target user role (e.g., "borrower").
 * @property firstName User first name mapped to "first_name".
 * @property lastName User last name mapped to "last_name".
 * @property phoneNumber Optional user phone number mapped to "phone_number".
 * @property displayName Optional user display name mapped to "display_name".
 */
@JsonClass(generateAdapter = true)
data class CreateProfileRequest(
    @Json(name = "role") val role: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "phone_number") val phoneNumber: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
)
