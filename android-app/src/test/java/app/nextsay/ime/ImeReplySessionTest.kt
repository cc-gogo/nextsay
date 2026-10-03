package app.nextsay.ime

import app.nextsay.overlay.ReplyCandidate
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImeReplySessionTest {
    @Test fun `cancelled request releases the loading UI and allows another request`() = runTest {
        val deferred = CompletableDeferred<Result<List<ReplyCandidate>>>()
        val session = readySession(ImeGenerationHandler { deferred.await() })
        val request = launch { session.requestGeneration() }
        runCurrent()
        request.cancel()
        request.join()
        assertEquals(ImeReplyState.Ready(WECHAT), session.state.value)
        session.registerHandler(ImeGenerationHandler { Result.success(replies()) })
        session.requestGeneration()
        assertTrue(session.state.value is ImeReplyState.Results)
    }
    @Test
    fun `supported editor needs a connected accessibility service`() {
        val session = ImeReplySession()

        session.attachEditor(ImeEditorEligibility.Available(WECHAT))

        assertEquals(
            ImeReplyState.Unavailable(ImeUnavailableReason.SERVICE_DISCONNECTED),
            session.state.value,
        )
    }

    @Test
    fun `registered handler makes supported editor ready`() {
        val session = ImeReplySession()
        session.attachEditor(ImeEditorEligibility.Available(WECHAT))

        session.registerHandler(ImeGenerationHandler { Result.success(replies()) })

        assertEquals(ImeReplyState.Ready(WECHAT), session.state.value)
    }

    @Test
    fun `generation exposes loading then exactly three candidates`() = runTest {
        val deferred = CompletableDeferred<Result<List<ReplyCandidate>>>()
        val session = readySession(ImeGenerationHandler { deferred.await() })

        val request = async { session.requestGeneration() }
        runCurrent()
        assertEquals(ImeReplyState.Loading(WECHAT), session.state.value)

        deferred.complete(Result.success(replies()))
        request.await()

        assertEquals(ImeReplyState.Results(WECHAT, replies()), session.state.value)
    }

    @Test
    fun `second request cannot start while generation is running`() = runTest {
        var calls = 0
        val deferred = CompletableDeferred<Result<List<ReplyCandidate>>>()
        val session = readySession(ImeGenerationHandler {
            calls += 1
            deferred.await()
        })

        val first = async { session.requestGeneration() }
        runCurrent()
        session.requestGeneration()

        assertEquals(1, calls)
        deferred.complete(Result.success(replies()))
        first.await()
    }

    @Test
    fun `late WeChat result cannot return after switching editor to QQ`() = runTest {
        val deferred = CompletableDeferred<Result<List<ReplyCandidate>>>()
        val session = readySession(ImeGenerationHandler { deferred.await() })
        launch { session.requestGeneration() }
        runCurrent()

        session.attachEditor(ImeEditorEligibility.Available(QQ))
        deferred.complete(Result.success(replies()))
        advanceUntilIdle()

        assertEquals(ImeReplyState.Ready(QQ), session.state.value)
    }

    @Test
    fun `detaching editor clears generated candidates`() = runTest {
        val session = readySession()
        session.requestGeneration()
        assertTrue(session.state.value is ImeReplyState.Results)

        session.detachEditor()

        assertEquals(
            ImeReplyState.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP),
            session.state.value,
        )
    }

    @Test
    fun `mismatched target cannot obtain candidate and clears stale results`() = runTest {
        val session = readySession()
        session.requestGeneration()

        val candidate = session.candidateForCommit(0, QQ)

        assertNull(candidate)
        assertEquals(ImeReplyState.Ready(WECHAT), session.state.value)
    }

    @Test
    fun `successful commit removes candidate text from state`() = runTest {
        val session = readySession()
        session.requestGeneration()
        assertNotNull(session.candidateForCommit(1, WECHAT))

        session.completeCommit(success = true)

        assertEquals(ImeReplyState.Inserted(WECHAT), session.state.value)
    }

    @Test
    fun `failed commit retains candidates and exposes connection error`() = runTest {
        val session = readySession()
        session.requestGeneration()
        session.candidateForCommit(1, WECHAT)

        session.completeCommit(success = false, diagnosticId = "insert-event")

        val state = session.state.value as ImeReplyState.Results
        assertEquals(replies(), state.candidates)
        assertNotNull(state.message)
        assertTrue(state.message!!.contains("APP-INSERT"))
        assertEquals("insert-event", state.diagnosticId)
    }

    @Test
    fun `unregister only removes the same generation handler`() {
        val first = ImeGenerationHandler { Result.success(replies()) }
        val other = ImeGenerationHandler { Result.success(replies()) }
        val session = readySession(first)

        session.unregisterHandler(other)
        assertEquals(ImeReplyState.Ready(WECHAT), session.state.value)

        session.unregisterHandler(first)
        assertEquals(
            ImeReplyState.Unavailable(ImeUnavailableReason.SERVICE_DISCONNECTED),
            session.state.value,
        )
    }

    @Test
    fun `invalid candidate count becomes an error`() = runTest {
        val session = readySession(ImeGenerationHandler { Result.success(replies().take(2)) })

        session.requestGeneration()

        assertTrue(session.state.value is ImeReplyState.Error)
    }

    @Test
    fun `IME error preserves diagnostic ID`() = runTest {
        val session = readySession(
            ImeGenerationHandler {
                Result.failure(ProviderException(ProviderErrorCode.NET_TIMEOUT, "evt-timeout"))
            },
        )

        session.requestGeneration()

        val error = session.state.value as ImeReplyState.Error
        assertEquals("evt-timeout", error.diagnosticId)
        assertTrue(error.message.contains("NET-TIMEOUT"))
    }

    private fun readySession(
        handler: ImeGenerationHandler = ImeGenerationHandler { Result.success(replies()) },
    ) = ImeReplySession().also { session ->
        session.registerHandler(handler)
        session.attachEditor(ImeEditorEligibility.Available(WECHAT))
    }

    private companion object {
        const val WECHAT = "com.tencent.mm"
        const val QQ = "com.tencent.mobileqq"

        fun replies() = listOf(
            ReplyCandidate("concise", "收到"),
            ReplyCandidate("tactful", "好的，我会尽快处理"),
            ReplyCandidate("natural", "没问题，处理好后告诉你"),
        )
    }
}
