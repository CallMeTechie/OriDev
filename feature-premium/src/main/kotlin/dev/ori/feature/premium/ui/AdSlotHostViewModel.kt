package dev.ori.feature.premium.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ori.core.ads.AdLoadResult
import dev.ori.core.ads.AdLoader
import dev.ori.domain.model.AdSlot
import dev.ori.domain.repository.AdGate
import dev.ori.domain.usecase.CheckPremiumUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdSlotHostViewModel @Inject constructor(
    private val checkPremiumUseCase: CheckPremiumUseCase,
    private val adGate: AdGate,
    private val adLoader: AdLoader,
) : ViewModel() {

    private val _state = MutableStateFlow<AdSlotHostState>(AdSlotHostState.Loading)
    val state: StateFlow<AdSlotHostState> = _state.asStateFlow()

    private var currentSlot: AdSlot? = null

    fun init(slot: AdSlot) {
        if (currentSlot == slot) return
        currentSlot = slot
        viewModelScope.launch {
            checkPremiumUseCase().collect { isPremium ->
                if (isPremium) {
                    _state.value = AdSlotHostState.Hidden
                } else {
                    loadAd(slot)
                }
            }
        }
    }

    private suspend fun loadAd(slot: AdSlot) {
        if (!adGate.shouldShow(slot)) {
            _state.value = AdSlotHostState.Hidden
            return
        }
        _state.value = AdSlotHostState.Loading
        when (slot) {
            AdSlot.SETTINGS_HOUSE_UPSELL -> {
                _state.value = AdSlotHostState.House
            }
            AdSlot.CONNECTION_LIST_NATIVE -> {
                when (val result = adLoader.loadNative(slot)) {
                    is AdLoadResult.Loaded -> _state.value = AdSlotHostState.Native(result.handle)
                    else -> _state.value = AdSlotHostState.Hidden
                }
            }
            else -> {
                when (val result = adLoader.loadBanner(slot)) {
                    is AdLoadResult.Loaded -> _state.value = AdSlotHostState.Banner(result.handle)
                    else -> _state.value = AdSlotHostState.Hidden
                }
            }
        }
    }

    override fun onCleared() {
        currentSlot?.let { adLoader.destroy(it) }
    }
}
