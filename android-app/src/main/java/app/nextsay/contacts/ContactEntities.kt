package app.nextsay.contacts

import androidx.room.*

@Entity(tableName = "contact_profiles")
data class ContactEntity(
    @PrimaryKey val id: String,
    val sourcePackage: String,
    val accountSpace: String,
    val encryptedName: String,
    val relationship: String = "unspecified",
    val encryptedDetails: String = "",
    val encryptedPreferences: String = "",
    val encryptedMemory: String = "",
    val autoEnabled: Boolean = false,
    val saveHistory: Boolean = true,
    val useMemory: Boolean = true,
    val confirmed: Boolean = true,
    @ColumnInfo(defaultValue = "'natural'") val replyMode: String = "natural",
)

@Entity(tableName = "contact_aliases", primaryKeys = ["sourcePackage", "accountSpace", "titleHash", "contactId"],
    foreignKeys = [ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contactId"], onDelete = ForeignKey.CASCADE)], indices = [Index("contactId")])
data class ContactAlias(
    val sourcePackage: String, val accountSpace: String, val titleHash: String,
    val contactId: String, val encryptedTitle: String, val linkedAt: Long,
)

@Entity(tableName = "contact_observations", indices = [Index("contactId")],
    foreignKeys = [ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contactId"], onDelete = ForeignKey.CASCADE)])
data class ContactObservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: String, val segmentId: String, val role: String,
    val encryptedText: String, val confidence: Float, val observedAt: Long,
)

@Entity(tableName = "contact_legacy_imports")
data class ContactLegacyImport(@PrimaryKey val legacyId: Long, val contactId: String)
@Entity(tableName = "contact_excluded_titles", primaryKeys = ["sourcePackage", "titleHash"])
data class ContactExcludedTitle(val sourcePackage: String, val titleHash: String)

@Entity(tableName = "contact_binding_audit", indices = [Index("contactId")],
    foreignKeys = [ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contactId"], onDelete = ForeignKey.CASCADE)])
data class ContactBindingAudit(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: String, val encryptedTitle: String, val encryptedPreviousName: String,
    val previousAccountSpace: String, val action: String, val observedAt: Long)

@Dao
interface ContactDao {
    @Insert suspend fun audit(item: ContactBindingAudit)
    @Query("SELECT * FROM contact_binding_audit WHERE contactId = :id ORDER BY id DESC LIMIT 1000")
    suspend fun audits(id: String): List<ContactBindingAudit>
    @Query("SELECT * FROM contact_profiles WHERE sourcePackage = :pkg AND accountSpace = :space")
    suspend fun list(pkg: String, space: String): List<ContactEntity>
    @Query("SELECT * FROM contact_profiles WHERE id = :id")
    suspend fun get(id: String): ContactEntity?
    @Query("SELECT * FROM contact_profiles") suspend fun allProfiles(): List<ContactEntity>
    @Query("SELECT * FROM contact_aliases WHERE contactId = :id") suspend fun contactAliases(id: String): List<ContactAlias>
    @Query("UPDATE contact_observations SET contactId = :target WHERE contactId = :source") suspend fun moveObservations(source: String, target: String)
    @Query("UPDATE contact_binding_audit SET contactId = :target WHERE contactId = :source") suspend fun moveAudits(source: String, target: String)
    @Query("UPDATE contact_legacy_imports SET contactId = :target WHERE contactId = :source") suspend fun moveImports(source: String, target: String)
    @Upsert suspend fun save(contact: ContactEntity)
    @Query("SELECT * FROM contact_aliases WHERE sourcePackage = :pkg AND accountSpace = :space AND titleHash = :hash")
    suspend fun aliases(pkg: String, space: String, hash: String): List<ContactAlias>
    @Upsert suspend fun alias(alias: ContactAlias)
    @Query("DELETE FROM contact_aliases WHERE sourcePackage = :pkg AND accountSpace = :space AND titleHash = :hash AND contactId = :id")
    suspend fun unlink(pkg: String, space: String, hash: String, id: String)
    @Query("SELECT * FROM contact_observations WHERE contactId = :id ORDER BY id DESC LIMIT :limit")
    suspend fun recent(id: String, limit: Int = 1000): List<ContactObservation>
    @Insert suspend fun observe(items: List<ContactObservation>)
    @Query("DELETE FROM contact_observations WHERE contactId = :id") suspend fun clearHistory(id: String)
    @Query("DELETE FROM contact_observations WHERE id = :id") suspend fun deleteObservation(id: Long)
    @Query("SELECT contactId FROM contact_observations WHERE id = :id") suspend fun observationContact(id: Long): String?
    @Query("DELETE FROM contact_aliases WHERE contactId = :id") suspend fun deleteAliases(id: String)
    @Query("DELETE FROM contact_profiles WHERE id = :id") suspend fun deleteContact(id: String)
    @Query("DELETE FROM contact_profiles") suspend fun clearContacts()
    @Query("DELETE FROM contact_aliases") suspend fun clearAliases()
    @Query("DELETE FROM contact_observations") suspend fun clearObservations()
    @Query("SELECT conversations.* FROM conversations WHERE NOT EXISTS (SELECT 1 FROM contact_legacy_imports WHERE legacyId = conversations.id)")
    suspend fun legacy(): List<app.nextsay.history.db.ConversationEntity>
    @Insert suspend fun imported(item: ContactLegacyImport)
    @Query("DELETE FROM messages WHERE conversationId = :id") suspend fun clearLegacyMessages(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun exclude(item: ContactExcludedTitle)
    @Query("SELECT EXISTS(SELECT 1 FROM contact_excluded_titles WHERE sourcePackage = :pkg AND titleHash = :hash)")
    suspend fun isExcluded(pkg: String, hash: String): Boolean
    @Query("UPDATE conversations SET normalizedTitle = :marker, displayTitle = :encryptedName WHERE id = :id")
    suspend fun scrubLegacy(id: Long, marker: String, encryptedName: String)
    @Query("DELETE FROM conversations WHERE id IN (SELECT legacyId FROM contact_legacy_imports WHERE contactId = :id)") suspend fun deleteLegacy(id: String)
    @Query("DELETE FROM contact_legacy_imports WHERE contactId = :id") suspend fun deleteImports(id: String)
    @Query("SELECT * FROM excluded_titles") suspend fun exclusions(): List<app.nextsay.history.db.ExcludedTitleEntity>
    @Query("UPDATE excluded_titles SET normalizedTitle = :hashed WHERE sourcePackage = :pkg AND normalizedTitle = :old")
    suspend fun hashExclusion(pkg: String, old: String, hashed: String)
}
