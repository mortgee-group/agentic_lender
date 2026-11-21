package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.network.model.CreateProfileRequest
import com.mortgee.al_mobile.data.network.model.UserProfileDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit API service interface for candidate user profile creation and lookup.
 *
 * Provides bootstrap access to the user profile endpoint before an app session exists.
 */
interface ProfileApiService {
    /**
     * Reads candidate user profile from GET /users/me using an explicit candidate bearer token.
     *
     * @param bearerToken Authorization header value formatted as "Bearer <token>".
     * @return [UserProfileDto] containing wire fields uid, email, role, display_name, first_name, last_name, phone_number, createdAt, updatedAt.
     */
    @GET("users/me")
    suspend fun getProfile(
        @Header("Authorization") bearerToken: String
    ): UserProfileDto

    /**
     * Creates a candidate user profile via POST /users/me using an explicit candidate bearer token.
     *
     * @param bearerToken Authorization header value formatted as "Bearer <token>".
     * @param request [CreateProfileRequest] payload containing profile attributes (such as `display_name`).
     * @return [UserProfileDto] parsed from the `201 Created` response.
     */
    @POST("users/me")
    suspend fun createProfile(
        @Header("Authorization") bearerToken: String,
        @Body request: CreateProfileRequest
    ): UserProfileDto
}

