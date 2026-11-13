package com.mortgee.al_mobile.ui.catalogue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mortgee.al_mobile.data.model.CatalogueException
import com.mortgee.al_mobile.data.repository.CatalogueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PropertyCatalogueViewModel @Inject constructor(
    private val catalogueRepository: CatalogueRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_QUERY = "query"
        private const val KEY_PAGE = "page"
    }

    private val _uiState = MutableStateFlow<PropertyCatalogueUiState>(PropertyCatalogueUiState.Loading)
    val uiState: StateFlow<PropertyCatalogueUiState> = _uiState.asStateFlow()

    val query: String?
        get() = savedStateHandle[KEY_QUERY]

    val page: Int
        get() = savedStateHandle[KEY_PAGE] ?: 1

    init {
        loadProperties()
    }

    fun loadProperties(query: String? = this.query, page: Int = this.page) {
        savedStateHandle[KEY_QUERY] = query
        savedStateHandle[KEY_PAGE] = page
        _uiState.value = PropertyCatalogueUiState.Loading

        viewModelScope.launch {
            catalogueRepository.getProperties(
                page = page,
                query = query
            ).onSuccess { pageResult ->
                _uiState.value = PropertyCatalogueUiState.Success(page = pageResult)
            }.onFailure { exception ->
                val error = (exception as? CatalogueException)?.error
                _uiState.value = PropertyCatalogueUiState.Error(
                    message = exception.message ?: "Failed to load properties",
                    error = error
                )
            }
        }
    }
}
