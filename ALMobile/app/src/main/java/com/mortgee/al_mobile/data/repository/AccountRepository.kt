package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.SignInResult

/**
 * Repository interface defining account sign-in operations.
 */
interface AccountRepository {
    /**
     * Attempts sign-in using the provided credentials.
     *
     * Returns a safe [SignInResult] indicating the outcome.
     * Propagates [kotlinx.coroutines.CancellationException] if cancelled.
     */
    suspend fun signIn(email: String, password: String): SignInResult

    /**
     * Attempts session restoration for a process using stored completed session marker.
     *
     * Runs restore once per process through the same gate and validation path as sign-in.
     *
     * Returns a safe [SignInResult] indicating the outcome.
     * Propagates [kotlinx.coroutines.CancellationException] if cancelled.
     */
    suspend fun restoreSession(): SignInResult
}
