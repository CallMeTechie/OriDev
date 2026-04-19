package dev.ori.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.domain.model.GrantedTree
import dev.ori.domain.repository.StorageAccessRepository
import dev.ori.feature.onboarding.data.OnboardingPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
    private val storageAccessRepository: StorageAccessRepository,
) : ViewModel() {

    /**
     * Phase 15 Task 15.6 — the permissions screen reads this so the
     * "Ordner auswählen" card can flip to a "granted" state after the
     * user picks a folder. Starts empty on fresh installs.
     */
    val grantedTrees: StateFlow<List<GrantedTree>> = storageAccessRepository.grantedTrees
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    fun grantStorageTree(uri: String) {
        viewModelScope.launch { storageAccessRepository.grant(uri) }
    }

    fun markCompleted(onMarked: () -> Unit = {}) {
        viewModelScope.launch {
            onboardingPreferences.markCompleted()
            onMarked()
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
    }
}
