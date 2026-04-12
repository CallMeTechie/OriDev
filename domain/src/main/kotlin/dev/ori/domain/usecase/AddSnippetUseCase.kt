package dev.ori.domain.usecase

import dev.ori.domain.model.CommandSnippet
import dev.ori.domain.repository.SnippetRepository
import javax.inject.Inject

class AddSnippetUseCase @Inject constructor(
    private val repository: SnippetRepository,
) {
    suspend operator fun invoke(snippet: CommandSnippet): Long = repository.addSnippet(snippet)
}
