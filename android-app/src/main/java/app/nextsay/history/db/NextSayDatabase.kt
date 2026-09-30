package app.nextsay.history.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, ExcludedTitleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NextSayDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile private var instance: NextSayDatabase? = null

        fun get(context: Context): NextSayDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NextSayDatabase::class.java,
                "nextsay-history.db",
            ).build().also { instance = it }
        }
    }
}
