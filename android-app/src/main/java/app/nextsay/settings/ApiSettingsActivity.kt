package app.nextsay.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import app.nextsay.nextSayDependencies
import app.nextsay.ui.NextSayUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ApiSettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectionTestJob: Job? = null
    private val ui by lazy { NextSayUi(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = nextSayDependencies
        val controller = ProviderSettingsController(
            store = dependencies.providerConfigStore,
            validator = dependencies.providerConfigValidator,
            tester = DirectProviderConnectionTester(dependencies.replyProviderClient),
            diagnostics = dependencies.diagnostics,
            eventFactory = dependencies.diagnosticEventFactory,
        )
        val initial = controller.state.value
        val root = ui.screen(this, "模型服务", "一套配置，供所有聊天对象使用。", back = { finish() })
        val content = ui.section(root, "连接信息", "支持 OpenAI 兼容格式。API Key 加密保存在本机。")

        val url = ui.field(initial.baseUrl, "https://api.deepseek.com/v1").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(initial.baseUrl)
            fullWidth()
        }
        ui.add(content, label("API 地址"), 18)
        ui.add(content, url, 8)

        val apiKey = ui.field(initial.apiKey, "输入你的 API Key").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setText(initial.apiKey)
            setSelection(text.length)
            fullWidth()
        }
        ui.add(content, label("API Key"), 18)
        ui.add(content, apiKey, 8)
        var keyVisible = false
        ui.add(content, ui.button("显示 API Key") {}.apply {
            setOnClickListener {
                keyVisible = !keyVisible
                apiKey.transformationMethod = if (keyVisible) null else PasswordTransformationMethod.getInstance()
                apiKey.setSelection(apiKey.text.length)
                text = if (keyVisible) "隐藏 API Key" else "显示 API Key"
            }
            fullWidth()
        }, 8)

        val model = ui.field(initial.model, DEFAULT_PROVIDER_MODEL).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial.model)
            fullWidth()
        }
        ui.add(content, label("模型名称"), 18)
        ui.add(content, model, 8)

        content.addView(TextView(this).apply {
            text = "对话文字会直接发送到你配置的第三方模型服务，并受该服务隐私政策约束。测试连接会发送一个极小请求，可能消耗可忽略的额度。"
            textSize = 14f
            setTextColor(ui.muted)
            setPadding(0, 20.dp, 0, 12.dp)
            fullWidth()
        })
        val status = ui.text("").apply {
            textSize = 15f
            setPadding(0, 8.dp, 0, 8.dp)
            fullWidth()
        }
        content.addView(status)
        val test = ui.button("测试连接") {}.apply {
            fullWidth()
        }
        ui.add(content, test, 10)
        val save = ui.button("保存配置", primary = true) {}.apply {
            isEnabled = false
            fullWidth()
        }
        ui.add(content, save, 10)
        val copyDiagnostics = ui.button("复制诊断信息") {}.apply {
            visibility = View.GONE
            fullWidth()
        }
        ui.add(content, copyDiagnostics, 10)

        url.doAfterTextChanged { controller.updateUrl(it?.toString().orEmpty()) }
        apiKey.doAfterTextChanged { controller.updateApiKey(it?.toString().orEmpty()) }
        model.doAfterTextChanged { controller.updateModel(it?.toString().orEmpty()) }
        test.setOnClickListener {
            connectionTestJob?.cancel()
            connectionTestJob = scope.launch { controller.testConnection() }
        }
        save.setOnClickListener {
            if (controller.save()) {
                Toast.makeText(this, "模型服务配置已保存", Toast.LENGTH_SHORT).show()
            }
        }
        copyDiagnostics.setOnClickListener {
            val state = controller.state.value
            val id = state.diagnosticId ?: return@setOnClickListener
            val summary = dependencies.diagnostics.find(id)?.let(dependencies.diagnosticFormatter::compact)
                ?: "${state.status}\n错误编号：$id"
            getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText("NextSay 诊断信息", summary),
            )
            Toast.makeText(this, "诊断信息已复制", Toast.LENGTH_SHORT).show()
        }
        scope.launch {
            controller.state.collectLatest { state ->
                test.isEnabled = !state.testing
                save.isEnabled = state.canSave && !state.testing
                test.alpha = if (test.isEnabled) 1f else .5f
                save.alpha = if (save.isEnabled) 1f else .5f
                status.text = state.status
                status.setTextColor(
                    if (state.canSave) ui.accent else ui.muted,
                )
                copyDiagnostics.visibility = if (state.diagnosticId == null) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(ui.ink)
        fullWidth()
    }

    private fun View.fullWidth() {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
