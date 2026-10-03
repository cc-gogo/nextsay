package app.nextsay

import android.app.Activity
import android.app.AlertDialog
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import app.nextsay.accessibility.NextSayAccessibilityService
import app.nextsay.contacts.ContactSettingsActivity
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.diagnostics.DiagnosticsActivity
import app.nextsay.ime.NextSayInputMethodService
import app.nextsay.provider.ProviderConfigValidation
import app.nextsay.settings.ApiSettingsActivity
import app.nextsay.ui.NextSayUi
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ui by lazy { NextSayUi(this) }
    private lateinit var providerStatus: TextView
    private lateinit var diagnosticsStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityButton: Button
    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var imeStatus: TextView
    private lateinit var imeButton: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ui.screen(this, "NextSay", "下一句，说得更贴心。")
        ui.add(root, ui.text("版本 ${BuildConfig.VERSION_NAME} · 构建 ${BuildConfig.VERSION_CODE}", 13f, tint = ui.muted), 8)
        val assistant = ui.section(root, "你的聊天助手", "帮你准备回复，选择和发送始终由你决定。")
        accessibilityStatus = ui.text("正在检查服务…", 14f, tint = ui.muted)
        ui.add(assistant, accessibilityStatus, 16)
        accessibilityButton = ui.button("开启聊天助手", primary = true) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        ui.add(assistant, accessibilityButton, 12)
        overlayStatus = ui.text("正在检查悬浮窗权限…", 14f, tint = ui.muted)
        ui.add(assistant, overlayStatus, 8)
        overlayButton = ui.button("允许稳定显示悬浮球") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        ui.add(assistant, overlayButton, 8)
        ui.add(assistant, ui.text("在微信或 QQ 中，点悬浮球展开或收起候选；点“生成新回复”刷新，长按设置。", 13f, tint = ui.muted), 12)

        val contacts = ui.section(root, "聊天对象", "关系、补充资料与长期记忆，让回复更符合你们的相处方式。")
        ui.add(contacts, ui.button("管理聊天对象") { startActivity(Intent(this, ContactSettingsActivity::class.java)) }, 12)

        val model = ui.section(root, "模型服务", "全软件共用一套 API 配置。")
        providerStatus = ui.text("正在检查配置…", 14f, tint = ui.muted)
        ui.add(model, providerStatus, 12)
        ui.add(model, ui.button("配置模型服务") { startActivity(Intent(this, ApiSettingsActivity::class.java)) }, 12)

        val input = ui.section(root, "AI 输入法", "可选：通过输入法将候选写入聊天输入框，不会替你发送。")
        imeStatus = ui.text("", 14f, tint = ui.muted)
        ui.add(input, imeStatus, 12)
        imeButton = ui.button("启用 NextSay 输入法") { openImeSetup() }
        ui.add(input, imeButton, 12)

        val support = ui.section(root, "隐私与帮助")
        diagnosticsStatus = ui.text("", 13f, tint = ui.muted)
        ui.add(support, diagnosticsStatus, 10)
        ui.add(support, ui.button("诊断日志") { startActivity(Intent(this, DiagnosticsActivity::class.java)) }, 10)
        ui.add(support, ui.button("隐私说明") { showPrivacy() }, 10)
        ui.add(support, ui.button("清空聊天记忆", destructive = true) { confirmClearHistory() }, 10)
    }
    override fun onResume() {
        super.onResume()
        if (!::accessibilityStatus.isInitialized) return
        val dependencies = nextSayDependencies
        val config = dependencies.providerConfigStore.load()
        val validated = config?.let { dependencies.providerConfigValidator.validate(it.baseUrl, it.apiKey, it.model) }
        providerStatus.text = if (validated is ProviderConfigValidation.Valid) "已配置 ${validated.config.model}\n${validated.host}" else "尚未配置模型服务"
        providerStatus.setTextColor(if (validated is ProviderConfigValidation.Valid) ui.accent else ui.muted)
        val crash = dependencies.diagnostics.events().any { it.type == DiagnosticEventType.APP_CRASHED }
        diagnosticsStatus.text = if (crash) "发现异常记录，可导出日志帮助排查。" else "诊断日志仅保存在本机。"
        diagnosticsStatus.setTextColor(if (crash) ui.danger else ui.muted)
        val enabled = isNextSayAccessibilityEnabled()
        accessibilityStatus.text = if (enabled) "聊天助手已开启" else "还差一步：开启无障碍服务"
        accessibilityStatus.setTextColor(if (enabled) ui.accent else ui.muted)
        accessibilityButton.text = if (enabled) "管理聊天助手" else "开启聊天助手"
        val overlayAllowed = Settings.canDrawOverlays(this)
        overlayStatus.text = if (overlayAllowed) {
            "悬浮窗权限已开启，键盘打开时保持显示"
        } else {
            "建议开启悬浮窗权限，避免小米输入法收起悬浮球"
        }
        overlayStatus.setTextColor(if (overlayAllowed) ui.accent else ui.muted)
        overlayButton.text = if (overlayAllowed) "管理悬浮窗权限" else "允许稳定显示悬浮球"
        val imeEnabled = isNextSayImeEnabled()
        imeStatus.text = if (imeEnabled) "NextSay 输入法已启用" else "尚未启用，不影响悬浮窗生成"
        imeStatus.setTextColor(if (imeEnabled) ui.accent else ui.muted)
        imeButton.text = if (imeEnabled) "选择 NextSay 输入法" else "启用 NextSay 输入法"
    }
    private fun isNextSayAccessibilityEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapTo(mutableSetOf()) { val service = it.resolveInfo.serviceInfo; ComponentName(service.packageName, service.name).flattenToString() }
        return AccessibilityServiceStatus().isEnabled(enabled, ComponentName(this, NextSayAccessibilityService::class.java).flattenToString())
    }
    private fun isNextSayImeEnabled(): Boolean {
        val enabled = getSystemService(InputMethodManager::class.java).enabledInputMethodList.mapTo(mutableSetOf()) { it.id }
        return ImeServiceStatus().isEnabled(enabled, ComponentName(this, NextSayInputMethodService::class.java).flattenToString())
    }
    private fun openImeSetup() {
        if (isNextSayImeEnabled()) getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        else startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }
    private fun showPrivacy() { AlertDialog.Builder(this).setTitle("隐私说明")
        .setMessage("只识别亮屏前台可见的微信或 QQ 单聊，不读取私有数据库。\n\n截图仅在本机识别，不保存、不上传。\n\n手动生成或开启对象自动生成后，相关聊天文字、资料和记忆将发送到你配置的 API，可能收费。\n\n本机档案加密；遮盖部分敏感格式不保证完全匿名。\n\n自动模式只生成候选，不自动输入或发送。")
        .setPositiveButton("知道了", null).show() }
    private fun confirmClearHistory() { AlertDialog.Builder(this).setTitle("清空聊天记忆")
        .setMessage("删除 NextSay 保存的所有聊天文字，无法恢复。保留对象资料及 API 配置，不影响微信和 QQ 本身的聊天记录。")
        .setNegativeButton("取消", null).setPositiveButton("清空") { _, _ ->
            scope.launch {
                try { withContext(Dispatchers.IO) { nextSayDependencies.contactStore.clearAll() }; toast("聊天记忆已清空") }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { toast("清空失败，请重试") }
            }
        }.show() }
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
