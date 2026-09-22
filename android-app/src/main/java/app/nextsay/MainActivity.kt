package app.nextsay

import android.app.Activity
import android.app.AlertDialog
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import app.nextsay.accessibility.NextSayAccessibilityService
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.diagnostics.DiagnosticsActivity
import app.nextsay.history.AndroidKeystoreMessageCipher
import app.nextsay.history.ConversationHistoryRepository
import app.nextsay.history.db.NextSayDatabase
import app.nextsay.ime.NextSayInputMethodService
import app.nextsay.provider.ProviderConfigValidation
import app.nextsay.settings.ApiSettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var providerStatus: TextView
    private lateinit var diagnosticsStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var imeStatus: TextView
    private lateinit var imeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = 24.dp
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(244, 246, 245))
        }
        root.addView(TextView(this).apply {
            text = "NextSay"
            textSize = 30f
            setTextColor(Color.rgb(26, 31, 29))
        })
        root.addView(TextView(this).apply {
            text = "NextSay 可以通过浮动面板生成回复，也可以作为轻量 AI 输入法在微信和 QQ 中稳定写入候选。所有候选只会写入输入框，由你亲自发送。"
            textSize = 16f
            setTextColor(Color.rgb(72, 82, 78))
            setPadding(0, 12.dp, 0, 24.dp)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        providerStatus = TextView(this).apply {
            textSize = 15f
            setPadding(0, 0, 0, 8.dp)
        }
        root.addView(providerStatus)
        root.addView(Button(this).apply {
            text = "配置模型服务"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ApiSettingsActivity::class.java))
            }
        })
        accessibilityStatus = TextView(this).apply {
            textSize = 15f
            setPadding(0, 20.dp, 0, 8.dp)
        }
        root.addView(accessibilityStatus)
        accessibilityButton = Button(this).apply {
            text = "开启无障碍服务"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(accessibilityButton)
        imeStatus = TextView(this).apply {
            textSize = 15f
            setPadding(0, 20.dp, 0, 8.dp)
        }
        root.addView(imeStatus)
        imeButton = Button(this).apply {
            text = "启用 NextSay 输入法"
            setOnClickListener { openImeSetup() }
        }
        root.addView(imeButton)
        root.addView(TextView(this).apply {
            text = "隐私说明\n\n• 微信或 QQ 位于前台且页面变化时，会自动提取当前可见对话\n• 截图仅在本机 OCR，不保存、不上传\n• 只有你主动点击生成时，识别文字才会发送到回复服务\n• 上传前会遮盖手机号、邮箱和长数字\n• NextSay 绝不会自动粘贴或发送消息"
            textSize = 14f
            setTextColor(Color.rgb(72, 82, 78))
            setPadding(0, 28.dp, 0, 0)
        })
        diagnosticsStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(168, 55, 55))
            setPadding(0, 20.dp, 0, 6.dp)
        }
        root.addView(diagnosticsStatus)
        root.addView(Button(this).apply {
            text = "诊断日志"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
            }
        })
        root.addView(Button(this).apply {
            text = "清空本地聊天历史"
            setOnClickListener { confirmClearHistory() }
        })
        setContentView(root)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (!::accessibilityStatus.isInitialized) return
        val dependencies = nextSayDependencies
        val providerConfig = dependencies.providerConfigStore.load()
        val providerValidation = providerConfig?.let {
            dependencies.providerConfigValidator.validate(it.baseUrl, it.apiKey, it.model)
        }
        if (providerValidation is ProviderConfigValidation.Valid) {
            providerStatus.text =
                "状态：模型服务已配置（${providerValidation.host} / ${providerValidation.config.model}）"
            providerStatus.setTextColor(Color.rgb(23, 107, 77))
        } else {
            providerStatus.text = "状态：请先配置模型服务"
            providerStatus.setTextColor(Color.rgb(168, 55, 55))
        }
        val hasPriorCrash = dependencies.diagnostics.events().any {
            it.type == DiagnosticEventType.APP_CRASHED
        }
        diagnosticsStatus.text = if (hasPriorCrash) {
            "检测到上次异常，可导出诊断日志"
        } else {
            "诊断日志仅保存在本机"
        }
        diagnosticsStatus.setTextColor(
            if (hasPriorCrash) Color.rgb(168, 55, 55) else Color.rgb(72, 82, 78),
        )
        val enabled = isNextSayAccessibilityEnabled()
        accessibilityStatus.text = if (enabled) "状态：无障碍服务已开启" else "状态：无障碍服务未开启"
        accessibilityStatus.setTextColor(
            if (enabled) Color.rgb(23, 107, 77) else Color.rgb(168, 55, 55),
        )
        accessibilityButton.text = if (enabled) "管理无障碍服务" else "开启无障碍服务"

        val imeEnabled = isNextSayImeEnabled()
        imeStatus.text = if (imeEnabled) "状态：NextSay 输入法已启用" else "状态：NextSay 输入法未启用"
        imeStatus.setTextColor(
            if (imeEnabled) Color.rgb(23, 107, 77) else Color.rgb(168, 55, 55),
        )
        imeButton.text = if (imeEnabled) "选择 NextSay 输入法" else "启用 NextSay 输入法"
    }

    private fun isNextSayAccessibilityEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val enabledComponents = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapTo(mutableSetOf()) { service ->
                val info = service.resolveInfo.serviceInfo
                ComponentName(info.packageName, info.name).flattenToString()
            }
        val target = ComponentName(this, NextSayAccessibilityService::class.java).flattenToString()
        return AccessibilityServiceStatus().isEnabled(enabledComponents, target)
    }

    private fun isNextSayImeEnabled(): Boolean {
        val manager = getSystemService(InputMethodManager::class.java)
        val enabledIds = manager.enabledInputMethodList.mapTo(mutableSetOf()) { it.id }
        val target = ComponentName(this, NextSayInputMethodService::class.java).flattenToString()
        return ImeServiceStatus().isEnabled(enabledIds, target)
    }

    private fun openImeSetup() {
        if (isNextSayImeEnabled()) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        } else {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("清空本地聊天历史")
            .setMessage("所有联系人在 NextSay 中保存的文字历史都会删除，无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ -> clearHistory() }
            .show()
    }

    private fun clearHistory() {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ConversationHistoryRepository(
                        NextSayDatabase.get(this@MainActivity).conversationDao(),
                        AndroidKeystoreMessageCipher(),
                    ).clearAll()
                }
            }
            Toast.makeText(
                this@MainActivity,
                if (result.isSuccess) "本地聊天历史已清空" else "清空失败，请重试",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
