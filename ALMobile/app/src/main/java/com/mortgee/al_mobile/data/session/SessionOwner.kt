package com.mortgee.al_mobile.data.session

import com.mortgee.al_mobile.data.model.BackendRole
import kotlinx.coroutines.flow.StateFlow

/**
 * Sole authority for identity-changing operations, publication of session state,
 * guarded result publication, invalidation, and restoration.
 *
 * Exposes an immutable, read-only [StateFlow] of [SessionState].
 */
interface SessionOwner {
    /**
     * Read-only stream of current immutable session state.
     */
    val sessionState: StateFlow<SessionState>

    /**
     * Returns current active [SessionKey] if authenticated, null otherwise.
     */
    val currentKey: SessionKey?
        get() = sessionState.value.sessionKey

    /**
     * Begins a sign-in attempt and acquires ownership of pending work.
     * Transitions state to [SessionState.Establishing] if available.
     *
     * @return [SessionAttempt] ticket if successfully acquired, or null if busy/unavailable.
     */
    suspend fun startSignInAttempt(): SessionAttempt?

    /**
     * Begins a session restoration attempt.
     * Transitions state to [SessionState.Restoring] if available.
     *
     * @return [SessionAttempt] ticket if successfully acquired, or null if busy/unavailable.
     */
    suspend fun startRestoreAttempt(): SessionAttempt?

    /**
     * Atomically commits and publishes a verified session.
     *
     * Checks attempt validity, persists completed UID marker, and updates state to [SessionState.Authenticated].
     *
     * @param attempt The candidate attempt ticket.
     * @param uid The verified user ID.
     * @param email The candidate user email.
     * @param role The decoded backend role.
     * @param displayName Optional display name.
     * @return true if atomic commit succeeded, false if attempt was invalid, retired, or write failed.
     */
    suspend fun commitSession(
        attempt: SessionAttempt,
        uid: String,
        email: String,
        role: BackendRole,
        displayName: String? = null,
    ): Boolean

    /**
     * Immediately retires the current session key and invalidates state.
     * Transitions state to [SessionState.SignedOut] and clears completed session marker.
     *
     * @return true if invalidation and marker removal succeeded, false otherwise.
     */
    suspend fun invalidateSession(): Boolean

    /**
     * Performs complete user sign-out, removing durable marker and transitioning to [SessionState.SignedOut].
     *
     * @return true if sign-out and marker removal succeeded, false otherwise.
     */
    suspend fun signOut(): Boolean

    /**
     * Cancels a pending attempt if it matches the active attempt ID.
     * Transitions state back to [SessionState.SignedOut].
     *
     * @param attempt The attempt to cancel.
     */
    suspend fun cancelAttempt(attempt: SessionAttempt)
}
