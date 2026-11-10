package com.mortgee.al_mobile.data.auth

/**
 * Represents a candidate authenticated SDK user returned by sign-in.
 * Token lookup is explicitly bound to this candidate user instance/UID.
 */
interface AuthUser {
    val uid: String
    val email: String?

    /**
     * Retrieves an ID token bound explicitly to this candidate user.
     *
     * @param forceRefresh Whether to force a token refresh from Firebase.
     * @return Safe [Result] containing the bearer token string or an error if invalid/mismatched.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): Result<String>
}
