package app.nextsay.contacts

import androidx.room.Room
import app.nextsay.capture.CapturedConversation
import app.nextsay.context.*
import app.nextsay.history.MessageCipher
import app.nextsay.history.db.NextSayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real store/Room integration; only device-specific cryptography is substituted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactProfileSyncTest {
    private val cipher = object : MessageCipher {
        override fun encrypt(plaintext: String) = "encrypted:$plaintext"
        override fun decrypt(ciphertext: String) = ciphertext.removePrefix("encrypted:")
    }
    private fun person(id: String, name: String = "a CC_gogo", space: String = "default", confirmed: Boolean = true) =
        ContactSnapshot(ContactEntity(id, "com.tencent.mm", space, "", relationship = "lover",
            useMemory = false, confirmed = confirmed, replyMode = "huangmao"), name, "喜欢简短回复", "", "")
    private fun frame(title: String = "a CC_gogo") = ChatContext("wechat", "com.tencent.mm",
        listOf(ChatMessage(MessageRole.OTHER, "测试消息", 1f)), "", 1f,
        replyRound = ReplyRoundSnapshot(title, listOf(ChatMessage(MessageRole.OTHER, "测试消息", 1f))))

    @Test fun managementSavedNameResolvesWithoutSeparateChatBindingOrHistory() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a"))
            val prepared = store.prepareContext(frame("aCC_gogo"))
            assertEquals("a", prepared.contactId)
            assertEquals("lover", prepared.generationExtras!!.relationship)
            assertEquals("huangmao", prepared.generationExtras!!.replyMode)
            assertEquals("喜欢简短回复", prepared.generationExtras!!.details)
        }
    }
    @Test fun existingEditedLegacyProfileIsUsedByItsUniqueExactNameAndKeepsHistory() = runBlocking {
        withStore { store, db ->
            val legacy = person("a", space = "legacy", confirmed = false)
            store.save(legacy)
            db.contactDao().observe(listOf(ContactObservation(contactId = "a", segmentId = "old", role = "other",
                encryptedText = cipher.encrypt("原来的聊天记忆"), confidence = 1f, observedAt = 1L)))
            val prepared = store.prepareContext(frame())
            assertEquals("a", prepared.contactId)
            assertEquals("huangmao", prepared.generationExtras!!.replyMode)
            assertEquals("喜欢简短回复", prepared.generationExtras!!.details)
            assertEquals("原来的聊天记忆", store.messages("a").single().second.text)
            assertFalse(store.get("a")!!.entity.useMemory)
            assertFalse(store.get("a")!!.entity.autoEnabled)
        }
    }
    @Test fun repeatedPreparationReadsLatestManagementEditsRatherThanOldExtras() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a"))
            val old = store.prepareContext(frame())
            val edited = store.get("a")!!
            store.saveProfile(edited.copy(entity = edited.entity.copy(relationship = "friend"), details = "新资料"))
            val latest = store.prepareContext(old)
            assertEquals("friend", latest.generationExtras!!.relationship)
            assertEquals("natural", latest.generationExtras!!.replyMode)
            assertEquals("新资料", latest.generationExtras!!.details)
        }
    }
    @Test fun duplicateNamesRequireAChoiceRatherThanTakingTheLastEditedPerson() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a")); store.saveProfile(person("b"))
            val prepared = store.prepareContext(frame())
            assertNull(prepared.contactId)
            assertNull(prepared.generationExtras)
            val resolution = store.resolve(CapturedConversation("a CC_gogo", frame(), true))
            assertEquals(setOf("a", "b"), resolution.matched.map { it.entity.id }.toSet())
        }
    }
    @Test fun profileMatchingDoesNotCrossAccountsPlatformsOrSimilarNames() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a"))
            assertNull(store.prepareContext(frame("a CC_qogo")).contactId)
            assertNull(store.prepareContext(frame().copy(sourcePackage = "com.tencent.mobileqq")).contactId)
            store.accountSpace = "another-account"
            assertNull(store.prepareContext(frame()).contactId)
        }
    }
    @Test fun uneditedImportedRecordsAndMissingTitlesCannotSupplyPrivateProfileMaterial() = runBlocking {
        withStore { store, _ ->
            val imported = person("old", space = "legacy", confirmed = false)
            store.save(imported.copy(entity = imported.entity.copy(relationship = "unspecified"), details = ""))
            assertNull(store.prepareContext(frame()).contactId)
            store.saveProfile(person("a"))
            assertNull(store.prepareContext(frame().copy(replyRound = null)).contactId)
        }
    }
    @Test fun explicitUnlinkIsNotUndoneByNameFallbackButSavingProfileRestoresIt() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a"))
            store.unlink("a", "com.tencent.mm", "a CC_gogo")
            assertNull(store.prepareContext(frame()).contactId)
            store.saveProfile(store.get("a")!!)
            assertEquals("a", store.prepareContext(frame()).contactId)
        }
    }
    @Test fun savingLegacyProfileFromManagementPromotesItWithoutEnablingAutomaticRequests() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a", space = "legacy", confirmed = false))
            assertEquals("default", store.get("a")!!.entity.accountSpace)
            assertTrue(store.get("a")!!.entity.confirmed)
            assertFalse(store.get("a")!!.entity.autoEnabled)
            assertEquals("a", store.prepareContext(frame()).contactId)
        }
    }
    @Test fun explicitlyChoosingOneDuplicateIsRememberedAfterTheServiceLosesItsCache() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a")); store.saveProfile(person("b"))
            store.saveAndLink(store.get("b")!!, "com.tencent.mm", "a CC_gogo")
            assertEquals("b", store.prepareContext(frame()).contactId)
            store.saveProfile(store.get("b")!!.copy(details = "更新的资料"))
            assertEquals("b", store.prepareContext(frame()).contactId)
            assertEquals("更新的资料", store.prepareContext(frame()).generationExtras!!.details)
        }
    }
    @Test fun unlinkingAnOcrSpacingVariantSuppressesEveryEquivalentAlias() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a"))
            assertEquals("a", store.prepareContext(frame("aCC_gogo")).contactId)
            store.unlink("a", "com.tencent.mm", "aCC_gogo")
            assertNull(store.prepareContext(frame("aCC_gogo")).contactId)
            assertNull(store.prepareContext(frame("a CC_gogo")).contactId)
        }
    }
    @Test fun unlinkingRemovesMultipleExistingSpacingAliases() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a"))
            store.saveAndLink(store.get("a")!!, "com.tencent.mm", "aCC_gogo")
            store.unlink("a", "com.tencent.mm", "aCC_gogo")
            assertNull(store.prepareContext(frame("aCC_gogo")).contactId)
            assertNull(store.prepareContext(frame("a CC_gogo")).contactId)
        }
    }
    @Test fun unlinkingSelectedNamesakeCannotSilentlySwitchToAnotherPersonsMaterial() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a")); store.saveProfile(person("b"))
            store.saveAndLink(store.get("b")!!, "com.tencent.mm", "a CC_gogo")
            assertEquals("b", store.prepareContext(frame()).contactId)
            store.unlink("b", "com.tencent.mm", "a CC_gogo")
            assertNull(store.prepareContext(frame()).contactId)
            assertNull(store.prepareContext(frame()).generationExtras)
            store.saveAndLink(store.get("a")!!, "com.tencent.mm", "a CC_gogo")
            assertEquals("a", store.prepareContext(frame()).contactId)
        }
    }
    @Test fun bindingHistoryDoesNotDescribeSavingOrSelectingAsUnlinking() = runBlocking {
        withStore { store, _ ->
            store.saveProfile(person("a"))
            store.saveAndLink(store.get("a")!!, "com.tencent.mm", "a CC_gogo")
            assertFalse(store.bindingHistory("a").any { it.contains("解除") })
            store.unlink("a", "com.tencent.mm", "a CC_gogo")
            assertTrue(store.bindingHistory("a").first().contains("解除"))
        }
    }
    @Test fun menuReadFailureReturnsAnErrorResultInsteadOfThrowingOrReusingAProfile() = runBlocking {
        withStore { _, db ->
            db.contactDao().save(ContactEntity("bad", "com.tencent.mm", "default", "broken-ciphertext"))
            val brokenCipher = object : MessageCipher {
                override fun encrypt(plaintext: String) = plaintext
                override fun decrypt(ciphertext: String): String = throw IllegalArgumentException("synthetic unreadable data")
            }
            val store = ContactStore(RuntimeEnvironment.getApplication(), db, brokenCipher, { "test-index:$it" })
            assertNull(store.menuResolution(CapturedConversation("a CC_gogo", frame(), true)))
        }
    }
    @Test fun cancelledMenuReadStillPropagatesCancellation() = runBlocking {
        withStore { _, db ->
            val store = ContactStore(RuntimeEnvironment.getApplication(), db, cipher, { throw kotlinx.coroutines.CancellationException("cancelled") })
            try {
                store.menuResolution(CapturedConversation("a CC_gogo", frame(), true))
                fail("Cancellation must not be reported as a data failure")
            } catch (_: kotlinx.coroutines.CancellationException) { }
        }
    }
    private suspend fun withStore(block: suspend (ContactStore, NextSayDatabase) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, NextSayDatabase::class.java).build()
        val store = ContactStore(context, db, cipher = cipher, titleHasher = { "test-index:$it" })
        try { block(store, db) } finally { db.close() }
    }
}
