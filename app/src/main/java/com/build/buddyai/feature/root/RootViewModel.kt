package com.build.buddyai.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.PreferencesRepository
import com.build.buddyai.core.model.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RootUiState(
    val loading: Boolean = true,
    val preferences: AppPreferences = AppPreferences(),
)

@HiltViewModel
class RootViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val uiState = preferencesRepository.preferences
        .map { RootUiState(loading = false, preferences = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootUiState())
}

