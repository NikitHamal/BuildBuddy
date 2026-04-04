package com.build.buddyai.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val currentPage: Int = 0,
    val totalPages: Int = 4,
    val isCompleted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun nextPage() {
        _state.update { current ->
            if (current.currentPage < current.totalPages - 1) {
                current.copy(currentPage = current.currentPage + 1)
            } else {
                current
            }
        }
    }

    fun previousPage() {
        _state.update { current ->
            if (current.currentPage > 0) {
                current.copy(currentPage = current.currentPage - 1)
            } else {
                current
            }
        }
    }

    fun goToPage(page: Int) {
        _state.update { it.copy(currentPage = page.coerceIn(0, it.totalPages - 1)) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(true)
            _state.update { it.copy(isCompleted = true) }
        }
    }
}
