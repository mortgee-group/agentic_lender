package com.mortgee.al_mobile.ui.catalogue

import androidx.lifecycle.SavedStateHandle
import com.mortgee.al_mobile.data.model.CatalogueError
import com.mortgee.al_mobile.data.model.CatalogueException
import com.mortgee.al_mobile.data.model.Page
import com.mortgee.al_mobile.data.model.PropertyListing
import com.mortgee.al_mobile.data.repository.CatalogueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PropertyCatalogueViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load succeeds and transitions state from Loading to Success`() = runTest {
        val sampleListings = listOf(
            PropertyListing(
                id = "p1",
                title = "Villa",
                summary = "Beautiful villa",
                price = 500000.0,
                currency = "USD",
                imageUrl = null,
                category = "Luxury",
                isAvailable = true,
                location = "Dublin 4",
                propertySizeSqm = 150.0,
                bedrooms = 4,
                propertyType = "DETACHED",
                berRating = "A1"
            )
        )
        val samplePage = Page(
            items = sampleListings,
            page = 1,
            pageSize = 20,
            totalCount = 1,
            hasMore = false
        )

        val fakeRepository = FakeCatalogueRepository(result = Result.success(samplePage))
        val savedStateHandle = SavedStateHandle()

        val viewModel = PropertyCatalogueViewModel(fakeRepository, savedStateHandle)

        // Initial state is Loading
        assertTrue(viewModel.uiState.value is PropertyCatalogueUiState.Loading)

        testDispatcher.scheduler.advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue(currentState is PropertyCatalogueUiState.Success)
        val successState = currentState as PropertyCatalogueUiState.Success
        assertEquals(1, successState.page.items.size)
        assertEquals("p1", successState.page.items[0].id)
        assertEquals("Dublin 4", successState.page.items[0].location)
    }

    @Test
    fun `initial load failure transitions state to Error`() = runTest {
        val fakeRepository = FakeCatalogueRepository(
            result = Result.failure(CatalogueException(CatalogueError.NetworkError, Exception("Network issue")))
        )
        val savedStateHandle = SavedStateHandle()

        val viewModel = PropertyCatalogueViewModel(fakeRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue(currentState is PropertyCatalogueUiState.Error)
        val errorState = currentState as PropertyCatalogueUiState.Error
        assertEquals(CatalogueError.NetworkError, errorState.error)
    }

    @Test
    fun `load failure with NotFound maps CatalogueError NotFound to Error state`() = runTest {
        val fakeRepository = FakeCatalogueRepository(
            result = Result.failure(CatalogueException(CatalogueError.NotFound, Exception("Endpoint not found 404")))
        )
        val savedStateHandle = SavedStateHandle()

        val viewModel = PropertyCatalogueViewModel(fakeRepository, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        val currentState = viewModel.uiState.value
        assertTrue(currentState is PropertyCatalogueUiState.Error)
        val errorState = currentState as PropertyCatalogueUiState.Error
        assertEquals(CatalogueError.NotFound, errorState.error)
    }

    @Test
    fun `saved state handle retains query and page input`() = runTest {
        val fakeRepository = FakeCatalogueRepository(
            result = Result.success(Page(items = emptyList(), page = 2, pageSize = 20, totalCount = 0, hasMore = false))
        )
        val savedStateHandle = SavedStateHandle(mapOf("query" to "apartment", "page" to 2))

        val viewModel = PropertyCatalogueViewModel(fakeRepository, savedStateHandle)

        assertEquals("apartment", viewModel.query)
        assertEquals(2, viewModel.page)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("apartment", fakeRepository.lastQuery)
        assertEquals(2, fakeRepository.lastPage)
    }

    private class FakeCatalogueRepository(
        private var result: Result<Page<PropertyListing>>
    ) : CatalogueRepository {
        var lastPage: Int? = null
        var lastPageSize: Int? = null
        var lastSort: String? = null
        var lastQuery: String? = null

        override suspend fun getProperties(
            page: Int,
            pageSize: Int,
            sort: String?,
            query: String?
        ): Result<Page<PropertyListing>> {
            lastPage = page
            lastPageSize = pageSize
            lastSort = sort
            lastQuery = query
            return result
        }

        override suspend fun getPropertyDetail(id: String): Result<PropertyListing> {
            val item = result.getOrNull()?.items?.find { it.id == id }
            return if (item != null) {
                Result.success(item)
            } else {
                Result.failure(CatalogueException(CatalogueError.NotFound, Exception("Not found")))
            }
        }
    }
}
