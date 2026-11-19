package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.model.BackendRole
import com.mortgee.al_mobile.data.network.model.CreateProfileRequest
import com.mortgee.al_mobile.di.NetworkModule
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException

class ProfileApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var profileApiService: ProfileApiService
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var allowedOriginSeam: FakePrivateTokenSeam

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        allowedOriginSeam = FakePrivateTokenSeam(mockWebServer.url("/"))
        val interceptor = NetworkModule.providePrivateRequestInterceptor(allowedOriginSeam)
        okHttpClient = NetworkModule.provideOkHttpClient(interceptor)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        profileApiService = ApiClient(retrofit).createService()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getProfile sends GET to users me with exact bearer header on approved origin`() = runTest {
        val jsonResponse = """
            {
                "uid": "firebase_uid_123",
                "email": "borrower@example.com",
                "display_name": "Example Borrower",
                "role": "borrower",
                "first_name": "John",
                "last_name": "Conner",
                "phone_number": "+353871234567",
                "createdAt": "2026-11-06T10:00:00Z",
                "updatedAt": "2026-11-06T10:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val token = "Bearer candidate-id-token-xyz"
        val profile = profileApiService.getProfile(token)

        assertEquals("firebase_uid_123", profile.uid)
        assertEquals("borrower@example.com", profile.email)
        assertEquals("borrower", profile.role)
        assertEquals("Example Borrower", profile.displayName)
        assertEquals("John", profile.firstName)
        assertEquals("Conner", profile.lastName)
        assertEquals("+353871234567", profile.phoneNumber)
        assertEquals("2026-11-06T10:00:00Z", profile.createdAt)
        assertEquals("2026-11-06T10:00:00Z", profile.updatedAt)
        assertEquals(BackendRole.BORROWER, profile.decodeRole())

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/users/me", request.path)
        assertEquals(token, request.getHeader("Authorization"))

        val authHeaders = request.headers.values("Authorization")
        assertEquals(1, authHeaders.size)

        assertEquals(mockWebServer.hostName, request.requestUrl?.host)
        assertEquals(mockWebServer.port, request.requestUrl?.port)
    }

    @Test
    fun `getProfile parses valid response without optional display_name`() = runTest {
        val jsonResponse = """
            {
                "uid": "user_def456",
                "email": "underwriter@example.com",
                "role": "underwriter"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val profile = profileApiService.getProfile("Bearer token-456")

        assertEquals("user_def456", profile.uid)
        assertEquals("underwriter@example.com", profile.email)
        assertEquals("underwriter", profile.role)
        assertNull(profile.displayName)
        assertEquals(BackendRole.UNDERWRITER, profile.decodeRole())
    }

    @Test
    fun `getProfile handles HTTP 401 Unauthorized response`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Unauthorized candidate token"}""")
        )

        try {
            profileApiService.getProfile("Bearer invalid-token")
            fail("Expected HttpException for 401 status code")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
    }

    @Test
    fun `getProfile handles HTTP 404 Not Found response`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error": "User profile not found"}""")
        )

        try {
            profileApiService.getProfile("Bearer missing-profile-token")
            fail("Expected HttpException for 404 status code")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
        }
    }

    @Test
    fun `getProfile prevents forwarding bearer token on cross-origin redirects`() = runTest {
        val secondServer = MockWebServer()
        secondServer.start()

        try {
            val crossOriginUrl = secondServer.url("/users/me").toString()
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", crossOriginUrl)
            )

            val jsonResponse = """
                {
                    "uid": "user_redirected",
                    "email": "redirected@example.com",
                    "role": "broker"
                }
            """.trimIndent()
            secondServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

            val token = "Bearer sensitive-candidate-token"
            val profile = profileApiService.getProfile(token)

            assertEquals("user_redirected", profile.uid)

            val request1 = mockWebServer.takeRequest()
            assertEquals("GET", request1.method)
            assertEquals("/users/me", request1.path)
            assertEquals(token, request1.getHeader("Authorization"))

            val request2 = secondServer.takeRequest()
            assertEquals("GET", request2.method)
            assertEquals("/users/me", request2.path)
            assertNull("Authorization header must be stripped on cross-origin redirect", request2.getHeader("Authorization"))
        } finally {
            secondServer.shutdown()
        }
    }

    @Test
    fun `createProfile sends one POST to users me with candidate token and exact display_name JSON and parses 201 as UserProfileDto`() = runTest {
        val jsonResponse = """
            {
                "uid": "firebase_uid_123",
                "email": "borrower@example.com",
                "role": "borrower",
                "display_name": "Test Borrower",
                "createdAt": "2026-11-06T10:00:00Z",
                "updatedAt": "2026-11-06T10:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody(jsonResponse))

        val token = "Bearer candidate-id-token-xyz"
        val requestDto = CreateProfileRequest(displayName = "Test Borrower")
        val profile = profileApiService.createProfile(token, requestDto)

        assertEquals("firebase_uid_123", profile.uid)
        assertEquals("borrower@example.com", profile.email)
        assertEquals("borrower", profile.role)
        assertEquals("Test Borrower", profile.displayName)
        assertEquals(BackendRole.BORROWER, profile.decodeRole())
        assertEquals(1, mockWebServer.requestCount)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/users/me", request.path)
        assertEquals(token, request.getHeader("Authorization"))
        assertEquals(1, request.headers.values("Authorization").size)
        assertEquals("""{"display_name":"Test Borrower"}""", request.body.readUtf8())
    }

    @Test
    fun `createProfile preserves HTTP 400, 401, 409, and 422 responses without automatic replay`() = runTest {
        val statusCodes = listOf(400, 401, 409, 422)
        for (expectedCode in statusCodes) {
            val requestCountBefore = mockWebServer.requestCount
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(expectedCode)
                    .setBody("""{"detail": "Error $expectedCode"}""")
            )

            try {
                profileApiService.createProfile(
                    "Bearer candidate-token-$expectedCode",
                    CreateProfileRequest(displayName = "Test Borrower")
                )
                fail("Expected HttpException for status code $expectedCode")
            } catch (e: HttpException) {
                assertEquals(expectedCode, e.code())
                assertEquals("""{"detail": "Error $expectedCode"}""", e.response()?.errorBody()?.string())
            }

            // Ensure exactly one request was sent and no automatic replay occurred
            assertEquals(requestCountBefore + 1, mockWebServer.requestCount)
            val recorded = mockWebServer.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/users/me", recorded.path)
        }
    }

    @Test
    fun `createProfile with rejected token returns 401 and does not replay`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Invalid or expired Firebase ID token"}""")
        )

        try {
            profileApiService.createProfile(
                "Bearer rejected-token",
                CreateProfileRequest(displayName = "Test Borrower")
            )
            fail("Expected HttpException 401 for rejected token")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }

        assertEquals(1, mockWebServer.requestCount)
        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer rejected-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `createProfile on interrupted response throws IOException and does not automatically replay mutation`() = runTest {
        assertFalse("OkHttpClient must disable retryOnConnectionFailure to prevent automatic POST replay", okHttpClient.retryOnConnectionFailure)

        // First response drops connection mid-response; second response would succeed if replayed
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"uid":"u1","email":"e@example.com","role":"borrower"}""")
        )

        try {
            profileApiService.createProfile(
                "Bearer candidate-token",
                CreateProfileRequest(displayName = "Test Borrower")
            )
            fail("Expected IOException on interrupted connection")
        } catch (_: IOException) {
            // Expected transport failure
        }

        // Verify only 1 request was attempted and the mutation was not replayed
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `createProfile enforces origin rule and strips Authorization header for untrusted origins or redirects`() = runTest {
        // 1. Direct request when origin is not allowed strips Authorization header
        allowedOriginSeam.allowOrigin = false
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Missing token"}""")
        )

        try {
            profileApiService.createProfile(
                "Bearer candidate-secret-token",
                CreateProfileRequest(displayName = "Test Borrower")
            )
            fail("Expected 401 when token is stripped for untrusted origin")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }

        val untrustedRequest = mockWebServer.takeRequest()
        assertNull("Authorization header must be stripped for untrusted origin", untrustedRequest.getHeader("Authorization"))

        // 2. Cross-origin redirect strips Authorization header on follow-up request
        allowedOriginSeam.allowOrigin = true
        val secondServer = MockWebServer()
        secondServer.start()
        try {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(307)
                    .setHeader("Location", secondServer.url("/users/me").toString())
            )
            secondServer.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"error": "Unauthorized on redirect target"}""")
            )

            try {
                profileApiService.createProfile(
                    "Bearer candidate-secret-token",
                    CreateProfileRequest(displayName = "Test Borrower")
                )
                fail("Expected 401 from redirect target")
            } catch (e: HttpException) {
                assertEquals(401, e.code())
            }

            val firstReq = mockWebServer.takeRequest()
            assertEquals("Bearer candidate-secret-token", firstReq.getHeader("Authorization"))

            val redirectedReq = secondServer.takeRequest()
            assertNull("Authorization header must be stripped on cross-origin redirect", redirectedReq.getHeader("Authorization"))
        } finally {
            secondServer.shutdown()
        }
    }

    private class FakePrivateTokenSeam(
        private val allowedBaseUrl: HttpUrl
    ) : PrivateTokenSeam {
        var allowOrigin: Boolean = true

        override suspend fun getToken(
            entryKey: com.mortgee.al_mobile.data.session.SessionKey?,
            forceRefresh: Boolean
        ): Result<String> = Result.failure(UnsupportedOperationException())

        override suspend fun <T> executePrivate(
            entryKey: com.mortgee.al_mobile.data.session.SessionKey?,
            isMutation: Boolean,
            block: suspend (token: String) -> T
        ): Result<T> = Result.failure(UnsupportedOperationException())

        override fun isAllowedOrigin(url: HttpUrl): Boolean {
            if (!allowOrigin) return false
            return url.host.equals(allowedBaseUrl.host, ignoreCase = true) && url.port == allowedBaseUrl.port
        }

        override fun isAllowedOrigin(urlString: String): Boolean = false
    }
}

