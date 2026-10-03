package app.nextsay.contacts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.nextsay.history.db.NextSayDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local synthetic database only. Build now; run on an authorized emulator/device later. */
@RunWith(AndroidJUnit4::class)
class ContactMigrationTest {
    @Test fun migrationRetainsLegacyCiphertextAndAcceptsRoomValidation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "nextsay-synthetic-migration-${java.util.UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE conversations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourcePackage TEXT NOT NULL, normalizedTitle TEXT NOT NULL, displayTitle TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX index_conversations_sourcePackage_normalizedTitle ON conversations(sourcePackage, normalizedTitle)")
                db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, conversationId INTEGER NOT NULL, role TEXT NOT NULL, encryptedText TEXT NOT NULL, confidence REAL NOT NULL, observedAt INTEGER NOT NULL, FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_messages_conversationId ON messages(conversationId)")
                db.execSQL("CREATE TABLE excluded_titles (sourcePackage TEXT NOT NULL, normalizedTitle TEXT NOT NULL, PRIMARY KEY(sourcePackage, normalizedTitle))")
                db.execSQL("INSERT INTO conversations VALUES(1,'com.tencent.mm','synthetic','Synthetic',1)")
                db.execSQL("INSERT INTO messages VALUES(1,1,'other','original-ciphertext',0.9,1)")
                db.execSQL("INSERT INTO conversations VALUES(2,'com.tencent.mm','migrated:real','migrated:real',1)")
                db.execSQL("INSERT INTO messages VALUES(2,2,'me','second-original-ciphertext',0.9,1)")
                db.execSQL("INSERT INTO excluded_titles VALUES('com.tencent.mm','hmac:real')")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, NextSayDatabase::class.java, name).addMigrations(NextSayDatabase.MIGRATION_1_2, NextSayDatabase.MIGRATION_2_3).build()
        try {
            assertEquals("original-ciphertext", database.conversationDao().loadMessages(1).single().encryptedText)
            assertTrue(database.contactDao().list("com.tencent.mm", "default").isEmpty())
            val store = ContactStore(context, database)
            store.initialize()
            store.initialize()
            val legacy = database.contactDao().list("com.tencent.mm", "legacy")
            assertEquals(2, legacy.size)
            val importedText = legacy.flatMap { database.contactDao().recent(it.id) }.map { it.encryptedText }.toSet()
            assertEquals(setOf("original-ciphertext", "second-original-ciphertext"), importedText)
            assertTrue(database.conversationDao().loadMessages(1).isEmpty())
            assertTrue(database.conversationDao().loadMessages(2).isEmpty())
            assertTrue(database.contactDao().legacy().isEmpty())
            assertTrue(database.contactDao().exclusions().isEmpty())
            assertFalse(store.create("com.tencent.mm", "hmac:real").entity.saveHistory)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
