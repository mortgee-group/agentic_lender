package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.model.BackendRole
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ProfileApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var profileApiService: ProfileApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val okHttpClient = OkHttpClient.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        profileApiService = retrofit.create(ProfileApiService::class.java)
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
}
