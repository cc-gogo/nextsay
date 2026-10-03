package app.nextsay.contacts

import android.content.Context
import androidx.room.withTransaction
import app.nextsay.capture.CapturedConversation
import app.nextsay.context.*
import app.nextsay.history.AndroidKeystoreMessageCipher
import app.nextsay.history.MessageCipher
import app.nextsay.history.HistoryLoadResult
import app.nextsay.history.db.NextSayDatabase
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ContactSnapshot(val entity: ContactEntity, val name: String, val details: String, val preferences: String, val memory: String)
data class ContactResolution(val matched: List<ContactSnapshot>, val suggested: List<ContactSnapshot>)
data class GenerationExtras(val relationship: String = "unspecified", val relationshipRules: String = "", val details: String = "", val preferences: String = "", val memory: String = "", val replyMode: String = "natural")

/** All sensitive text is encrypted; HMAC lookup never uses provider/API keys. */
class ContactStore(context: Context, private val db: NextSayDatabase = NextSayDatabase.get(context),
    private val cipher: MessageCipher = AndroidKeystoreMessageCipher(),
    private val titleHasher: (String) -> String = ContactTitleIndex::hash,
) {
    private val dao = db.contactDao()
    private val prefs = context.getSharedPreferences("nextsay-contacts", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val epoch = AtomicLong()
    private var globalInvalidation = 0L
    private val contactInvalidations = mutableMapOf<String, Long>()
    private val memoryWarmup = Mutex()
    @Volatile private var initialized = false
    // Ciphertext is the cache key, so another activity's saved edits cannot reuse
    // stale plaintext. This is bounded process memory, never unencrypted storage.
    private val plaintextCache = LinkedHashMap<String, String>(128, .75f, true)
    private var cachedCharacters = 0
    private var pendingSelection: Triple<String, String, String>? = null
    private var pendingSpace: String? = null
    fun consumeSelection(pkg: String, title: String): String? {
        val selection = pendingSelection ?: return null
        if (selection.first != pkg || selection.second != title || pendingSpace != accountSpace) return null
        pendingSelection = null
        return selection.third
    }
    private fun select(pkg: String, title: String, id: String) { pendingSelection = Triple(pkg, title, id); pendingSpace = accountSpace }
    var totalPaused: Boolean
        get() = prefs.getBoolean("auto-paused", false)
        set(value) { prefs.edit().putBoolean("auto-paused", value).apply() }
    val simpleMemoryNoticeAccepted: Boolean get() = prefs.getBoolean("simple-memory-notice-v1", false)
    suspend fun acceptSimpleMemoryDefaults() {
        initialize()
        db.withTransaction {
            dao.allProfiles().filter { it.saveHistory }.forEach { dao.save(it.copy(useMemory = true)) }
        }
        prefs.edit().putBoolean("simple-memory-notice-v1", true).apply()
    }
    var accountSpace: String
        get() = prefs.getString("account-space", "default")!!
        set(value) {
            invalidateHistory()
            try { prefs.edit().putString("account-space", value.trim().take(80).ifBlank { "default" }).apply() }
            finally { invalidateHistory(); clearPlaintextCache() }
        }
    private fun encrypt(text: String) = if (text.isEmpty()) "" else cipher.encrypt(text)
    private fun decrypt(text: String, expectedEpoch: Long = historyEpoch): String {
        if (text.isEmpty()) return ""
        if (expectedEpoch != historyEpoch) return ""
        synchronized(plaintextCache) { plaintextCache[text]?.let { return it } }
        val plain = cipher.decrypt(text)
        synchronized(plaintextCache) {
            if (expectedEpoch == historyEpoch && !plaintextCache.containsKey(text)) {
                plaintextCache[text] = plain
                cachedCharacters += text.length + plain.length
                while (plaintextCache.size > 2048 || cachedCharacters > 256_000) {
                    val entry = plaintextCache.entries.iterator().next()
                    cachedCharacters -= entry.key.length + entry.value.length
                    plaintextCache.remove(entry.key)
                }
            }
        }
        return plain
    }
    private fun clearPlaintextCache() = synchronized(plaintextCache) { plaintextCache.clear(); cachedCharacters = 0 }
    private fun snapshot(entity: ContactEntity, expectedEpoch: Long = historyEpoch) = ContactSnapshot(entity,
        decrypt(entity.encryptedName, expectedEpoch), decrypt(entity.encryptedDetails, expectedEpoch),
        decrypt(entity.encryptedPreferences, expectedEpoch), decrypt(entity.encryptedMemory, expectedEpoch))
    private fun hash(title: String) = titleHasher(ContactEvidence.normalize(title).lowercase())
    suspend fun initialize() {
        if (initialized) return
        mutex.withLock {
        if (initialized) return@withLock
        db.withTransaction {
            val legacyRows = dao.legacy()
            for (legacy in legacyRows) {
                val id = UUID.randomUUID().toString()
                val excluded = db.conversationDao().isTitleExcluded(legacy.sourcePackage, legacy.normalizedTitle)
                val entity = ContactEntity(id, legacy.sourcePackage, "legacy", encrypt(legacy.displayTitle), saveHistory = !excluded, useMemory = !excluded, confirmed = false)
                dao.save(entity)
                dao.alias(ContactAlias(legacy.sourcePackage, "legacy", hash(legacy.displayTitle), id, entity.encryptedName, System.currentTimeMillis()))
                dao.observe(db.conversationDao().loadMessages(legacy.id).map {
                    ContactObservation(contactId = id, segmentId = "legacy-${legacy.id}", role = it.role, encryptedText = it.encryptedText, confidence = it.confidence, observedAt = it.observedAt)
                })
                dao.scrubLegacy(legacy.id, "migrated:$id", entity.encryptedName)
                dao.imported(ContactLegacyImport(legacy.id, id))
                dao.clearLegacyMessages(legacy.id)
            }
            // Import metadata is structural, never inferred from arbitrary user title prefixes.
            for (excluded in dao.exclusions()) {
                dao.exclude(ContactExcludedTitle(excluded.sourcePackage, hash(excluded.normalizedTitle)))
            }
            db.conversationDao().clearExcludedTitles()
        }
        initialized = true
        }
    }
    suspend fun list(pkg: String, includeLegacy: Boolean = false): List<ContactSnapshot> {
        val before = historyEpoch
        val result = (dao.list(pkg, accountSpace) + if (includeLegacy) dao.list(pkg, "legacy") else emptyList())
            .distinctBy { it.id }.map { snapshot(it, before) }
        return if (before == historyEpoch) result else emptyList()
    }
    suspend fun get(id: String): ContactSnapshot? {
        val before = historyEpoch
        val result = dao.get(id)?.let { snapshot(it, before) }
        return result.takeIf { before == historyEpoch }
    }
    suspend fun isLinked(id: String, pkg: String, title: String): Boolean {
        val person = dao.get(id) ?: return false
        if (title.isBlank() || !person.confirmed || person.sourcePackage != pkg || person.accountSpace != accountSpace) return false
        return dao.aliases(pkg, accountSpace, hash(title)).singleOrNull()?.contactId == id
    }
    suspend fun saveAndLink(snapshot: ContactSnapshot, pkg: String, title: String): ContactSnapshot = db.withTransaction {
        saveProfile(snapshot)
        link(snapshot.entity.id, pkg, title)
    }
    suspend fun menuResolution(capture: CapturedConversation): ContactResolution? = try {
        resolve(capture)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    suspend fun resolve(capture: CapturedConversation): ContactResolution {
        initialize()
        if (!capture.persistable) return ContactResolution(emptyList(), emptyList())
        val people = list(capture.context.sourcePackage, accountSpace == "default").filter {
            it.entity.confirmed && it.entity.accountSpace == accountSpace ||
                it.entity.accountSpace == "legacy" && accountSpace == "default" && hasManagedMaterial(it)
        }
        val eligibleIds = people.map { it.entity.id }.toSet()
        val titleAudits = people.mapNotNull { person ->
            dao.audits(person.entity.id).firstOrNull { sameName(decrypt(it.encryptedTitle), capture.title) }
                ?.let { person to it }
        }
        // Withdrawing the current object must not make a namesake appear uniquely eligible.
        val latestAudit = titleAudits.maxByOrNull { it.second.id }
        if (latestAudit?.second?.action == "unlink") {
            return ContactResolution(emptyList(), people.filter { sameName(it.name, capture.title) || it.entity.id == latestAudit.first.entity.id })
        }
        val aliases = dao.aliases(capture.context.sourcePackage, accountSpace, hash(capture.title))
            .mapNotNull { get(it.contactId) }.filter { it.entity.id in eligibleIds && !wasUnlinked(it.entity.id, capture.title) }
        // Use the unique managed name even when an old profile has no current alias.
        // Ignore OCR spacing/case only; letter substitutions still need evidence or a choice.
        val names = people.filter { sameName(it.name, capture.title) && !wasUnlinked(it.entity.id, capture.title) }
        val candidates = (aliases + names).distinctBy { it.entity.id }
        // An explicit object choice is durable; an ordinary profile save is not a choice between namesakes.
        val candidateIds = candidates.map { it.entity.id }.toSet()
        val selected = titleAudits.filter { it.first.entity.id in candidateIds && it.second.action == "select" }
            .maxByOrNull { it.second.id }?.first
        val matches = selected?.let { listOf(it) } ?: candidates
        if (matches.isNotEmpty()) {
            val unique = matches.singleOrNull()
            if (unique != null && unique.entity.accountSpace == "legacy") {
                saveProfile(unique.copy(entity = unique.entity.copy(autoEnabled = false)))
                return ContactResolution(listOf(requireNotNull(get(unique.entity.id))), emptyList())
            }
            return ContactResolution(matches, if (matches.size > 1) matches else emptyList())
        }
        val suggestions = people.filter { candidate ->
            // Segment order is only observed locally; never concatenate unrelated viewports
            // into identity evidence. Reliable role/text continuity is required.
            nearOcrTitle(candidate.name, capture.title) || messages(candidate.entity.id).groupBy { it.first.segmentId }.values.any { segment ->
                ContactEvidence.distinctiveOverlap(segment.map { it.second }, capture.context.messages)
            }
        }
        val jitter = suggestions.filter { candidate ->
            candidate.entity.confirmed && candidate.entity.accountSpace == accountSpace && nearOcrTitle(candidate.name, capture.title) &&
                !wasUnlinked(candidate.entity.id, capture.title) && messages(candidate.entity.id).groupBy { it.first.segmentId }.values.any { segment ->
                    ContactEvidence.distinctiveOverlap(segment.map { it.second }, capture.context.messages)
                }
        }.singleOrNull()
        // More than one possible identity always needs user confirmation. Never use a
        // fuzzy title alone, and never silently rename or combine existing profiles.
        return ContactResolution(if (jitter != null && suggestions.size == 1) listOf(jitter) else emptyList(), suggestions)
    }
    private fun hasManagedMaterial(contact: ContactSnapshot) = contact.entity.relationship != "unspecified" ||
        contact.details.isNotBlank() || contact.preferences.isNotBlank() || contact.memory.isNotBlank()
    private suspend fun wasUnlinked(id: String, title: String): Boolean = dao.audits(id)
        .firstOrNull { sameName(decrypt(it.encryptedTitle), title) }?.action == "unlink"
    private fun nameKey(name: String) = name.trim().replace(Regex("\\s+"), "").lowercase(java.util.Locale.ROOT)
    fun sameName(left: String, right: String) = left.isNotBlank() && right.isNotBlank() && nameKey(left) == nameKey(right)
    suspend fun create(pkg: String, title: String): ContactSnapshot {
        val excluded = dao.isExcluded(pkg, hash(title))
        val entity = ContactEntity(UUID.randomUUID().toString(), pkg, accountSpace, encrypt(title), saveHistory = !excluded, useMemory = !excluded)
        db.withTransaction { dao.save(entity); linkInternal(entity, title) }
        select(pkg, title, entity.id)
        return snapshot(entity)
    }
    private suspend fun linkInternal(entity: ContactEntity, title: String, previousName: String = entity.encryptedName, previousSpace: String = entity.accountSpace, action: String = "link") {
        val encrypted = encrypt(title)
        val now = System.currentTimeMillis()
        dao.alias(ContactAlias(entity.sourcePackage, accountSpace, hash(title), entity.id, encrypted, now))
        dao.audit(ContactBindingAudit(contactId = entity.id, encryptedTitle = encrypted, encryptedPreviousName = previousName,
            previousAccountSpace = previousSpace, action = action, observedAt = now))
    }
    suspend fun link(id: String, pkg: String, title: String): ContactSnapshot = db.withTransaction {
        val original = requireNotNull(dao.get(id))
        require(original.sourcePackage == pkg && original.accountSpace in setOf(accountSpace, "legacy"))
        val exclusion = dao.isExcluded(pkg, hash(title))
        val entity = original.copy(accountSpace = accountSpace, encryptedName = encrypt(title), confirmed = true, saveHistory = original.saveHistory && !exclusion, useMemory = original.useMemory && original.saveHistory && !exclusion)
        dao.save(entity); linkInternal(entity, title, original.encryptedName, original.accountSpace, action = "select"); select(pkg, title, id); snapshot(entity)
    }
    suspend fun unlink(id: String, pkg: String, title: String) = db.withTransaction {
        val entity = dao.get(id) ?: return@withTransaction
        if (title.isBlank() || entity.sourcePackage != pkg || entity.accountSpace !in setOf(accountSpace, "legacy")) return@withTransaction
        val equivalent = dao.contactAliases(id).filter {
            it.sourcePackage == pkg && it.accountSpace == accountSpace && sameName(decrypt(it.encryptedTitle), title)
        }
        dao.audit(ContactBindingAudit(contactId = id, encryptedTitle = encrypt(title), encryptedPreviousName = entity.encryptedName,
            previousAccountSpace = entity.accountSpace, action = "unlink", observedAt = System.currentTimeMillis()))
        equivalent.forEach { dao.unlink(pkg, accountSpace, it.titleHash, id) }
    }
    suspend fun bindingHistory(id: String): List<String> = dao.audits(id).map {
        val action = when (it.action) {
            "unlink" -> "解除"
            "select" -> "选择对象"
            "profile_save" -> "保存资料"
            "merge" -> "合并对象"
            else -> "关联"
        }
        "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.observedAt))} · $action ${decrypt(it.encryptedTitle)}\n原显示名：${decrypt(it.encryptedPreviousName)} · 原账号空间：${it.previousAccountSpace}"
    }
    suspend fun save(snapshot: ContactSnapshot) {
        // UI simplifies old fields into one input. Keep the complete locally edited
        // material; generation already has independent prompt budgets.
        val entity = snapshot.entity.copy(encryptedName = encrypt(snapshot.name.take(200)), encryptedDetails = encrypt(snapshot.details), encryptedPreferences = encrypt(snapshot.preferences), encryptedMemory = encrypt(snapshot.memory),
            replyMode = app.nextsay.provider.LoverReplyModes.normalize(snapshot.entity.relationship, snapshot.entity.replyMode))
        dao.save(entity)
    }
    /** A deliberate management-page save establishes this object's usable profile. */
    suspend fun saveProfile(snapshot: ContactSnapshot) = db.withTransaction {
        require(snapshot.entity.accountSpace in setOf(accountSpace, "legacy"))
        val managed = snapshot.copy(entity = snapshot.entity.copy(accountSpace = accountSpace, confirmed = true))
        save(managed)
        val saved = requireNotNull(dao.get(managed.entity.id))
        if (managed.name.isNotBlank()) {
            val alreadyIndexed = dao.aliases(saved.sourcePackage, accountSpace, hash(managed.name)).any { it.contactId == saved.id }
            if (!alreadyIndexed) linkInternal(saved, managed.name, action = "profile_save")
        }
    }
    suspend fun prepareContext(context: ChatContext, selectedId: String? = null): ChatContext {
        val title = context.replyRound?.title ?: return context.copy(contactId = null, generationExtras = null)
        if (title.isBlank() || Regex(".*[（(]\\d+[）)]$").matches(title))
            return context.copy(contactId = null, generationExtras = null)
        val resolution = resolve(CapturedConversation(title, context, true))
        val contact = (resolution.matched.firstOrNull { it.entity.id == selectedId } ?: resolution.matched.singleOrNull())
            ?.takeIf { it.entity.confirmed }
            ?: return context.copy(contactId = null, generationExtras = null)
        return context.copy(contactId = contact.entity.id, generationExtras = extras(contact.entity.id, context))
    }
    suspend fun merge(targetId: String, sourceId: String): ContactSnapshot = mutex.withLock {
        require(targetId != sourceId)
        db.withTransaction {
            val target = requireNotNull(get(targetId))
            val source = requireNotNull(get(sourceId))
            require(target.entity.sourcePackage == source.entity.sourcePackage)
            require(target.entity.accountSpace in setOf(accountSpace, "legacy") && source.entity.accountSpace in setOf(accountSpace, "legacy"))
            fun combine(a: String, b: String) = listOf(a, b).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
            val currentConfirmed = listOf(target, source).any { it.entity.confirmed && it.entity.accountSpace == accountSpace }
            val destinationSpace = if (currentConfirmed) accountSpace else target.entity.accountSpace
            val relationship = target.entity.relationship.takeUnless { it == "unspecified" } ?: source.entity.relationship
            val oldRelation = source.entity.relationship.takeIf { it != "unspecified" && it != relationship }
                ?.let { "${source.name} 原资料的关系：${app.nextsay.ui.RelationshipChoices.label(it)}" }.orEmpty()
            val merged = target.copy(entity = target.entity.copy(
                accountSpace = destinationSpace, confirmed = currentConfirmed || target.entity.confirmed,
                relationship = relationship,
                replyMode = app.nextsay.provider.LoverReplyModes.normalize(relationship,
                    if (target.entity.relationship == "unspecified") source.entity.replyMode else target.entity.replyMode),
                saveHistory = target.entity.saveHistory && source.entity.saveHistory,
                useMemory = target.entity.useMemory && source.entity.useMemory,
                autoEnabled = false, // Confirmation/merge never enables paid requests.
            ), details = combine(combine(target.details, source.details), oldRelation), preferences = combine(target.preferences, source.preferences), memory = combine(target.memory, source.memory))
            save(merged)
            val aliases = dao.contactAliases(targetId) + dao.contactAliases(sourceId)
            aliases.forEach { alias ->
                dao.alias(alias.copy(contactId = targetId))
                if (currentConfirmed && alias.accountSpace == "legacy") dao.alias(alias.copy(contactId = targetId, accountSpace = destinationSpace))
            }
            dao.moveObservations(sourceId, targetId)
            dao.moveAudits(sourceId, targetId)
            dao.moveImports(sourceId, targetId)
            dao.audit(ContactBindingAudit(contactId = targetId, encryptedTitle = source.entity.encryptedName,
                encryptedPreviousName = target.entity.encryptedName, previousAccountSpace = source.entity.accountSpace,
                action = "merge", observedAt = System.currentTimeMillis()))
            dao.deleteAliases(sourceId)
            dao.deleteContact(sourceId)
            requireNotNull(get(targetId))
        }
    }

    private fun nearOcrTitle(a: String, b: String): Boolean {
        val left = ContactEvidence.normalize(a).lowercase()
        val right = ContactEvidence.normalize(b).lowercase()
        if (minOf(left.length, right.length) < 4 || kotlin.math.abs(left.length - right.length) > 1) return false
        if (left.length == right.length) return left.zip(right).count { it.first != it.second } <= 1
        val shorter = if (left.length < right.length) left else right
        val longer = if (left.length < right.length) right else left
        return longer.indices.any { longer.removeRange(it, it + 1) == shorter }
    }
    fun relationshipRules(relationship: String) = decrypt(prefs.getString("rule-$relationship", "")!!)
    fun saveRelationshipRules(relationship: String, text: String) { prefs.edit().putString("rule-$relationship", encrypt(text.take(600))).apply() }
    suspend fun messages(id: String): List<Pair<ContactObservation, ChatMessage>> {
        val before = historyEpoch
        val result = dao.recent(id).reversed().map { row -> row to message(row, before) }
        return if (before == historyEpoch) result else emptyList()
    }
    private fun message(row: ContactObservation, expectedEpoch: Long = historyEpoch) = ChatMessage(MessageRole.entries.firstOrNull { it.wireValue == row.role } ?: MessageRole.UNKNOWN, decrypt(row.encryptedText, expectedEpoch), row.confidence)
    val historyEpoch: Long get() = epoch.get()
    fun isHistoryEpochCurrent(id: String, expectedEpoch: Long): Boolean = synchronized(epoch) {
        expectedEpoch >= globalInvalidation && expectedEpoch >= (contactInvalidations[id] ?: 0L)
    }
    private fun invalidateHistory(id: String? = null) = synchronized(epoch) {
        val next = epoch.incrementAndGet()
        if (id == null) { globalInvalidation = next; contactInvalidations.clear() }
        else contactInvalidations[id] = next
    }
    /** Single low-priority pool warmer. Requests never await this operation. */
    suspend fun warmMemory(id: String, expectedEpoch: Long = historyEpoch): Boolean {
        if (!memoryWarmup.tryLock()) return true
        try {
            val contact = dao.get(id) ?: return true
            if (!contact.confirmed || !contact.useMemory || contact.accountSpace != accountSpace) return true
            for (row in dao.recent(id)) {
                currentCoroutineContext().ensureActive()
                if (historyEpoch != expectedEpoch) return true
                decrypt(row.encryptedText, expectedEpoch)
                yield()
            }
            return true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return false }
        finally { memoryWarmup.unlock() }
    }
    suspend fun observe(id: String, capture: CapturedConversation, expectedEpoch: Long? = null): HistoryLoadResult = mutex.withLock {
        if (expectedEpoch != null && !isHistoryEpochCurrent(id, expectedEpoch)) return@withLock HistoryLoadResult(capture.context.messages, false)
        val before = historyEpoch
        val contact = dao.get(id) ?: return@withLock HistoryLoadResult(capture.context.messages, false)
        if (!capture.persistable || !contact.confirmed || !contact.saveHistory || contact.sourcePackage != capture.context.sourcePackage || contact.accountSpace != accountSpace) return@withLock HistoryLoadResult(capture.context.messages, false)
        val rows = dao.recent(id)
        val lastSegment = rows.firstOrNull()?.segmentId
        val existing = rows.filter { it.segmentId == lastSegment }.asReversed().map { message(it, before) }
        val overlap = ContactEvidence.forwardOverlap(existing, capture.context.messages)
        // A subset/older viewport adds no independent segment.
        val subset = capture.context.messages.isNotEmpty() && existing.windowed(capture.context.messages.size).any { window -> window.zip(capture.context.messages).all { ContactEvidence.same(it.first, it.second) } }
        if (subset) return@withLock HistoryLoadResult(capture.context.messages, true)
        val segment = if (overlap > 0) lastSegment!! else UUID.randomUUID().toString()
        val appended = if (overlap > 0) capture.context.messages.drop(overlap) else capture.context.messages
        val added = appended.map { ContactObservation(contactId = id, segmentId = segment, role = it.role.wireValue, encryptedText = encrypt(it.text), confidence = it.confidence, observedAt = System.currentTimeMillis()) }
        if (!isHistoryEpochCurrent(id, before)) return@withLock HistoryLoadResult(capture.context.messages, false)
        dao.observe(added)
        HistoryLoadResult(if (overlap > 0) existing + appended else capture.context.messages, true)
    }
    suspend fun extras(id: String, context: ChatContext, includeMemory: Boolean = true): GenerationExtras {
        val before = historyEpoch
        val contact = get(id) ?: return GenerationExtras()
        if (!contact.entity.confirmed || contact.entity.sourcePackage != context.sourcePackage || contact.entity.accountSpace != accountSpace) return GenerationExtras()
        val useMemory = includeMemory && contact.entity.useMemory
        // At most twelve cold decrypts. Farther history is usable as soon as the
        // background warmer has populated the bounded process cache.
        val pool = if (useMemory) dao.recent(id).mapIndexedNotNull { index, row ->
            if (before != historyEpoch) return GenerationExtras()
            val text = if (index < 12) decrypt(row.encryptedText, before) else synchronized(plaintextCache) { plaintextCache[row.encryptedText] }
            text?.let { "${row.role}：$it" }
        }.asReversed() else emptyList()
        val selected = LocalMemorySelector.select(context.messages, pool)
        if (before != historyEpoch) return GenerationExtras()
        return GenerationExtras(contact.entity.relationship, relationshipRules(contact.entity.relationship), contact.details,
            contact.preferences.ifBlank { contact.details.takeLast(400) },
            if (useMemory) (contact.memory.take(700) + "\n历史片段（观察时间不等于发送时间，片段之间先后可能不确定）：\n" + selected).take(1200) else "",
            replyMode = app.nextsay.provider.LoverReplyModes.normalize(contact.entity.relationship, contact.entity.replyMode))
    }
    private suspend fun invalidateAround(id: String? = null, operation: suspend () -> Unit) {
        invalidateHistory(id)
        try { operation() }
        finally {
            // Readers started during the DAO operation must also be rejected.
            invalidateHistory(id)
            clearPlaintextCache()
        }
    }
    suspend fun deleteObservation(id: Long) = mutex.withLock {
        val owner = dao.observationContact(id) ?: return@withLock
        invalidateAround(owner) { dao.deleteObservation(id) }
    }
    suspend fun clearHistory(id: String) = mutex.withLock { invalidateAround(id) { dao.clearHistory(id) } }
    suspend fun delete(id: String) = mutex.withLock {
        invalidateAround(id) { db.withTransaction { dao.clearHistory(id); dao.deleteAliases(id); dao.deleteContact(id); dao.deleteLegacy(id); dao.deleteImports(id) } }
    }
    suspend fun clearAll() = mutex.withLock {
        invalidateAround { db.withTransaction {
            // Main screen says clear chat history, not delete profiles/preferences/exclusions.
            dao.clearObservations()
            db.conversationDao().clearMessages()
        } }
    }
}
