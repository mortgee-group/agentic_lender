package com.mortgee.al_mobile.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PropertyDetailRoute(
    propertyId: String,
    onBackClick: () -> Unit = {},
    viewModel: PropertyDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(propertyId) {
        viewModel.loadPropertyDetail(propertyId)
    }

    PropertyDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRetry = { viewModel.loadPropertyDetail(propertyId) }
    )
}
