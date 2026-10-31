package com.mortgee.al_mobile.ui.testfeature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TestFeatureRoute(
    onBackClick: () -> Unit = {},
    viewModel: TestFeatureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TestFeatureScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onLocationChange = viewModel::updateLocation,
        onSizeSqmChange = viewModel::updateSizeSqm,
        onBedroomsChange = viewModel::updateBedrooms,
        onPropertyTypeChange = viewModel::updatePropertyType,
        onBerRatingChange = viewModel::updateBerRating,
        onValidateClick = viewModel::validateInputs,
        onResetClick = viewModel::resetInputs
    )
}
