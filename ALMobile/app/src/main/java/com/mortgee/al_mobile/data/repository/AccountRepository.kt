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

    /**
     * Registers a new Borrower account with Firebase Authentication and creates the
     * corresponding AL-Core Borrower profile via `POST /users/me`.
     *
     * Prepares the UID partition and commits the authenticated session through [com.mortgee.al_mobile.data.session.SessionOwner]
     * upon verifying that the profile UID matches the candidate and `decodeRole() == BackendRole.BORROWER`.
     *
     * @param email Candidate user's email address (sent to Firebase Auth).
     * @param password Candidate user's raw password (sent unchanged to Firebase Auth; never logged or persisted).
     * @param displayName Candidate user's display name (sent to AL-Core in `POST /users/me`).
     * @return A safe [SignInResult] outcome free of credentials, tokens, or raw exceptions.
     * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): SignInResult
}
