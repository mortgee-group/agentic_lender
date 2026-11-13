package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.network.model.UserProfileDto
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Retrofit API service interface for candidate user profile lookup.
 *
 * Provides bootstrap access to user profile endpoint before an app session exists.
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
}
