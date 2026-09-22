package app.nextsay.provider

import app.nextsay.api.ReplyCandidateDto
import app.nextsay.api.ReplyRequestDto
import app.nextsay.diagnostics.DiagnosticSurface

interface ReplyProviderClient {
    suspend fun generate(
        config: ValidatedProviderConfig,
        request: ReplyRequestDto,
        surface: DiagnosticSurface,
    ): List<ReplyCandidateDto>

    suspend fun testConnection(config: ValidatedProviderConfig)
}
