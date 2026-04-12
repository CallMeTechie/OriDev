package dev.ori.domain.usecase

import dev.ori.domain.repository.ClaudeRepository
import javax.inject.Inject

class SetClaudeApiKeyUseCase @Inject constructor(
    private val repository: ClaudeRepository,
) {
    suspend operator fun invoke(apiKey: String) {
        repository.setApiKey(apiKey)
    }
}
