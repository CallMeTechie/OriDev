package dev.ori.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.feature.onboarding.data.OnboardingPreferences
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {

    fun markCompleted(onMarked: () -> Unit = {}) {
        viewModelScope.launch {
            onboardingPreferences.markCompleted()
            onMarked()
        }
    }
}
