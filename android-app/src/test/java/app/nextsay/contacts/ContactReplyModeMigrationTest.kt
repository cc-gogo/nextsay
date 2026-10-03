package app.nextsay.contacts

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.nextsay.history.db.NextSayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Upgrade a real synthetic v2 file, validate through Room, then reopen it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactReplyModeMigrationTest {
    @Test fun upgradeRetainsEncryptedProfilesAndHistoryWhileDefaultingEachModeToNatural() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "reply-mode-migration-${java.util.UUID.randomUUID()}.db"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE conversations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourcePackage TEXT NOT NULL, normalizedTitle TEXT NOT NULL, displayTitle TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_conversations_sourcePackage_normalizedTitle ON conversations(sourcePackage, normalizedTitle)")
                db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, conversationId INTEGER NOT NULL, role TEXT NOT NULL, encryptedText TEXT NOT NULL, confidence REAL NOT NULL, observedAt INTEGER NOT NULL, FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_messages_conversationId ON messages(conversationId)")
                db.execSQL("CREATE TABLE excluded_titles (sourcePackage TEXT NOT NULL, normalizedTitle TEXT NOT NULL, PRIMARY KEY(sourcePackage, normalizedTitle))")
                NextSayDatabase.MIGRATION_1_2.migrate(db)
                db.execSQL("INSERT INTO contact_profiles VALUES('lover-a','com.tencent.mm','default','name-cipher','lover','details-cipher','preferences-cipher','memory-cipher',0,1,1,1)")
                db.execSQL("INSERT INTO contact_profiles VALUES('friend-b','com.tencent.mm','default','friend-cipher','friend','','','',0,1,1,1)")
                db.execSQL("INSERT INTO contact_observations VALUES(1,'lover-a','segment','other','chat-cipher',0.9,1)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.setWriteAheadLoggingEnabled(false)
        helper.writableDatabase
        helper.close()
        try {
            val custom = Room.databaseBuilder(context, NextSayDatabase::class.java, name)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(NextSayDatabase.MIGRATION_1_2, NextSayDatabase.MIGRATION_2_3).build()
            try {
                val sqlite = custom.openHelper.writableDatabase
                assertEquals("New schema version must include persistent reply modes", 3, sqlite.version)
                sqlite.query("SELECT encryptedName, encryptedDetails, encryptedPreferences, encryptedMemory, replyMode FROM contact_profiles WHERE id='lover-a'").use { row ->
                    assertTrue(row.moveToFirst())
                    assertEquals(listOf("name-cipher", "details-cipher", "preferences-cipher", "memory-cipher", "natural"), (0..4).map { row.getString(it) })
                }
                assertEquals("chat-cipher", custom.contactDao().recent("lover-a").single().encryptedText)
                assertEquals("natural", custom.contactDao().get("friend-b")!!.replyMode)
                val lover = custom.contactDao().get("lover-a")!!
                custom.contactDao().save(lover.copy(replyMode = "huangmao"))
            } finally { custom.close() }
            val reopened = Room.databaseBuilder(context, NextSayDatabase::class.java, name)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(NextSayDatabase.MIGRATION_1_2, NextSayDatabase.MIGRATION_2_3).build()
            try {
                assertEquals("huangmao", reopened.contactDao().get("lover-a")!!.replyMode)
                assertEquals("natural", reopened.contactDao().get("friend-b")!!.replyMode)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
