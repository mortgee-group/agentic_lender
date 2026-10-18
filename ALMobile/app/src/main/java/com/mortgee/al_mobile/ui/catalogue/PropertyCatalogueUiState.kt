package com.mortgee.al_mobile.ui.catalogue

import com.mortgee.al_mobile.data.model.CatalogueError
import com.mortgee.al_mobile.data.model.Page
import com.mortgee.al_mobile.data.model.PropertyListing

sealed interface PropertyCatalogueUiState {
    data object Loading : PropertyCatalogueUiState

    data class Success(
        val page: Page<PropertyListing>
    ) : PropertyCatalogueUiState

    data class Error(
        val message: String? = null,
        val error: CatalogueError? = null
    ) : PropertyCatalogueUiState
}
