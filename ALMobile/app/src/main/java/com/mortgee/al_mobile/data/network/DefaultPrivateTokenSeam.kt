package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionOwner
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [PrivateTokenSeam].
 *
 * Coordinates session key capture, token acquisition via [AuthAdapter], post-acquisition
 * and post-execution session key re-checks, origin validation for private requests,
 * and bounded 401 authentication recovery.
 */
@Singleton
class DefaultPrivateTokenSeam @Inject constructor(
    private val sessionOwner: SessionOwner,
    private val authAdapter: AuthAdapter,
    baseUrlString: String,
) : PrivateTokenSeam {

    private val allowedBaseUrl: HttpUrl? = baseUrlString.toHttpUrlOrNull()

    override suspend fun getToken(entryKey: SessionKey?, forceRefresh: Boolean): Result<String> {
        val key = entryKey ?: sessionOwner.currentKey
        if (key == null) {
            return Result.failure(PrivateTokenException.NoActiveSession())
        }

        val tokenResult = try {
            authAdapter.getToken(key.uid, forceRefresh)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(PrivateTokenException.TokenFetchFailed(e))
        }

        if (tokenResult.isFailure) {
            val cause = tokenResult.exceptionOrNull() ?: Exception("Unknown token fetch error")
            return Result.failure(PrivateTokenException.TokenFetchFailed(cause))
        }

        val token = tokenResult.getOrThrow()

        // Recheck full session key at the dispatch boundary
        val currentKey = sessionOwner.currentKey
        if (currentKey != key) {
            return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = currentKey))
        }

        return Result.success(token)
    }

    override suspend fun <T> executePrivate(
        entryKey: SessionKey?,
        isMutation: Boolean,
        block: suspend (token: String) -> T
    ): Result<T> {
        val key = entryKey ?: sessionOwner.currentKey
        if (key == null) {
            return Result.failure(PrivateTokenException.NoActiveSession())
        }

        // 1. Initial token acquisition (unforced)
        val tokenResult = getToken(key, forceRefresh = false)
        if (tokenResult.isFailure) {
            val cause = tokenResult.exceptionOrNull()!!
            if (cause is PrivateTokenException.StaleSession) {
                return Result.failure(cause)
            }
            if (isDefinitivelyInvalidUser(cause)) {
                invalidateIfCurrentKey(key)
            }
            return Result.failure(cause)
        }

        val token = tokenResult.getOrThrow()

        // 2. First execution attempt
        val firstAttemptResult = try {
            Result.success(block(token))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }

        if (firstAttemptResult.isSuccess) {
            val res = firstAttemptResult.getOrThrow()
            val postExecutionKey = sessionOwner.currentKey
            if (postExecutionKey != key) {
                return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = postExecutionKey))
            }
            return Result.success(res)
        }

        val firstException = firstAttemptResult.exceptionOrNull()!!

        // Check if error is HTTP 401
        if (!isHttp401(firstException)) {
            val postKey = sessionOwner.currentKey
            if (postKey != key) {
                return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = postKey))
            }
            // 403 or other non-401 error: return failure to caller without retry or invalidation
            return Result.failure(firstException)
        }

        // First attempt failed with 401
        if (isMutation) {
            // Mutations receive no automatic replay in this slice
            val currentKey = sessionOwner.currentKey
            if (currentKey != key) {
                return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = currentKey))
            }
            // Invalidate identity through SessionOwner only if key is still current
            sessionOwner.invalidateSession()
            return Result.failure(firstException)
        }

        // Replayable read with 401: recheck original session key before refresh
        val preRefreshKey = sessionOwner.currentKey
        if (preRefreshKey != key) {
            return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = preRefreshKey))
        }

        // Trigger ONE forced token refresh
        val refreshTokenResult = getToken(key, forceRefresh = true)
        if (refreshTokenResult.isFailure) {
            val refreshCause = refreshTokenResult.exceptionOrNull()!!
            if (refreshCause is PrivateTokenException.StaleSession) {
                return Result.failure(refreshCause)
            }
            if (isDefinitivelyInvalidUser(refreshCause)) {
                invalidateIfCurrentKey(key)
            }
            // Connectivity / network failure during refresh surfaces retryable network error without invalidating session
            return Result.failure(refreshCause)
        }

        val refreshedToken = refreshTokenResult.getOrThrow()

        // Recheck original session key before retry
        val preRetryKey = sessionOwner.currentKey
        if (preRetryKey != key) {
            return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = preRetryKey))
        }

        // Single retry attempt with refreshed token
        val retryAttemptResult = try {
            Result.success(block(refreshedToken))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }

        val postRetryKey = sessionOwner.currentKey
        if (postRetryKey != key) {
            return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = postRetryKey))
        }

        if (retryAttemptResult.isSuccess) {
            return Result.success(retryAttemptResult.getOrThrow())
        }

        val retryException = retryAttemptResult.exceptionOrNull()!!

        // If retried request returned a second 401 (refreshed token definitively rejected),
        // invalidate identity through SessionOwner (since postRetryKey == key)
        if (isHttp401(retryException)) {
            sessionOwner.invalidateSession()
        }

        return Result.failure(retryException)
    }

    override fun isAllowedOrigin(url: HttpUrl): Boolean {
        val allowed = allowedBaseUrl ?: return false
        if (allowed.isHttps && !url.isHttps) return false
        return url.host.equals(allowed.host, ignoreCase = true) && url.port == allowed.port
    }

    override fun isAllowedOrigin(urlString: String): Boolean {
        val parsed = urlString.toHttpUrlOrNull() ?: return false
        return isAllowedOrigin(parsed)
    }

    private suspend fun invalidateIfCurrentKey(key: SessionKey) {
        if (sessionOwner.currentKey == key) {
            sessionOwner.invalidateSession()
        }
    }

    private fun isHttp401(throwable: Throwable): Boolean {
        if (throwable is HttpException && throwable.code() == 401) return true
        val cause = throwable.cause
        if (cause != null && cause is HttpException && cause.code() == 401) return true
        return false
    }

    private fun isDefinitivelyInvalidUser(throwable: Throwable): Boolean {
        val rootCause = if (throwable is PrivateTokenException.TokenFetchFailed) throwable.cause else throwable
        if (isConnectivityError(rootCause)) return false
        val message = rootCause.message?.lowercase() ?: ""
        return message.contains("invalid user") ||
                message.contains("user missing") ||
                message.contains("uid mismatch") ||
                message.contains("user_not_found") ||
                message.contains("user not found") ||
                message.contains("user disabled")
    }

    private fun isConnectivityError(throwable: Throwable): Boolean {
        if (throwable is IOException) return true
        val message = throwable.message?.lowercase() ?: ""
        return message.contains("network") || message.contains("connection") || message.contains("timeout") || message.contains("unreachable")
    }
}
