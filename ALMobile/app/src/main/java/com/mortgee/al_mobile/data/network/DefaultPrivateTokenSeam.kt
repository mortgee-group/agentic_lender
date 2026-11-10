package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionOwner
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [PrivateTokenSeam].
 *
 * Coordinates session key capture, token acquisition via [AuthAdapter], post-acquisition
 * and post-execution session key re-checks, and origin validation for private requests.
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
        block: suspend (token: String) -> T
    ): Result<T> {
        val key = entryKey ?: sessionOwner.currentKey
        if (key == null) {
            return Result.failure(PrivateTokenException.NoActiveSession())
        }

        val tokenResult = getToken(key)
        if (tokenResult.isFailure) {
            return Result.failure(tokenResult.exceptionOrNull()!!)
        }

        val token = tokenResult.getOrThrow()

        val result = try {
            block(token)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return Result.failure(e)
        }

        // Recheck full session key after request execution to guard completion, errors, and cache writes
        val postExecutionKey = sessionOwner.currentKey
        if (postExecutionKey != key) {
            return Result.failure(PrivateTokenException.StaleSession(capturedKey = key, currentKey = postExecutionKey))
        }

        return Result.success(result)
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
}
