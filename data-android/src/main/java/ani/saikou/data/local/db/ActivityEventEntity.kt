package ani.saikou.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_events",
    indices = [Index(value = ["timestampMs"])],
)
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMs: Long,
    val type: String,
    /** Optional media context — null for events written before the v8 schema. */
    val mediaId: Int? = null,
    val mediaTitle: String? = null,
    val coverUrl: String? = null,
    val episodeNumber: Int? = null,
    val chapterNumber: Int? = null,
)
