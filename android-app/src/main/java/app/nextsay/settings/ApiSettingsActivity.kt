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
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp, 24.dp, 24.dp, 32.dp)
            setBackgroundColor(Color.rgb(244, 246, 245))
        }
        content.addView(TextView(this).apply {
            text = "配置模型服务"
            textSize = 26f
            setTextColor(Color.rgb(26, 31, 29))
            fullWidth()
        })
        content.addView(TextView(this).apply {
            text = "填写一套支持 OpenAI Chat Completions 格式的服务。API Key 只会加密保存在这台手机上。"
            textSize = 15f
            setTextColor(Color.rgb(72, 82, 78))
            setPadding(0, 10.dp, 0, 18.dp)
            fullWidth()
        })

        val url = EditText(this).apply {
            hint = "https://api.deepseek.com/v1"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(initial.baseUrl)
            fullWidth()
        }
        content.addView(label("API URL"))
        content.addView(url)

        val apiKey = EditText(this).apply {
            hint = "API Key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setText(initial.apiKey)
            setSelection(text.length)
            fullWidth()
        }
        content.addView(label("API Key").apply { setPadding(0, 16.dp, 0, 0) })
        content.addView(apiKey)
        var keyVisible = false
        content.addView(Button(this).apply {
            text = "显示 API Key"
            setOnClickListener {
                keyVisible = !keyVisible
                apiKey.transformationMethod = if (keyVisible) null else PasswordTransformationMethod.getInstance()
                apiKey.setSelection(apiKey.text.length)
                text = if (keyVisible) "隐藏 API Key" else "显示 API Key"
            }
            fullWidth()
        })

        val model = EditText(this).apply {
            hint = DEFAULT_PROVIDER_MODEL
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial.model)
            fullWidth()
        }
        content.addView(label("模型名称").apply { setPadding(0, 16.dp, 0, 0) })
        content.addView(model)

        content.addView(TextView(this).apply {
            text = "对话文字会直接发送到你配置的第三方模型服务，并受该服务隐私政策约束。测试连接会发送一个极小请求，可能消耗可忽略的额度。"
            textSize = 14f
            setTextColor(Color.rgb(72, 82, 78))
            setPadding(0, 20.dp, 0, 12.dp)
            fullWidth()
        })
        val status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 8.dp, 0, 8.dp)
            fullWidth()
        }
        content.addView(status)
        val test = Button(this).apply {
            text = "测试连接"
            fullWidth()
        }
        content.addView(test)
        val save = Button(this).apply {
            text = "保存"
            isEnabled = false
            fullWidth()
        }
        content.addView(save)
        val copyDiagnostics = Button(this).apply {
            text = "复制诊断信息"
            visibility = View.GONE
            fullWidth()
        }
        content.addView(copyDiagnostics)
        setContentView(ScrollView(this).apply { addView(content) })

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
                status.text = state.status
                status.setTextColor(
                    if (state.canSave) Color.rgb(23, 107, 77) else Color.rgb(168, 55, 55),
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
        setTextColor(Color.rgb(26, 31, 29))
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
