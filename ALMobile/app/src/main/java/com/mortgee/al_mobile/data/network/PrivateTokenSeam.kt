package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.session.SessionKey
import okhttp3.HttpUrl

/**
 * Exceptions thrown or returned when private token acquisition or request execution fails
 * due to session state, token retrieval issues, or security violations.
 */
sealed class PrivateTokenException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * Returned when a private request is attempted without an active session key.
     */
    class NoActiveSession : PrivateTokenException("No active session key provided for private request")

    /**
     * Returned when the session key becomes stale (session invalidated, signed out, or generation changed)
     * during token acquisition or after request execution.
     */
    data class StaleSession(
        val capturedKey: SessionKey,
        val currentKey: SessionKey?,
    ) : PrivateTokenException("Session key is stale. Captured: $capturedKey, current: $currentKey")

    /**
     * Returned when token retrieval from AuthAdapter fails.
     */
    data class TokenFetchFailed(override val cause: Throwable) :
        PrivateTokenException("Failed to acquire SDK token", cause)
}

/**
 * Seam for obtaining SDK-managed tokens for private requests, enforcing session key validity,
 * and checking target origin security rules.
 */
interface PrivateTokenSeam {
    /**
     * Captures the entry [SessionKey] (or current session key if omitted) and acquires an SDK-managed token for that UID.
     *
     * Validates that:
     * 1. A session key is present (non-null).
     * 2. Token retrieval via AuthAdapter for [SessionKey.uid] succeeds.
     * 3. The session key in SessionOwner remains equal to entryKey after token acquisition.
     *
     * @param entryKey The session key captured at the request entry point.
     * @param forceRefresh Whether to force token refresh.
     * @return [Result.success] containing the bearer token, or [Result.failure] with [PrivateTokenException].
     */
    suspend fun getToken(entryKey: SessionKey?, forceRefresh: Boolean = false): Result<String>

    /**
     * Evaluates a private request block using a validated SDK token.
     *
     * Re-checks session key validity both before dispatching and after the block completes
     * to prevent stale result publication or cache writes.
     *
     * @param entryKey The session key captured at request entry point.
     * @param block The suspendable request execution block receiving the bearer token.
     * @return [Result.success] with result of block, or [Result.failure] if session is stale/missing or block failed.
     */
    suspend fun <T> executePrivate(
        entryKey: SessionKey?,
        block: suspend (token: String) -> T
    ): Result<T>

    /**
     * Returns true if the provided [HttpUrl] matches the configured AL-Core HTTPS origin.
     */
    fun isAllowedOrigin(url: HttpUrl): Boolean

    /**
     * Returns true if the provided URL string matches the configured AL-Core HTTPS origin.
     */
    fun isAllowedOrigin(urlString: String): Boolean
}
