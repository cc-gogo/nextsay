package app.nextsay.contacts

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import app.nextsay.nextSayDependencies
import app.nextsay.ui.NextSayUi
import app.nextsay.ui.RelationshipChoices
import app.nextsay.ui.LoverReplyModeChoices
import app.nextsay.provider.LoverReplyModes
import kotlinx.coroutines.*

/** Private editor. No provider requests and no chat messages are sent here. */
class ContactSettingsActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store get() = nextSayDependencies.contactStore
    private val ui by lazy { NextSayUi(this) }
    private lateinit var root: LinearLayout
    private var pkg = "com.tencent.mm"
    private var title = ""
    private var pageBack: (() -> Unit)? = null
    private var pageEpoch = 0L
    private fun screen(name: String, subtitle: String, back: () -> Unit): LinearLayout {
        pageEpoch++
        return ui.screen(this, name, subtitle, back)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra("package") ?: pkg
        title = intent.getStringExtra("title").orEmpty()
        reload()
        if (!store.simpleMemoryNoticeAccepted) confirm("长期记忆说明",
            "默认在本机加密保存识别过的聊天，生成时会将相关记忆和资料发送到你设置的模型服务。不会自动发送消息；已明确关闭保存的对象保持关闭。你可以随时清除记忆。") {
            work { store.acceptSimpleMemoryDefaults(); withContext(Dispatchers.Main) { reload() } }
        }
    }
    private fun reload() {
        pageBack = null
        root = screen("聊天对象", "让每一次回复，更了解你们。", back = { finish() })
        val epoch = pageEpoch
        val platform = pkg
        val modes = LinearLayout(this)
        listOf("微信" to "com.tencent.mm", "QQ" to "com.tencent.mobileqq").forEach { (label, platform) ->
            modes.addView(ui.button(label, primary = pkg == platform) { pkg = platform; title = ""; reload() },
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) })
        }
        ui.add(root, modes, 20)
        if (title.isNotBlank()) {
            val current = ui.section(root, "当前聊天", title)
            ui.add(current, ui.button("保存为聊天对象", primary = true) {
                confirm("保存聊天对象", "请确认是单人聊天。若下面已有这个人，直接打开他的资料并保存关系和要求，无需重复创建。") {
                    work {
                        val existing = store.list(pkg, true).filter { it.name == title }
                        val person = existing.singleOrNull() ?: store.create(pkg, title)
                        withContext(Dispatchers.Main) { editor(person) }
                    }
                }
            }, 12)
        }
        val list = ui.section(root, "我的对象", "记忆默认长期保存，自动生成由你决定。")
        ui.add(root, ui.toggle("暂停所有自动生成", store.totalPaused) { store.totalPaused = it }, 8)
        work {
            store.initialize()
            val contacts = store.list(platform, true)
            val selected = intent.getStringExtra("contactId")
            withContext(Dispatchers.Main) {
                if (epoch != pageEpoch) return@withContext
                if (contacts.isEmpty()) ui.add(list, ui.text("还没有聊天对象\n在聊天中长按悬浮球，打开对象资料即可保存。", tint = ui.muted), 16)
                contacts.filter { it.name != "选择聊天" }.forEach { contact ->
                    val row = ui.column(14).apply { background = ui.shape(ui.inset, 12); isClickable = true; isFocusable = true }
                    ui.add(row, ui.text(contact.name, 18f, true))
                    val modeLabel = if (contact.entity.relationship == "lover") " · ${LoverReplyModes.label(contact.entity.replyMode)}" else ""
                    ui.add(row, ui.text("${RelationshipChoices.label(contact.entity.relationship)}$modeLabel  ·  ${if (contact.entity.autoEnabled) "自动生成" else "手动生成"}", 13f, tint = ui.muted), 5)
                    row.setOnClickListener { editor(contact) }
                    ui.add(list, row, 10)
                }
                ui.add(root, ui.button("更多管理") { listManagement(contacts) }, 20)
                selected?.let { id -> contacts.firstOrNull { it.entity.id == id }?.let(::editor) }
                intent.removeExtra("contactId")
            }
        }
    }
    private fun editor(contact: ContactSnapshot) {
        var saveInFlight = false
        pageBack = { if (!saveInFlight) reload() else toast("正在保存资料，请稍候") }
        root = screen(contact.name, "对象资料", back = { pageBack?.invoke() })
        val editorEpoch = pageEpoch
        val chatPackage = pkg
        val chatTitle = title
        val accountSpace = store.accountSpace
        fun stillEditing() = pageEpoch == editorEpoch && !isFinishing && !isDestroyed && store.accountSpace == accountSpace
        var submitSave: () -> Unit = {}
        var bindingButton: Button? = null
        lateinit var saveButton: Button
        lateinit var managementButton: Button
        if (title.isNotBlank() && contact.entity.sourcePackage == pkg && contact.entity.accountSpace in setOf(store.accountSpace, "legacy")) {
            val epoch = pageEpoch
            val hint = ui.text("正在核对当前聊天关联…", 13f, tint = ui.muted)
            ui.add(root, hint, 18)
            // Use the same save+confirm path so unsaved edits are not discarded by linking.
            val linkButton = ui.button("确认当前聊天对象", primary = true) { submitSave() }
            bindingButton = linkButton
            ui.add(root, linkButton, 8)
            work {
                val linked = contact.entity.confirmed && contact.entity.accountSpace == accountSpace && store.isLinked(contact.entity.id, chatPackage, chatTitle)
                withContext(Dispatchers.Main) {
                    if (pageEpoch != epoch) return@withContext
                    val same = store.sameName(contact.name, chatTitle)
                    hint.text = if (linked || same) "保存后，当前聊天将直接使用这份关系和资料" else "当前聊天名称不同，请确认是否为同一个人：$title"
                    linkButton.visibility = if (linked || same) android.view.View.GONE else android.view.View.VISIBLE
                }
            }
        }
        val relationCard = ui.section(root, "你们的关系")
        val modeArea = ui.column()
        val replyMode = LoverReplyModeChoices(this, contact.entity.replyMode)
        ui.add(modeArea, ui.text("恋人回复风格", 14f, true), 12)
        ui.add(modeArea, replyMode, 8)
        ui.add(modeArea, ui.text("自然：贴合情境、承接情绪。黄毛：简短、自信、俏皮。补充要求始终优先。", 12f, tint = ui.muted), 6)
        modeArea.visibility = if (contact.entity.relationship == "lover") android.view.View.VISIBLE else android.view.View.GONE
        val relation = RelationshipChoices(this, contact.entity.relationship) { selected ->
            modeArea.visibility = if (selected == "lover") android.view.View.VISIBLE else android.view.View.GONE
        }
        ui.add(relationCard, relation, 10)
        ui.add(relationCard, modeArea)
        val material = ui.section(root, "补充资料", "写下称呼、性格、相处背景或回复偏好，AI 生成时会参考。")
        val existing = ContactMaterial.display(contact.details, contact.preferences, contact.memory)
        val details = ui.field(existing, "例如：认识多年的朋友，喜欢电影；回复自然一点，别太正式。", 5)
        ui.add(material, details, 12)
        val behavior = ui.section(root, "回复方式", "自动模式只生成候选，仍由你亲自选择和发送。")
        val auto = ui.toggle("收到新消息时自动生成", contact.entity.autoEnabled)
        ui.add(behavior, auto, 8)
        ui.add(behavior, ui.text(if (contact.entity.saveHistory) "长期记忆已开启" else "此对象曾关闭保存，仍保持关闭", 13f, tint = ui.muted), 6)
        fun saving(value: Boolean) {
            saveInFlight = value
            saveButton.isEnabled = !value
            bindingButton?.isEnabled = !value
            managementButton.isEnabled = !value
            relation.isEnabled = !value; replyMode.isEnabled = !value; details.isEnabled = !value; auto.isEnabled = !value
        }
        saveButton = ui.button("保存资料", primary = true) {
            if (!stillEditing() || saveInFlight) return@button
            saving(true)
            var committing = false
            val materialChanged = details.text.toString() != existing
            val material = ContactMaterial.edited(details.text.toString())
            val updated = contact.copy(entity = contact.entity.copy(relationship = relation.value, autoEnabled = auto.isChecked,
                replyMode = LoverReplyModes.normalize(relation.value, replyMode.value),
                useMemory = contact.entity.saveHistory && (contact.entity.useMemory || store.simpleMemoryNoticeAccepted)),
                details = if (materialChanged) material.details else contact.details,
                preferences = if (materialChanged) material.preferences else contact.preferences, memory = if (materialChanged) material.memory else contact.memory)
            fun persist(link: Boolean) {
                if (!stillEditing()) return
                committing = true
                work {
                    try {
                        if (link) store.saveAndLink(updated, chatPackage, chatTitle) else store.saveProfile(updated)
                        withContext(Dispatchers.Main) {
                            if (stillEditing()) { toast("已保存，对应聊天将使用这份关系和资料"); reload() }
                        }
                    } finally {
                        withContext(Dispatchers.Main) { if (stillEditing()) saving(false) }
                    }
                }
            }
            val save = {
                // Same managed name needs no second operation; different names need one identity confirmation.
                if (chatTitle.isNotBlank() && contact.entity.sourcePackage == chatPackage && contact.entity.accountSpace in setOf(accountSpace, "legacy")) {
                    if (store.sameName(contact.name, chatTitle)) persist(true)
                    else work {
                        try {
                            val linked = contact.entity.confirmed && contact.entity.accountSpace == accountSpace && store.isLinked(contact.entity.id, chatPackage, chatTitle)
                            withContext(Dispatchers.Main) {
                                if (!stillEditing()) return@withContext
                                if (linked) persist(false)
                                else AlertDialog.Builder(this@ContactSettingsActivity).setTitle("用于当前聊天吗？")
                                    .setMessage("请确认“${contact.name}”和当前聊天“$chatTitle”是同一个人。关联后使用刚选的关系和资料，已有记忆保留；不同的人请选择仅保存。")
                                    .setPositiveButton("保存并用于当前聊天") { _, _ -> persist(true) }
                                    .setNeutralButton("仅保存资料") { _, _ -> persist(false) }
                                    .setNegativeButton("取消", null).show().apply {
                                        setOnDismissListener { if (!committing && stillEditing()) saving(false) }
                                    }
                            }
                        } catch (error: Exception) {
                            withContext(Dispatchers.Main) { if (stillEditing()) saving(false) }
                            throw error
                        }
                    }
                } else persist(false)
                Unit
            }
            if (auto.isChecked && (!contact.entity.autoEnabled || !contact.entity.confirmed || contact.entity.accountSpace != accountSpace)) {
                var continuing = false
                AlertDialog.Builder(this).setTitle("开启自动生成")
                    .setMessage("当前聊天、对象资料和相关记忆会发送到你配置的模型服务，可能收费。不会自动输入或发送消息。")
                    .setNegativeButton("取消", null).setPositiveButton("确认") { _, _ -> continuing = true; save() }
                    .show().apply { setOnDismissListener { if (!continuing && stillEditing()) saving(false) } }
            } else save()
        }
        submitSave = { saveButton.performClick(); Unit }
        ui.add(root, saveButton, 22)
        managementButton = ui.button("记忆与对象管理") { if (!saveInFlight) management(contact) }
        ui.add(root, managementButton, 10)
    }
    private fun management(contact: ContactSnapshot) {
        pageBack = { editor(contact) }
        root = screen("对象管理", contact.name, back = { editor(contact) })
        val memory = ui.section(root, "记忆")
        ui.add(memory, ui.button("查看已保存的聊天") { history(contact) }, 10)
        ui.add(memory, ui.button("清除聊天记忆", destructive = true) {
            confirm("清除聊天记忆", "永久删除这个对象已保存的聊天文字，保留补充资料。") {
                work { store.clearHistory(contact.entity.id); withContext(Dispatchers.Main) { toast("聊天记忆已清除") } }
            }
        }, 10)
        val objects = ui.section(root, "对象")
        ui.add(objects, ui.button("合并重复对象") { mergePicker(contact) }, 10)
        if (title.isNotBlank()) ui.add(objects, ui.button("解除当前聊天关联") {
            confirm("解除关联", "不删除已有资料和记忆，仅解除当前聊天名称与这份资料的关联。") {
                work { store.unlink(contact.entity.id, pkg, title); withContext(Dispatchers.Main) { reload() } }
            }
        }, 10)
        ui.add(objects, ui.button("删除对象", destructive = true) {
            confirm("删除对象", "这个对象的资料和记忆将永久删除，无法恢复。") {
                work { store.delete(contact.entity.id); withContext(Dispatchers.Main) { reload() } }
            }
        }, 10)
    }
    private fun mergePicker(target: ContactSnapshot) {
        work {
            val others = store.list(pkg, true).filter { it.entity.id != target.entity.id }
            withContext(Dispatchers.Main) {
                if (others.isEmpty()) { toast("没有其他可合并的对象"); return@withContext }
                AlertDialog.Builder(this@ContactSettingsActivity).setTitle("哪一份也是“${target.name}”？")
                    .setItems(others.map { it.name }.toTypedArray()) { _, index ->
                        val source = others[index]
                        confirm("合并这两份资料", "请确认“${target.name}”和“${source.name}”是同一个人。两边的聊天记忆和资料全部保留，合并后暂时关闭自动生成。") {
                            work { val merged = store.merge(target.entity.id, source.entity.id); withContext(Dispatchers.Main) { toast("已合并，记忆已保留"); editor(merged) } }
                        }
                    }.setNegativeButton("取消", null).show()
            }
        }
    }
    private fun listManagement(contacts: List<ContactSnapshot>) {
        pageBack = ::reload
        root = screen("更多管理", "日常使用无需调整这里。", back = ::reload)
        val accounts = ui.section(root, "多个账号", "只有切换微信或 QQ 登录账号时，才需要切换独立的记忆分区。")
        val name = ui.field(store.accountSpace, "账号名称")
        ui.add(accounts, name, 12)
        ui.add(accounts, ui.button("切换账号") { store.accountSpace = name.text.toString(); reload() }, 10)
        val hidden = contacts.filter { it.name == "选择聊天" }
        if (hidden.isNotEmpty()) {
            val archived = ui.section(root, "待整理记录", "旧版可能把非聊天画面识别成对象。记录未删除，可自行检查。")
            hidden.forEach { person -> ui.add(archived, ui.button(person.name) { editor(person) }, 10) }
        }
        val defaults = ui.section(root, "关系偏好", "可选：给同一类关系设置共用回复要求。")
        val relation = RelationshipChoices(this)
        ui.add(defaults, relation, 10)
        ui.add(defaults, ui.button("编辑关系偏好") {
            work {
                val rules = store.relationshipRules(relation.value)
                withContext(Dispatchers.Main) {
                    val field = ui.field(rules, "这类关系的回复要求", 4)
                    AlertDialog.Builder(this@ContactSettingsActivity).setTitle(RelationshipChoices.label(relation.value) + "的共用偏好")
                        .setView(field).setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
                            val text = field.text.toString(); val value = relation.value
                            work { store.saveRelationshipRules(value, text); withContext(Dispatchers.Main) { toast("已保存") } }
                        }.show()
                }
            }
        }, 10)
    }
    private fun history(contact: ContactSnapshot) {
        pageBack = { management(contact) }
        root = screen("聊天记忆", "${contact.name} · 本机识别过的文字，不是完整微信聊天记录。", back = { management(contact) })
        val epoch = pageEpoch
        work {
            val items = store.messages(contact.entity.id)
            withContext(Dispatchers.Main) {
                if (epoch != pageEpoch) return@withContext
                if (items.isEmpty()) ui.add(root, ui.text("还没有保存聊天记忆。", tint = ui.muted), 24)
                items.forEach { (row, message) ->
                    val card = ui.section(root, when (message.role) { app.nextsay.context.MessageRole.ME -> "我"; app.nextsay.context.MessageRole.OTHER -> "对方"; else -> "说话人未确定" },
                        "识别于 ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.observedAt))}")
                    ui.add(card, ui.text(message.text), 10)
                    ui.add(card, ui.button("删除这条记忆", destructive = true) {
                        confirm("删除这条记忆", "删除后无法恢复。") { work { store.deleteObservation(row.id); withContext(Dispatchers.Main) { history(contact) } } }
                    }, 10)
                }
            }
        }
    }
    private fun work(block: suspend () -> Unit) { scope.launch { try { withContext(Dispatchers.IO) { block() } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { toast("操作失败，请重试；原资料已保留") } } }
    private fun confirm(title: String, message: String, action: () -> Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消", null).setPositiveButton("确认") { _, _ -> action() }.show() }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    @Suppress("DEPRECATION") @Deprecated("Activity compatibility") override fun onBackPressed() { pageBack?.invoke() ?: super.onBackPressed() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
