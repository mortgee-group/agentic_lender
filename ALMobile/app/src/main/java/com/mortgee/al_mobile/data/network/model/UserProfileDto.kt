package com.mortgee.al_mobile.data.network.model

import com.mortgee.al_mobile.data.model.BackendRole
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data transfer object for the user profile response from GET /users/me.
 *
 * @property uid Unique backend user identifier matching candidate Firebase Auth UID.
 * @property email User email address.
 * @property role Raw wire role string (e.g., "borrower", "broker", "underwriter").
 * @property displayName Optional user display name mapped from "display_name".
 * @property firstName Optional user first name mapped from "first_name".
 * @property lastName Optional user last name mapped from "last_name".
 * @property phoneNumber Optional user phone number mapped from "phone_number".
 * @property createdAt Optional ISO creation timestamp mapped from "createdAt".
 * @property updatedAt Optional ISO update timestamp mapped from "updatedAt".
 */
@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "uid") val uid: String,
    @Json(name = "email") val email: String,
    @Json(name = "role") val role: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "phone_number") val phoneNumber: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null,
) {
    /**
     * Decodes the raw wire [role] string into a supported [BackendRole].
     * Returns null if role is missing, blank, or an unsupported value.
     */
    fun decodeRole(): BackendRole? = BackendRole.fromValue(role)
}
