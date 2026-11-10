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
 */
@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "uid") val uid: String,
    @Json(name = "email") val email: String,
    @Json(name = "role") val role: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
) {
    /**
     * Decodes the raw wire [role] string into a supported [BackendRole].
     * Returns null if role is missing, blank, or an unsupported value.
     */
    fun decodeRole(): BackendRole? = BackendRole.fromValue(role)
}
