package app.nextsay.history.db

import androidx.room.Entity

@Entity(
    tableName = "excluded_titles",
    primaryKeys = ["sourcePackage", "normalizedTitle"],
)
data class ExcludedTitleEntity(
    val sourcePackage: String,
    val normalizedTitle: String,
)
