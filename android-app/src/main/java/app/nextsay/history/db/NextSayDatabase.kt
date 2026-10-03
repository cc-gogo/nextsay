package app.nextsay.history.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.nextsay.contacts.*

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ExcludedTitleEntity::class,
        ContactEntity::class, ContactAlias::class, ContactObservation::class, ContactLegacyImport::class, ContactExcludedTitle::class, ContactBindingAudit::class],
    version = 3,
    exportSchema = false,
)
abstract class NextSayDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contact_profiles ADD COLUMN replyMode TEXT NOT NULL DEFAULT 'natural'")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS contact_profiles (id TEXT NOT NULL PRIMARY KEY, sourcePackage TEXT NOT NULL, accountSpace TEXT NOT NULL, encryptedName TEXT NOT NULL, relationship TEXT NOT NULL, encryptedDetails TEXT NOT NULL, encryptedPreferences TEXT NOT NULL, encryptedMemory TEXT NOT NULL, autoEnabled INTEGER NOT NULL, saveHistory INTEGER NOT NULL, useMemory INTEGER NOT NULL, confirmed INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS contact_aliases (sourcePackage TEXT NOT NULL, accountSpace TEXT NOT NULL, titleHash TEXT NOT NULL, contactId TEXT NOT NULL, encryptedTitle TEXT NOT NULL, linkedAt INTEGER NOT NULL, PRIMARY KEY(sourcePackage, accountSpace, titleHash, contactId), FOREIGN KEY(contactId) REFERENCES contact_profiles(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_aliases_contactId ON contact_aliases(contactId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS contact_observations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contactId TEXT NOT NULL, segmentId TEXT NOT NULL, role TEXT NOT NULL, encryptedText TEXT NOT NULL, confidence REAL NOT NULL, observedAt INTEGER NOT NULL, FOREIGN KEY(contactId) REFERENCES contact_profiles(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_observations_contactId ON contact_observations(contactId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS contact_legacy_imports (legacyId INTEGER NOT NULL PRIMARY KEY, contactId TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS contact_excluded_titles (sourcePackage TEXT NOT NULL, titleHash TEXT NOT NULL, PRIMARY KEY(sourcePackage, titleHash))")
                db.execSQL("CREATE TABLE IF NOT EXISTS contact_binding_audit (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contactId TEXT NOT NULL, encryptedTitle TEXT NOT NULL, encryptedPreviousName TEXT NOT NULL, previousAccountSpace TEXT NOT NULL, action TEXT NOT NULL, observedAt INTEGER NOT NULL, FOREIGN KEY(contactId) REFERENCES contact_profiles(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contact_binding_audit_contactId ON contact_binding_audit(contactId)")
            }
        }
        @Volatile private var instance: NextSayDatabase? = null

        fun get(context: Context): NextSayDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NextSayDatabase::class.java,
                "nextsay-history.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
