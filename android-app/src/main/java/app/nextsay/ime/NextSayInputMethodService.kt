package app.nextsay.ime

import android.inputmethodservice.InputMethodService
import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import app.nextsay.nextSayDependencies
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.provider.ProviderErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NextSayInputMethodService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val editorPolicy = ImeEditorPolicy()
    private val session = NextSayImeRuntime.session
    private var renderJob: Job? = null

    override fun onCreateInputView(): View {
        val views = ImeKeyboardViewFactory(this).create(
            ImeKeyboardCallbacks(
                onGenerate = { scope.launch { session.requestGeneration() } },
                onCandidate = ::commitCandidate,
                onSwitchInputMethod = ::showInputMethodPicker,
                onCopyDiagnostics = ::copyDiagnostics,
            ),
        )
        renderJob?.cancel()
        renderJob = scope.launch { session.state.collectLatest(views::render) }
        return views.root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        session.attachEditor(editorPolicy.evaluate(attribute?.packageName, attribute?.inputType ?: 0))
    }

    override fun onFinishInput() {
        session.detachEditor()
        super.onFinishInput()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun commitCandidate(index: Int) {
        val editorInfo = currentInputEditorInfo
        val eligibility = editorPolicy.evaluate(editorInfo?.packageName, editorInfo?.inputType ?: 0)
        if (eligibility !is ImeEditorEligibility.Available) {
            session.attachEditor(eligibility)
            return
        }
        val candidate = session.candidateForCommit(index, eligibility.targetPackage) ?: return
        val success = runCatching {
            currentInputConnection?.commitText(candidate.text, 1) == true
        }.getOrDefault(false)
        val diagnosticId = if (success) null else {
            val dependencies = nextSayDependencies
            val event = dependencies.diagnosticEventFactory.create(
                type = DiagnosticEventType.INSERTION_FAILED,
                surface = DiagnosticSurface.IME,
                errorCode = ProviderErrorCode.INSERTION_FAILED.wireCode,
            )
            dependencies.diagnostics.record(event)
            event.id
        }
        session.completeCommit(success, diagnosticId)
    }

    private fun showInputMethodPicker() {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    private fun copyDiagnostics(diagnosticId: String) {
        val dependencies = nextSayDependencies
        val summary = dependencies.diagnostics.find(diagnosticId)
            ?.let(dependencies.diagnosticFormatter::compact)
            ?: buildString {
                appendLine("NextSay 诊断信息")
                appendLine("错误编号：$diagnosticId")
                val current = session.state.value as? ImeReplyState.Error
                append("错误：${current?.message ?: ProviderErrorCode.APP_INTERNAL.wireCode}")
            }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("NextSay 诊断信息", summary),
        )
        Toast.makeText(this, "诊断信息已复制", Toast.LENGTH_SHORT).show()
    }
}
