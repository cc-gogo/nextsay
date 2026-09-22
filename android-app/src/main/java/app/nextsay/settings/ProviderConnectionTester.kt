package app.nextsay.settings

import app.nextsay.provider.ReplyProviderClient
import app.nextsay.provider.ValidatedProviderConfig
import kotlinx.coroutines.CancellationException

fun interface ProviderConnectionTester {
    suspend fun test(config: ValidatedProviderConfig): Result<Unit>
}

class DirectProviderConnectionTester(
    private val client: ReplyProviderClient,
) : ProviderConnectionTester {
    override suspend fun test(config: ValidatedProviderConfig): Result<Unit> = try {
        client.testConnection(config)
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
