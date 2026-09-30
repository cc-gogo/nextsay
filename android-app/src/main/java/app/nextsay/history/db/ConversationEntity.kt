package app.nextsay.history.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["sourcePackage", "normalizedTitle"], unique = true)],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val normalizedTitle: String,
    val displayTitle: String,
    val updatedAt: Long,
)
