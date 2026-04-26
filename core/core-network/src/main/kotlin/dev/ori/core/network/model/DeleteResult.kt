package dev.ori.core.network.model

data class DeleteResult(val succeeded: List<String>, val failed: List<Pair<String, String>>) {
    val isFullSuccess: Boolean get() = failed.isEmpty()
    fun merge(other: DeleteResult) =
        DeleteResult(succeeded + other.succeeded, failed + other.failed)
    companion object { val EMPTY = DeleteResult(emptyList(), emptyList()) }
}
