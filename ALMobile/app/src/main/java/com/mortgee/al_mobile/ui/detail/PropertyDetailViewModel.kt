package com.mortgee.al_mobile.ui.detail

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
class PropertyDetailViewModel @Inject constructor(
    private val catalogueRepository: CatalogueRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_PROPERTY_ID = "propertyId"
    }

    private val _uiState = MutableStateFlow<PropertyDetailUiState>(PropertyDetailUiState.Loading)
    val uiState: StateFlow<PropertyDetailUiState> = _uiState.asStateFlow()

    val propertyId: String?
        get() = savedStateHandle[KEY_PROPERTY_ID]

    init {
        propertyId?.let { id ->
            loadPropertyDetail(id)
        }
    }

    fun loadPropertyDetail(id: String = propertyId ?: "") {
        if (id.isBlank()) return
        savedStateHandle[KEY_PROPERTY_ID] = id
        _uiState.value = PropertyDetailUiState.Loading

        viewModelScope.launch {
            catalogueRepository.getPropertyDetail(id)
                .onSuccess { item ->
                    _uiState.value = PropertyDetailUiState.Success(property = item)
                }
                .onFailure { exception ->
                    val error = (exception as? CatalogueException)?.error
                    _uiState.value = PropertyDetailUiState.Error(
                        message = exception.message ?: "Failed to load property details",
                        error = error
                    )
                }
        }
    }
}
