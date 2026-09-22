package app.nextsay.settings

import app.nextsay.diagnostics.DiagnosticEvent
import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticMetadata
import app.nextsay.diagnostics.DiagnosticRecorder
import app.nextsay.provider.ProviderConfig
import app.nextsay.provider.ProviderConfigStore
import app.nextsay.provider.ProviderConfigValidator
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import app.nextsay.provider.ValidatedProviderConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSettingsControllerTest {
    @Test
    fun `valid fields must pass test before save`() = runTest {
        val store = FakeStore()
        val controller = controller(store, SuccessfulTester())
        configure(controller)

        assertFalse(controller.state.value.canSave)
        controller.testConnection()

        assertTrue(controller.state.value.canSave)
        assertTrue(controller.save())
        assertEquals("model", store.load()!!.model)
    }

    @Test
    fun `editing any field invalidates successful test`() = runTest {
        val controller = controller(FakeStore(), SuccessfulTester())
        configure(controller)
        controller.testConnection()

        controller.updateModel("other-model")

        assertFalse(controller.state.value.canSave)
        assertFalse(controller.save())
    }

    @Test
    fun `late result from old values cannot enable save`() = runTest {
        val tester = DeferredTester()
        val controller = controller(FakeStore(), tester)
        configure(controller)
        val first = launch { controller.testConnection() }
        runCurrent()

        controller.updateUrl("https://changed.example/v1")
        tester.result.complete(Result.success(Unit))
        first.join()

        assertFalse(controller.state.value.canSave)
    }

    @Test
    fun `starting another test cancels authority of first result`() = runTest {
        val tester = OrderedDeferredTester()
        val controller = controller(FakeStore(), tester)
        configure(controller)
        val first = launch { controller.testConnection() }
        runCurrent()
        val second = launch { controller.testConnection() }
        runCurrent()

        tester.calls[1].complete(Result.success(Unit))
        second.join()
        tester.calls[0].complete(
            Result.failure(ProviderException(ProviderErrorCode.NET_TIMEOUT, "old-event")),
        )
        first.join()

        assertTrue(controller.state.value.canSave)
        assertNull(controller.state.value.diagnosticId)
    }

    @Test
    fun `invalid fields produce one correlated diagnostic without calling provider`() = runTest {
        val diagnostics = MemoryDiagnosticRecorder()
        val tester = CountingTester()
        val controller = controller(FakeStore(), tester, diagnostics)
        controller.updateUrl("not-a-url")
        controller.updateApiKey("key")
        controller.updateModel("model")

        controller.testConnection()

        assertEquals(0, tester.calls)
        assertEquals(1, diagnostics.events().size)
        assertEquals("CFG-INVALID", diagnostics.events().single().errorCode)
        assertEquals("event-1", controller.state.value.diagnosticId)
        assertTrue(controller.state.value.status.contains("CFG-INVALID"))
    }

    @Test
    fun `provider failure keeps client diagnostic id without duplicate event`() = runTest {
        val diagnostics = MemoryDiagnosticRecorder()
        val controller = controller(
            store = FakeStore(),
            tester = ProviderConnectionTester {
                Result.failure(ProviderException(ProviderErrorCode.API_AUTH, "provider-event"))
            },
            diagnostics = diagnostics,
        )
        configure(controller)

        controller.testConnection()

        assertEquals(0, diagnostics.events().size)
        assertEquals("provider-event", controller.state.value.diagnosticId)
        assertTrue(controller.state.value.status.contains("API-AUTH"))
        assertFalse(controller.state.value.canSave)
    }

    private fun configure(controller: ProviderSettingsController) {
        controller.updateUrl("https://api.example/v1")
        controller.updateApiKey("key")
        controller.updateModel("model")
    }

    private fun controller(
        store: FakeStore,
        tester: ProviderConnectionTester,
        diagnostics: MemoryDiagnosticRecorder = MemoryDiagnosticRecorder(),
    ) = ProviderSettingsController(
        store = store,
        validator = ProviderConfigValidator(allowCleartext = false),
        tester = tester,
        diagnostics = diagnostics,
        eventFactory = DiagnosticEventFactory(
            metadataProvider = {
                DiagnosticMetadata("0.1.0", "test", "14", "Test Device")
            },
            nowMillis = { 1_000L },
            newId = { "event-${diagnostics.events().size + 1}" },
        ),
    )

    private class FakeStore(
        private var value: ProviderConfig? = null,
    ) : ProviderConfigStore {
        override fun load(): ProviderConfig? = value
        override fun save(config: ProviderConfig) {
            value = config
        }
        override fun clear() {
            value = null
        }
    }

    private class SuccessfulTester : ProviderConnectionTester {
        override suspend fun test(config: ValidatedProviderConfig) = Result.success(Unit)
    }

    private class DeferredTester : ProviderConnectionTester {
        val result = CompletableDeferred<Result<Unit>>()
        override suspend fun test(config: ValidatedProviderConfig) = result.await()
    }

    private class OrderedDeferredTester : ProviderConnectionTester {
        val calls = mutableListOf<CompletableDeferred<Result<Unit>>>()
        override suspend fun test(config: ValidatedProviderConfig): Result<Unit> {
            val result = CompletableDeferred<Result<Unit>>()
            calls += result
            return result.await()
        }
    }

    private class CountingTester : ProviderConnectionTester {
        var calls = 0
        override suspend fun test(config: ValidatedProviderConfig): Result<Unit> {
            calls += 1
            return Result.success(Unit)
        }
    }

    private class MemoryDiagnosticRecorder : DiagnosticRecorder {
        private val values = mutableListOf<DiagnosticEvent>()
        override fun record(event: DiagnosticEvent) {
            values += event
        }
        override fun events(): List<DiagnosticEvent> = values.toList()
        override fun find(id: String): DiagnosticEvent? = values.firstOrNull { it.id == id }
        override fun clear() = values.clear()
    }
}
