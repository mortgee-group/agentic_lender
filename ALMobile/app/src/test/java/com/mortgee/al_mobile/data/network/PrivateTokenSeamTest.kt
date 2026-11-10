package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.auth.AuthAdapter
import com.mortgee.al_mobile.data.auth.AuthResult
import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.session.CompletedSessionStorage
import com.mortgee.al_mobile.data.session.DefaultSessionOwner
import com.mortgee.al_mobile.data.session.SessionKey
import com.mortgee.al_mobile.data.session.SessionOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrivateTokenSeamTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeStorage: FakeCompletedSessionStorage
    private lateinit var fakeAuthAdapter: FakeAuthAdapter
    private lateinit var sessionOwner: SessionOwner

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        fakeStorage = FakeCompletedSessionStorage()
        fakeAuthAdapter = FakeAuthAdapter()
        sessionOwner = DefaultSessionOwner(fakeStorage, fakeAuthAdapter)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getToken returns NoActiveSession failure when no session exists`() = runTest {
        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        val result = seam.getToken(entryKey = null)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is PrivateTokenException.NoActiveSession)
    }

    @Test
    fun `executePrivate returns NoActiveSession failure when no session exists`() = runTest {
        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        val result = seam.executePrivate<String>(entryKey = null) { token ->
            "data_with_$token"
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PrivateTokenException.NoActiveSession)
    }

    @Test
    fun `getToken and executePrivate succeed with valid session key and token`() = runTest {
        val key = establishSession("uid_123", "user@example.com")
        fakeAuthAdapter.tokenResult = Result.success("valid_sdk_token_456")

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        val tokenResult = seam.getToken(key)
        assertTrue(tokenResult.isSuccess)
        assertEquals("valid_sdk_token_456", tokenResult.getOrThrow())

        val execResult = seam.executePrivate(key) { token ->
            "fetched_with_$token"
        }
        assertTrue(execResult.isSuccess)
        assertEquals("fetched_with_valid_sdk_token_456", execResult.getOrThrow())
    }

    @Test
    fun `getToken returns TokenFetchFailed when AuthAdapter fails`() = runTest {
        val key = establishSession("uid_123", "user@example.com")
        fakeAuthAdapter.tokenResult = Result.failure(RuntimeException("Network error fetching token"))

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        val result = seam.getToken(key)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is PrivateTokenException.TokenFetchFailed)
        assertEquals("Network error fetching token", (exception as PrivateTokenException.TokenFetchFailed).cause.message)
    }

    @Test
    fun `getToken returns StaleSession when session is invalidated during token fetch`() = runTest {
        val key = establishSession("uid_123", "user@example.com")

        val tokenDeferred = CompletableDeferred<Result<String>>()
        fakeAuthAdapter.customTokenHandler = { _, _ -> tokenDeferred.await() }

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        val job = async { seam.getToken(key) }

        // Invalidate session while token fetch is in flight
        sessionOwner.invalidateSession()

        // Complete token fetch
        tokenDeferred.complete(Result.success("late_token"))

        val result = job.await()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is PrivateTokenException.StaleSession)
        val staleEx = exception as PrivateTokenException.StaleSession
        assertEquals(key, staleEx.capturedKey)
        assertNull(staleEx.currentKey)
    }

    @Test
    fun `executePrivate returns StaleSession when session is invalidated during request execution`() = runTest {
        val key = establishSession("uid_123", "user@example.com")
        fakeAuthAdapter.tokenResult = Result.success("sdk_token_789")

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        val requestBlockGate = CompletableDeferred<Unit>()

        val job = async {
            seam.executePrivate(key) { token ->
                requestBlockGate.await()
                "data_for_$token"
            }
        }

        // Invalidate session while request block is waiting
        sessionOwner.invalidateSession()

        // Resume request block execution
        requestBlockGate.complete(Unit)

        val result = job.await()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is PrivateTokenException.StaleSession)
    }

    @Test
    fun `isAllowedOrigin correctly validates origin and scheme`() {
        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com:8443/")

        assertTrue(seam.isAllowedOrigin("https://api.example.com:8443/v1/properties"))
        assertTrue(seam.isAllowedOrigin("https://API.EXAMPLE.COM:8443/v1/users/me"))

        // HTTP scheme forbidden
        assertFalse(seam.isAllowedOrigin("http://api.example.com:8443/v1/properties"))

        // Different host forbidden
        assertFalse(seam.isAllowedOrigin("https://untrusted-api.com:8443/v1/properties"))

        // Different port forbidden
        assertFalse(seam.isAllowedOrigin("https://api.example.com:443/v1/properties"))

        // Invalid URL
        assertFalse(seam.isAllowedOrigin("invalid-url-string"))
    }

    @Test
    fun `PrivateRequestInterceptor leaves public requests without authorization header`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, mockWebServer.url("/").toString())
        val interceptor = PrivateRequestInterceptor(seam)
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/public-data"))
            .get()
            .build()

        client.newCall(request).execute().close()

        val recordedRequest = mockWebServer.takeRequest()
        assertNull(recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `PrivateRequestInterceptor strips authorization header when target origin is non-HTTPS`() = runTest {
        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")
        val interceptor = PrivateRequestInterceptor(seam)

        // Mock HTTP request to non-HTTPS scheme
        val request = Request.Builder()
            .url("http://api.example.com/v1/private-data")
            .header("Authorization", "Bearer sensitive_token")
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        // Run interceptor with mock chain
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        // Send via client to a mock endpoint but interceptor will see http target
        // We test interceptor directly by calling client.newCall or testing interceptor chain
        val chainRequest = request.newBuilder().url(mockWebServer.url("/v1/private-data")).build()
        // Here mockWebServer.url is HTTP, so isAllowedOrigin returns false
        client.newCall(chainRequest).execute().close()

        val recordedRequest = mockWebServer.takeRequest()
        assertNull("Authorization header must be stripped when scheme is HTTP", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `PrivateRequestInterceptor strips authorization header on cross-origin redirect`() = runTest {
        val secondServer = MockWebServer()
        secondServer.start()

        try {
            val seam = DefaultPrivateTokenSeam(
                sessionOwner = sessionOwner,
                authAdapter = fakeAuthAdapter,
                baseUrlString = mockWebServer.url("/").toString()
            )
            val interceptor = PrivateRequestInterceptor(seam)
            val client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addNetworkInterceptor(interceptor)
                .build()

            val crossOriginUrl = secondServer.url("/redirect-destination").toString()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", crossOriginUrl)
            )
            secondServer.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"))

            val request = Request.Builder()
                .url(mockWebServer.url("/v1/private-endpoint"))
                .header("Authorization", "Bearer token_12345")
                .build()

            client.newCall(request).execute().close()

            val request1 = mockWebServer.takeRequest()
            assertEquals("Bearer token_12345", request1.getHeader("Authorization"))

            val request2 = secondServer.takeRequest()
            assertNull("Authorization header must be stripped on cross-origin redirect", request2.getHeader("Authorization"))
        } finally {
            secondServer.shutdown()
        }
    }

    @Test
    fun `executePrivate retries once on 401 with forced refresh token and invalidates session on second 401`() = runTest {
        val key = establishSession("uid_123", "user@example.com")
        fakeAuthAdapter.tokenResult = Result.success("token_v1")

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        var blockCalls = 0
        val execResult = seam.executePrivate(key, isMutation = false) { token ->
            blockCalls++
            if (blockCalls == 1) {
                assertEquals("token_v1", token)
                // Simulated 401 on first attempt
                throw createHttpException(401)
            } else {
                assertEquals("token_v1", token) // default fake returned token_v1
                // Second 401 on retried attempt
                throw createHttpException(401)
            }
        }

        assertEquals(2, blockCalls)
        assertTrue(execResult.isFailure)
        val exception = execResult.exceptionOrNull()
        assertTrue(exception is retrofit2.HttpException)
        assertEquals(401, (exception as retrofit2.HttpException).code())

        // Verify forced token refresh was requested on the second getToken call
        assertEquals(2, fakeAuthAdapter.tokenRequests.size)
        assertFalse(fakeAuthAdapter.tokenRequests[0].second) // initial unforced
        assertTrue(fakeAuthAdapter.tokenRequests[1].second) // forced refresh

        // Session must be invalidated after 2nd 401 on still-current session key
        assertNull(sessionOwner.currentKey)
    }

    @Test
    fun `executePrivate retries once on 401 and succeeds if retried block succeeds`() = runTest {
        val key = establishSession("uid_123", "user@example.com")

        fakeAuthAdapter.customTokenHandler = { _, forceRefresh ->
            if (forceRefresh) Result.success("refreshed_token_999") else Result.success("initial_token_111")
        }

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        var blockCalls = 0
        val execResult = seam.executePrivate(key, isMutation = false) { token ->
            blockCalls++
            if (blockCalls == 1) {
                assertEquals("initial_token_111", token)
                throw createHttpException(401)
            } else {
                assertEquals("refreshed_token_999", token)
                "recovered_data_payload"
            }
        }

        assertEquals(2, blockCalls)
        assertTrue(execResult.isSuccess)
        assertEquals("recovered_data_payload", execResult.getOrThrow())

        // Session must remain active
        assertEquals(key, sessionOwner.currentKey)
    }

    @Test
    fun `executePrivate returns 403 failure without forced refresh retry or session invalidation`() = runTest {
        val key = establishSession("uid_123", "user@example.com")
        fakeAuthAdapter.tokenResult = Result.success("valid_token")

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        var blockCalls = 0
        val execResult = seam.executePrivate(key, isMutation = false) { _ ->
            blockCalls++
            throw createHttpException(403)
        }

        assertEquals(1, blockCalls)
        assertTrue(execResult.isFailure)
        val exception = execResult.exceptionOrNull()
        assertTrue(exception is retrofit2.HttpException)
        assertEquals(403, (exception as retrofit2.HttpException).code())

        // Forced refresh should NOT occur for 403
        assertEquals(1, fakeAuthAdapter.tokenRequests.size)
        assertFalse(fakeAuthAdapter.tokenRequests[0].second)

        // Session must remain active on 403 permission error
        assertEquals(key, sessionOwner.currentKey)
    }

    @Test
    fun `executePrivate handles offline refresh by surfacing retryable network error without invalidating session`() = runTest {
        val key = establishSession("uid_123", "user@example.com")

        fakeAuthAdapter.customTokenHandler = { _, forceRefresh ->
            if (forceRefresh) {
                Result.failure(java.io.IOException("No internet connection"))
            } else {
                Result.success("initial_token")
            }
        }

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        var blockCalls = 0
        val execResult = seam.executePrivate(key, isMutation = false) { _ ->
            blockCalls++
            throw createHttpException(401)
        }

        assertEquals(1, blockCalls)
        assertTrue(execResult.isFailure)
        val exception = execResult.exceptionOrNull()
        assertTrue(exception is PrivateTokenException.TokenFetchFailed)

        // Session must NOT be invalidated on offline/connectivity failure during refresh
        assertEquals(key, sessionOwner.currentKey)
    }

    @Test
    fun `executePrivate on mutation does not auto replay on 401 and invalidates active session`() = runTest {
        val key = establishSession("uid_123", "user@example.com")
        fakeAuthAdapter.tokenResult = Result.success("valid_token")

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        var blockCalls = 0
        val execResult = seam.executePrivate(key, isMutation = true) { _ ->
            blockCalls++
            throw createHttpException(401)
        }

        // Mutations must not replay
        assertEquals(1, blockCalls)
        assertTrue(execResult.isFailure)
        val exception = execResult.exceptionOrNull()
        assertTrue(exception is retrofit2.HttpException)
        assertEquals(401, (exception as retrofit2.HttpException).code())

        // Active session must be invalidated on mutation 401
        assertNull(sessionOwner.currentKey)
    }

    @Test
    fun `executePrivate returns StaleSession and does not invalidate Session B when Session A response finishes after B signs in`() = runTest {
        val keyA = establishSession("uid_user_A", "userA@example.com")
        fakeAuthAdapter.tokenResult = Result.success("token_A")

        val seam = DefaultPrivateTokenSeam(sessionOwner, fakeAuthAdapter, "https://api.example.com/")

        val blockGate = CompletableDeferred<Unit>()

        val job = async {
            seam.executePrivate(keyA, isMutation = false) { _ ->
                blockGate.await()
                throw createHttpException(401)
            }
        }

        // Session B signs in while Session A's request is in flight
        sessionOwner.invalidateSession()
        val keyB = establishSession("uid_user_B", "userB@example.com")

        // Unblock Session A's response execution
        blockGate.complete(Unit)

        val result = job.await()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is PrivateTokenException.StaleSession)

        val staleEx = exception as PrivateTokenException.StaleSession
        assertEquals(keyA, staleEx.capturedKey)
        assertEquals(keyB, staleEx.currentKey)

        // Session B must NOT be invalidated by Session A's late response
        assertEquals(keyB, sessionOwner.currentKey)
    }

    private fun createHttpException(code: Int): retrofit2.HttpException {
        val responseBody = "{\"error\":\"HTTP $code\"}".toResponseBody("application/json".toMediaTypeOrNull())
        return retrofit2.HttpException(retrofit2.Response.error<Any>(code, responseBody))
    }

    private suspend fun establishSession(uid: String, email: String): SessionKey {
        val attempt = sessionOwner.startSignInAttempt()!!
        sessionOwner.commitSession(
            attempt = attempt,
            uid = uid,
            email = email,
            role = BackendRole.BORROWER
        )
        return sessionOwner.currentKey!!
    }

    private class FakeCompletedSessionStorage : CompletedSessionStorage {
        var storedUid: String? = null
        override suspend fun saveCompletedUid(uid: String): Boolean {
            storedUid = uid
            return true
        }
        override suspend fun getCompletedUid(): String? = storedUid
        override suspend fun clearCompletedUid(): Boolean {
            storedUid = null
            return true
        }
    }

    private class FakeAuthAdapter : AuthAdapter {
        var tokenResult: Result<String> = Result.success("default_test_token")
        var customTokenHandler: (suspend (uid: String, forceRefresh: Boolean) -> Result<String>)? = null
        val tokenRequests = mutableListOf<Pair<String, Boolean>>()

        override suspend fun signIn(email: String, password: String): AuthResult {
            return AuthResult.Failure(com.mortgee.al_mobile.data.model.SignInResult.GenericFailure)
        }

        override suspend fun getToken(uid: String, forceRefresh: Boolean): Result<String> {
            tokenRequests.add(uid to forceRefresh)
            val handler = customTokenHandler
            return if (handler != null) {
                handler(uid, forceRefresh)
            } else {
                tokenResult
            }
        }

        override suspend fun signOut(): Boolean = true
    }
}
