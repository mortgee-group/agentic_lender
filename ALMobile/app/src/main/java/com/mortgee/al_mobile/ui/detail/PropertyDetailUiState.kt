package com.mortgee.al_mobile.ui.detail

import com.mortgee.al_mobile.data.model.CatalogueError
import com.mortgee.al_mobile.data.model.PropertyListing

sealed interface PropertyDetailUiState {
    data object Loading : PropertyDetailUiState

    data class Success(
        val property: PropertyListing
    ) : PropertyDetailUiState

    data class Error(
        val message: String? = null,
        val error: CatalogueError? = null
    ) : PropertyDetailUiState
}
