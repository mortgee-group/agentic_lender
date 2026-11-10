package com.mortgee.al_mobile.data.session

import com.mortgee.al_mobile.data.model.BackendRole

/**
 * Immutable state model representing the current application session lifecycle.
 *
 * [SignedOut], [Establishing], and [Restoring] states are closed to private content.
 * [Authenticated] state is open to private content.
 */
sealed interface SessionState {
    /**
     * No active session. Closed to private content.
     */
    data object SignedOut : SessionState

    /**
     * Sign-in attempt in progress. Closed to private content.
     *
     * @property attemptId Unique identifier for the pending sign-in attempt.
     */
    data class Establishing(val attemptId: String) : SessionState

    /**
     * Session restoration attempt in progress. Closed to private content.
     *
     * @property attemptId Unique identifier for the pending restore attempt.
     */
    data class Restoring(val attemptId: String) : SessionState

    /**
     * Verified and authenticated session. Open to private content.
     *
     * @property key Process-local unique session key.
     * @property uid Verified backend user ID matching the profile.
     * @property email User's email address.
     * @property role Supported backend role (borrower, broker, underwriter).
     * @property displayName Optional user display name.
     */
    data class Authenticated(
        val key: SessionKey,
        val uid: String,
        val email: String,
        val role: BackendRole,
        val displayName: String? = null,
    ) : SessionState

    /**
     * Returns true if and only if the current state is [Authenticated].
     */
    val isAuthenticated: Boolean
        get() = this is Authenticated

    /**
     * Returns the current [SessionKey] if authenticated, or null otherwise.
     */
    val sessionKey: SessionKey?
        get() = (this as? Authenticated)?.key

    /**
     * Returns the verified UID if authenticated, or null otherwise.
     */
    val currentUid: String?
        get() = (this as? Authenticated)?.uid
}
