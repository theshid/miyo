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
)
