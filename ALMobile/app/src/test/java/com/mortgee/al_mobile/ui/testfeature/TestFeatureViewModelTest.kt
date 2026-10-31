package com.mortgee.al_mobile.ui.testfeature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestFeatureViewModelTest {

    @Test
    fun `initial uiState has default build and feature flag configuration`() {
        val viewModel = TestFeatureViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.isFeatureEnabled)
        assertEquals("", state.locationInput)
        assertNull(state.validationResult)
    }

    @Test
    fun `input field updates correctly update uiState`() {
        val viewModel = TestFeatureViewModel()

        viewModel.updateLocation("Ballsbridge, Dublin 4")
        viewModel.updateSizeSqm("100.0")
        viewModel.updateBedrooms("2")
        viewModel.updatePropertyType("APARTMENT")
        viewModel.updateBerRating("A1")

        val state = viewModel.uiState.value
        assertEquals("Ballsbridge, Dublin 4", state.locationInput)
        assertEquals("100.0", state.sizeSqmInput)
        assertEquals("2", state.bedroomsInput)
        assertEquals("APARTMENT", state.propertyTypeInput)
        assertEquals("A1", state.berRatingInput)
    }

    @Test
    fun `validateInputs produces valid PropertyFormValidationResult`() {
        val viewModel = TestFeatureViewModel()

        viewModel.updateLocation("Ballsbridge, Dublin 4")
        viewModel.updateSizeSqm("120.0")
        viewModel.updateBedrooms("3")
        viewModel.updatePropertyType("DETACHED")
        viewModel.updateBerRating("A2")

        viewModel.validateInputs()

        val state = viewModel.uiState.value
        assertNotNull(state.validationResult)
        assertTrue(state.validationResult!!.isValid)
        assertEquals("Ballsbridge, Dublin 4", state.validationResult!!.parsedLocation)
        assertEquals(120.0, state.validationResult!!.parsedSizeSqm!!, 0.01)
        assertEquals(3, state.validationResult!!.parsedBedrooms)
        assertEquals("DETACHED", state.validationResult!!.parsedPropertyType)
        assertEquals("A2", state.validationResult!!.parsedBerRating)
    }

    @Test
    fun `resetInputs clears all inputs and validation result`() {
        val viewModel = TestFeatureViewModel()

        viewModel.updateLocation("Test Location")
        viewModel.validateInputs()
        assertNotNull(viewModel.uiState.value.validationResult)

        viewModel.resetInputs()

        val state = viewModel.uiState.value
        assertEquals("", state.locationInput)
        assertEquals("", state.sizeSqmInput)
        assertEquals("", state.bedroomsInput)
        assertEquals("", state.propertyTypeInput)
        assertEquals("", state.berRatingInput)
        assertNull(state.validationResult)
    }
}
