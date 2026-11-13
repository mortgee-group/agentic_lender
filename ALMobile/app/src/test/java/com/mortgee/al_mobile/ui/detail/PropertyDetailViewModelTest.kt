package com.mortgee.al_mobile.ui.detail

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
class PropertyDetailViewModelTest {

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
    fun `loadPropertyDetail transitions state to Success when repository returns property`() = runTest {
        val sampleProperty = PropertyListing(
            id = "p-100",
            title = "Luxury Apartment",
            summary = "Great location",
            description = "Full description here",
            price = 450000.0,
            currency = "EUR",
            location = "Dublin 2",
            propertySizeSqm = 85.0,
            bedrooms = 2,
            propertyType = "APARTMENT",
            berRating = "A3"
        )

        val fakeRepo = FakeCatalogueRepository(detailResult = Result.success(sampleProperty))
        val savedStateHandle = SavedStateHandle(mapOf(PropertyDetailViewModel.KEY_PROPERTY_ID to "p-100"))

        val viewModel = PropertyDetailViewModel(fakeRepo, savedStateHandle)

        assertTrue(viewModel.uiState.value is PropertyDetailUiState.Loading)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PropertyDetailUiState.Success)
        val successState = state as PropertyDetailUiState.Success
        assertEquals("p-100", successState.property.id)
        assertEquals("Dublin 2", successState.property.location)
        assertEquals(85.0, successState.property.propertySizeSqm!!, 0.01)
        assertEquals(2, successState.property.bedrooms)
        assertEquals("APARTMENT", successState.property.propertyType)
        assertEquals("A3", successState.property.berRating)
    }

    @Test
    fun `loadPropertyDetail transitions state to Error on NotFound 404`() = runTest {
        val fakeRepo = FakeCatalogueRepository(
            detailResult = Result.failure(CatalogueException(CatalogueError.NotFound, Exception("404")))
        )
        val savedStateHandle = SavedStateHandle(mapOf(PropertyDetailViewModel.KEY_PROPERTY_ID to "invalid-id"))

        val viewModel = PropertyDetailViewModel(fakeRepo, savedStateHandle)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PropertyDetailUiState.Error)
        val errorState = state as PropertyDetailUiState.Error
        assertEquals(CatalogueError.NotFound, errorState.error)
    }

    private class FakeCatalogueRepository(
        private val detailResult: Result<PropertyListing>
    ) : CatalogueRepository {
        override suspend fun getProperties(
            page: Int,
            pageSize: Int,
            sort: String?,
            query: String?
        ): Result<Page<PropertyListing>> {
            return Result.success(Page(items = emptyList(), page = 1, pageSize = 20, totalCount = 0, hasMore = false))
        }

        override suspend fun getPropertyDetail(id: String): Result<PropertyListing> {
            return detailResult
        }
    }
}
