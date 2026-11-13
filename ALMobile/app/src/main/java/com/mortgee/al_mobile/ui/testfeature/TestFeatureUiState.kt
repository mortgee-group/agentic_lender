package com.mortgee.al_mobile.ui.testfeature

import com.mortgee.al_mobile.ui.form.PropertyFormValidationResult

data class TestFeatureUiState(
    val isFeatureEnabled: Boolean = true,
    val apiBaseUrl: String = "",
    val buildType: String = "",
    val locationInput: String = "",
    val sizeSqmInput: String = "",
    val bedroomsInput: String = "",
    val propertyTypeInput: String = "",
    val berRatingInput: String = "",
    val validationResult: PropertyFormValidationResult? = null
)
