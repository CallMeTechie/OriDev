package dev.ori.domain.usecase

import dev.ori.core.common.result.AppResult
import dev.ori.domain.repository.ClaudeRepository
import javax.inject.Inject

class SendToClaudeUseCase @Inject constructor(
    private val repository: ClaudeRepository,
) {
    suspend operator fun invoke(userMessage: String, context: String? = null): AppResult<String> =
        repository.sendPrompt(userMessage, context)
}
