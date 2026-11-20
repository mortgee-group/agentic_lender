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
     * corresponding AL-Core Borrower profile via `POST /users/me` with fixed `role = "borrower"`.
     *
     * Prepares the UID partition and commits the authenticated session through [com.mortgee.al_mobile.data.session.SessionOwner]
     * upon verifying that the profile UID matches the candidate and `decodeRole() == BackendRole.BORROWER`.
     *
     * @param email Candidate user's email address (sent to Firebase Auth).
     * @param password Candidate user's raw password (sent unchanged to Firebase Auth; never logged or persisted).
     * @param firstName Candidate user's first name (required, length 1–30, sent to AL-Core in `POST /users/me`).
     * @param lastName Candidate user's last name (required, length 1–50, sent to AL-Core in `POST /users/me`).
     * @param phoneNumber Candidate user's optional phone number (max length 20, or null/blank, sent to AL-Core in `POST /users/me`).
     * @return A safe [SignInResult] outcome free of credentials, tokens, or raw exceptions.
     * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled.
     */
    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phoneNumber: String? = null,
    ): SignInResult

    /**
     * Explicitly completes a previously started Borrower registration where the Firebase account
     * already exists (e.g., after an interrupted response, connectivity drop, or correctable profile error).
     *
     * Authenticates the existing account once via [com.mortgee.al_mobile.data.auth.AuthAdapter.signIn] and inspects
     * `GET /users/me`:
     * - If `200 OK` returns a valid matching [com.mortgee.al_mobile.data.model.BackendRole.BORROWER] profile,
     *   commits the session with zero writes.
     * - If `200 OK` returns a missing, unknown, or non-Borrower role, rejects modification and returns
     *   [SignInResult.UnsupportedRole] with zero profile writes.
     * - If a confirmed missing-profile `404 Not Found` (`detail: "User profile not found"`) is returned and
     *   the profile fields ([firstName], [lastName], [phoneNumber]) pass validation, sends one `POST /users/me`
     *   with fixed `role = "borrower"` and commits on a valid Borrower profile response.
     * - Any other read failure ends the attempt without creating or overwriting a profile.
     *
     * @param email Existing candidate user's email address.
     * @param password Existing candidate user's raw password (sent unchanged; never logged or persisted).
     * @param firstName First name to use if the profile needs to be created on `404`.
     * @param lastName Last name to use if the profile needs to be created on `404`.
     * @param phoneNumber Optional phone number to use if the profile needs to be created on `404`.
     * @return A safe [SignInResult] outcome free of credentials, tokens, or raw exceptions.
     * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled.
     */
    suspend fun completeRegistration(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phoneNumber: String? = null,
    ): SignInResult
}
