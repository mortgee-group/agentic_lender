package com.mortgee.al_mobile.data.auth

import com.mortgee.al_mobile.data.model.SignInResult

/**
 * Contract wrapping Firebase Authentication SDK operations.
 *
 * Provides a clean seam for testing and decouples the repository/session layers
 * from direct Firebase SDK calls.
 */
interface AuthAdapter {
    /**
     * The UID of the currently signed-in SDK user, or null if no SDK user is signed in.
     */
    val currentUid: String?

    /**
     * Signs in with email and password credentials using the Firebase Authentication SDK.
     *
     * @param email User email credential.
     * @param password User password credential.
     * @return [AuthResult] containing candidate [AuthUser] on success or safe [SignInResult] on error.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun signIn(email: String, password: String): AuthResult

    /**
     * Obtains an ID token for the specified candidate UID.
     *
     * @param uid The expected candidate user ID.
     * @param forceRefresh Whether to force token refresh.
     * @return Safe [Result] containing bearer token string or failure if UID mismatches/user missing.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun getToken(uid: String, forceRefresh: Boolean = false): Result<String>

    /**
     * Performs SDK local sign-out.
     *
     * @return true if SDK sign-out completed successfully, false otherwise.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun signOut(): Boolean
}
