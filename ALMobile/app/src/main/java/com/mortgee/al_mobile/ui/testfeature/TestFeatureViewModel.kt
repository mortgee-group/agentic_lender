package com.mortgee.al_mobile.ui.testfeature

import androidx.lifecycle.ViewModel
import com.mortgee.al_mobile.BuildConfig
import com.mortgee.al_mobile.ui.form.PropertyFormData
import com.mortgee.al_mobile.ui.form.PropertyFormValidator
import com.mortgee.al_mobile.ui.navigation.NavigationConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TestFeatureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(
        TestFeatureUiState(
            isFeatureEnabled = NavigationConfig.ENABLE_TEST_FEATURE,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            buildType = BuildConfig.BUILD_TYPE
        )
    )
    val uiState: StateFlow<TestFeatureUiState> = _uiState.asStateFlow()

    fun updateLocation(location: String) {
        _uiState.update { it.copy(locationInput = location) }
    }

    fun updateSizeSqm(sizeSqm: String) {
        _uiState.update { it.copy(sizeSqmInput = sizeSqm) }
    }

    fun updateBedrooms(bedrooms: String) {
        _uiState.update { it.copy(bedroomsInput = bedrooms) }
    }

    fun updatePropertyType(propertyType: String) {
        _uiState.update { it.copy(propertyTypeInput = propertyType) }
    }

    fun updateBerRating(berRating: String) {
        _uiState.update { it.copy(berRatingInput = berRating) }
    }

    fun validateInputs() {
        val currentState = _uiState.value
        val formData = PropertyFormData(
            locationInput = currentState.locationInput,
            sizeSqmInput = currentState.sizeSqmInput,
            bedroomsInput = currentState.bedroomsInput,
            propertyTypeInput = currentState.propertyTypeInput,
            berRatingInput = currentState.berRatingInput
        )
        val result = PropertyFormValidator.validate(formData)
        _uiState.update { it.copy(validationResult = result) }
    }

    fun resetInputs() {
        _uiState.update {
            it.copy(
                locationInput = "",
                sizeSqmInput = "",
                bedroomsInput = "",
                propertyTypeInput = "",
                berRatingInput = "",
                validationResult = null
            )
        }
    }
}
