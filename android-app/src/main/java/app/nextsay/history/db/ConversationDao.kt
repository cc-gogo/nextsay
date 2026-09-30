package app.nextsay.history.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE sourcePackage = :sourcePackage AND normalizedTitle = :normalizedTitle LIMIT 1")
    suspend fun findConversation(sourcePackage: String, normalizedTitle: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun loadMessages(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insertMessages(items: List<MessageEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM excluded_titles WHERE sourcePackage = :sourcePackage AND normalizedTitle = :normalizedTitle)")
    suspend fun isTitleExcluded(sourcePackage: String, normalizedTitle: String): Boolean

    @Query("INSERT OR IGNORE INTO excluded_titles(sourcePackage, normalizedTitle) VALUES(:sourcePackage, :normalizedTitle)")
    suspend fun excludeTitle(sourcePackage: String, normalizedTitle: String)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query("DELETE FROM excluded_titles")
    suspend fun clearExcludedTitles()
}
