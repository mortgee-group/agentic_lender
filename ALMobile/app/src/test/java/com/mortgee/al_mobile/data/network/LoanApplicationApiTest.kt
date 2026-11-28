package com.mortgee.al_mobile.data.network

import com.mortgee.al_mobile.data.network.model.CreateLoanReceipt
import com.mortgee.al_mobile.data.network.model.CreateLoanRequest
import com.mortgee.al_mobile.data.network.model.LoanPropertyDto
import com.mortgee.al_mobile.di.NetworkModule
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class LoanApplicationApiTest {

    @Test
    fun createWire() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val moshi = NetworkModule.provideMoshi()
            val api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(LoanApplicationApi::class.java)

            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setBody(
                        """{"applicationId":"application-001","message":"Mortgage application created successfully"}""",
                    ),
            )

            val response = api.create("Bearer fixture-token", completeRequest())

            assertEquals(201, response.code())
            assertEquals(
                CreateLoanReceipt(
                    applicationId = "application-001",
                    message = "Mortgage application created successfully",
                ),
                response.body(),
            )
            assertEquals(1, server.requestCount)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/applications", recorded.path)
            assertEquals("Bearer fixture-token", recorded.getHeader("Authorization"))
            assertEquals(1, recorded.headers.values("Authorization").size)
            val contentType = recorded.getHeader("Content-Type")
            assertNotNull(contentType)
            assertTrue(contentType!!.startsWith("application/json"))

            val bodyMap = moshi.adapter(Map::class.java).fromJson(recorded.body.readUtf8()) as Map<*, *>
            assertEquals(
                setOf(
                    "householdAnnualGrossIncome",
                    "existingDebtLiabilities",
                    "deposit",
                    "requestedMortgageTerm",
                    "sellerAskingPrice",
                    "property",
                ),
                bodyMap.keys,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun redirectNotFollowed() = runTest {
        val primary = MockWebServer()
        val secondary = MockWebServer()
        primary.start()
        secondary.start()
        try {
            val api = providedApi(primary)
            primary.enqueue(
                MockResponse()
                    .setResponseCode(307)
                    .addHeader("Location", secondary.url("/applications").toString()),
            )
            enqueueSpareSuccess(primary)
            enqueueSpareSuccess(secondary)

            val response = api.create("Bearer fixture-token", completeRequest())

            assertEquals(307, response.code())
            assertEquals(1, primary.requestCount)
            assertEquals(0, secondary.requestCount)
        } finally {
            primary.shutdown()
            secondary.shutdown()
        }
    }

    @Test
    fun retryAfter503NotRetried() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val api = providedApi(server)
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .addHeader("Retry-After", "0")
                    .setBody("""{"detail":"No brokers are currently available"}"""),
            )
            enqueueSpareSuccess(server)

            val response = api.create("Bearer fixture-token", completeRequest())

            assertEquals(503, response.code())
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun disconnectAfterRequestDoesNotRetransmit() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val api = providedApi(server)
            server.enqueue(
                MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
            )
            enqueueSpareSuccess(server)

            try {
                api.create("Bearer fixture-token", completeRequest())
                fail("Expected IOException on disconnected response")
            } catch (_: IOException) {
                // Expected transport failure
            }

            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun providedApi(server: MockWebServer): LoanApplicationApi {
        val moshi = NetworkModule.provideMoshi()
        val localClient = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(localClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return NetworkModule.provideLoanApplicationApi(retrofit, localClient)
    }

    private fun enqueueSpareSuccess(server: MockWebServer) {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """{"applicationId":"spare-001","message":"Mortgage application created successfully"}""",
                ),
        )
    }

    private fun completeRequest(): CreateLoanRequest = CreateLoanRequest(
        householdAnnualGrossIncome = 75000.0,
        existingDebtLiabilities = 10000.0,
        deposit = 70000.0,
        requestedMortgageTerm = 30,
        sellerAskingPrice = 350000.0,
        property = LoanPropertyDto(
            county = "Kilkenny",
            floorArea = 115.0,
            bedrooms = 3,
            bathrooms = 2,
            yearBuilt = 2005,
            propertyType = "SEMI_DETACHED",
            berRating = "B2",
        ),
    )
}
