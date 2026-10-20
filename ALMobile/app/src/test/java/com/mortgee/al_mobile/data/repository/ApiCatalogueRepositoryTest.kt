package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.CatalogueError
import com.mortgee.al_mobile.data.model.CatalogueException
import com.mortgee.al_mobile.data.network.CatalogueApiService
import com.mortgee.al_mobile.data.network.model.CatalogueItemDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlin.coroutines.cancellation.CancellationException

class ApiCatalogueRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: CatalogueApiService
    private lateinit var repository: ApiCatalogueRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        apiService = retrofit.create(CatalogueApiService::class.java)
        repository = ApiCatalogueRepository(apiService)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getProperties parses all five new listing attributes when present in properties array`() = runTest {
        val jsonResponse = """
            {
                "properties": [
                    {
                        "id": "prop-101",
                        "title": "Grand Luxury Penthouse",
                        "summary": "Stunning city view penthouse",
                        "price": 850000.0,
                        "currency": "EUR",
                        "imageUrl": "https://example.com/img1.jpg",
                        "category": "Residential",
                        "isAvailable": true,
                        "location": "Ballsbridge, Dublin 4",
                        "propertySizeSqm": 125.5,
                        "bedrooms": 3,
                        "propertyType": "APARTMENT",
                        "berRating": "A2"
                    }
                ],
                "page": 1,
                "pageSize": 20,
                "totalCount": 1,
                "hasMore": false
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val result = repository.getProperties(page = 1, pageSize = 20)

        assertTrue(result.isSuccess)
        val item = result.getOrNull()!!.items.first()
        assertEquals("Ballsbridge, Dublin 4", item.location)
        assertEquals(125.5, item.propertySizeSqm!!, 0.01)
        assertEquals(3, item.bedrooms)
        assertEquals("APARTMENT", item.propertyType)
        assertEquals("A2", item.berRating)
    }

    @Test
    fun `getProperties handles legacy payloads with missing attributes without crashing`() = runTest {
        val jsonResponse = """
            {
                "properties": [
                    {
                        "id": "prop-legacy",
                        "title": "Old Cottage",
                        "price": 150000.0,
                        "currency": "EUR",
                        "isAvailable": true
                    }
                ],
                "page": 1,
                "pageSize": 20,
                "totalCount": 1,
                "hasMore": false
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val result = repository.getProperties(page = 1)

        assertTrue(result.isSuccess)
        val item = result.getOrNull()!!.items.first()
        assertNull(item.location)
        assertNull(item.propertySizeSqm)
        assertNull(item.bedrooms)
        assertNull(item.propertyType)
        assertNull(item.berRating)
    }

    @Test
    fun `getProperties handles zero-bedroom studios and unknown future codes`() = runTest {
        val jsonResponse = """
            {
                "properties": [
                    {
                        "id": "prop-studio",
                        "title": "Compact Studio",
                        "price": 200000.0,
                        "currency": "EUR",
                        "isAvailable": true,
                        "bedrooms": 0,
                        "propertySizeSqm": 35.0,
                        "propertyType": "MICRO_PAD",
                        "berRating": "EXEMPT"
                    }
                ],
                "page": 1,
                "pageSize": 20,
                "totalCount": 1,
                "hasMore": false
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val result = repository.getProperties(page = 1)

        assertTrue(result.isSuccess)
        val item = result.getOrNull()!!.items.first()
        assertEquals(0, item.bedrooms)
        assertEquals(35.0, item.propertySizeSqm!!, 0.01)
        assertEquals("MICRO_PAD", item.propertyType)
        assertEquals("EXEMPT", item.berRating)
    }

    @Test
    fun `getPropertyDetail returns full property details from detail endpoint`() = runTest {
        val jsonResponse = """
            {
                "id": "prop-202",
                "title": "Executive Suburban House",
                "summary": "Spacious family home",
                "description": "Full detailed description of property features and garden.",
                "price": 620000.0,
                "currency": "EUR",
                "imageUrl": "https://example.com/house.jpg",
                "category": "Residential",
                "isAvailable": true,
                "location": "Dundrum, Dublin 14",
                "propertySizeSqm": 180.0,
                "bedrooms": 4,
                "propertyType": "DETACHED",
                "berRating": "B1",
                "createdAt": "2026-03-01T10:00:00Z",
                "updatedAt": "2026-03-02T12:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val result = repository.getPropertyDetail("prop-202")

        assertTrue(result.isSuccess)
        val detail = result.getOrNull()!!
        assertEquals("prop-202", detail.id)
        assertEquals("Executive Suburban House", detail.title)
        assertEquals("Full detailed description of property features and garden.", detail.description)
        assertEquals("Dundrum, Dublin 14", detail.location)
        assertEquals(180.0, detail.propertySizeSqm!!, 0.01)
        assertEquals(4, detail.bedrooms)
        assertEquals("DETACHED", detail.propertyType)
        assertEquals("B1", detail.berRating)
        assertEquals("2026-03-01T10:00:00Z", detail.createdAt)

        val request = mockWebServer.takeRequest()
        assertEquals("/api/v1/properties/prop-202", request.path)
    }

    @Test
    fun `getPropertyDetail maps 404 Not Found to NotFound CatalogueError`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val result = repository.getPropertyDetail("unknown-id")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as CatalogueException
        assertEquals(CatalogueError.NotFound, exception.error)
    }

    @Test
    fun `getProperties maps 400 Bad Request to InvalidQuery CatalogueError`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        val result = repository.getProperties(page = 1)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as CatalogueException
        assertEquals(CatalogueError.InvalidQuery, exception.error)
    }

    @Test
    fun `getProperties maps 500 Internal Error to NetworkError CatalogueError`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.getProperties(page = 1)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as CatalogueException
        assertEquals(CatalogueError.NetworkError, exception.error)
    }

    @Test
    fun `getProperties rethrows CancellationException`() = runTest {
        val cancellingApiService = object : CatalogueApiService {
            override suspend fun getItems(
                page: Int,
                pageSize: Int,
                sort: String?,
                query: String?
            ) = throw CancellationException("Job cancelled")

            override suspend fun getItemDetail(id: String): CatalogueItemDto {
                throw CancellationException("Job cancelled")
            }
        }

        val cancellingRepository = ApiCatalogueRepository(cancellingApiService)

        try {
            cancellingRepository.getProperties(page = 1)
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Job cancelled", e.message)
        }
    }
}
