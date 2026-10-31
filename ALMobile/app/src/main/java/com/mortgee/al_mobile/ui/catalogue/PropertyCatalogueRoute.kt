package com.mortgee.al_mobile.ui.catalogue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PropertyCatalogueRoute(
    onPropertyClick: (String) -> Unit = {},
    isTestFeatureEnabled: Boolean = false,
    onTestFeatureClick: (() -> Unit)? = null,
    viewModel: PropertyCatalogueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PropertyCatalogueScreen(
        uiState = uiState,
        onPropertyClick = onPropertyClick,
        isTestFeatureEnabled = isTestFeatureEnabled,
        onTestFeatureClick = onTestFeatureClick,
        onRetry = { viewModel.loadProperties() }
    )
}
