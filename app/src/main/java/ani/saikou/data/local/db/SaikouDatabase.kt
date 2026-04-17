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
    ],
    version = 4,
    exportSchema = false,
)
abstract class SaikouDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun watchHistoryDao(): WatchHistoryDao

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

        fun getInstance(context: Context): SaikouDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SaikouDatabase::class.java,
                    "saikou_v2.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    // Fallback only as a last resort — prefers migrations above
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
