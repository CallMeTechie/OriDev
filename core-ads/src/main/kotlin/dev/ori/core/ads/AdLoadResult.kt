package dev.ori.core.ads

sealed class AdLoadResult {
    data class Loaded(val handle: Any) : AdLoadResult()
    data class Failed(val code: Int, val message: String) : AdLoadResult()
    data object NoFill : AdLoadResult()
}
