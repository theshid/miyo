package ani.saikou.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadEntity::class,
        DownloadedMangaEntity::class,
        ReadingHistoryEntity::class,
        WatchHistoryEntity::class,
        ActivityEventEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class SaikouDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun activityEventDao(): ActivityEventDao

    companion object {
        @Volatile
        private var INSTANCE: SaikouDatabase? = null

        // ── Migrations ───────────────────────────────────────
        // Add new migrations here for each version bump so user data is preserved.

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add completedEpisodes column to watch_history with default 0
                db.execSQL("ALTER TABLE watch_history ADD COLUMN completedEpisodes INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_events (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        timestampMs INTEGER NOT NULL,
                        type TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_events_timestampMs ON activity_events(timestampMs)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add chapterNumber so the reader can find downloaded chapters
                // by number alone (no parser round-trip required).
                // -1 = unknown for any rows that pre-date this column.
                db.execSQL("ALTER TABLE downloads ADD COLUMN chapterNumber INTEGER NOT NULL DEFAULT -1")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Size in bytes, measured at completion time. Powers the
                // "Download next N — uses about X MB" estimate in the reader.
                db.execSQL("ALTER TABLE downloads ADD COLUMN fileSizeBytes INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Capture media context per activity event so the heatmap can
                // show "what you actually did on day X" — old rows keep NULLs.
                db.execSQL("ALTER TABLE activity_events ADD COLUMN mediaId INTEGER")
                db.execSQL("ALTER TABLE activity_events ADD COLUMN mediaTitle TEXT")
                db.execSQL("ALTER TABLE activity_events ADD COLUMN coverUrl TEXT")
                db.execSQL("ALTER TABLE activity_events ADD COLUMN episodeNumber INTEGER")
                db.execSQL("ALTER TABLE activity_events ADD COLUMN chapterNumber INTEGER")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Counter for service-level auto-retries on ERROR rows so we can
                // cap how many times a genuinely-unfetchable chapter gets cycled.
                db.execSQL("ALTER TABLE downloads ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): SaikouDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SaikouDatabase::class.java,
                    "saikou_v2.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    // Fallback only as a last resort — prefers migrations above
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
