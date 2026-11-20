package com.mortgee.al_mobile.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data transfer object for the profile creation request body (POST /users/me).
 *
 * Maps to the backend `UserProfileCreate` contract:
 * @property firstName Required user first name (length 1–30) mapped to "first_name".
 * @property lastName Required user last name (length 1–50) mapped to "last_name".
 * @property phoneNumber Optional user phone number (max length 20, or null) mapped to "phone_number".
 * @property role Fixed lowercase wire string for the target user role ("borrower") mapped to "role".
 */
@JsonClass(generateAdapter = true)
data class CreateProfileRequest(
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "phone_number") val phoneNumber: String? = null,
    @Json(name = "role") val role: String = BORROWER_WIRE_ROLE,
) {
    companion object {
        const val BORROWER_WIRE_ROLE = "borrower"

        /**
         * Factory method constructing a Borrower profile creation request with fixed `role = "borrower"`
         * and converting blank [phoneNumber] inputs to `null`.
         */
        fun forBorrower(
            firstName: String,
            lastName: String,
            phoneNumber: String? = null,
        ): CreateProfileRequest = CreateProfileRequest(
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
            role = BORROWER_WIRE_ROLE,
        )
    }
}
