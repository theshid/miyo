package ani.saikou.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadEntity::class,
        DownloadedMangaEntity::class,
        ReadingHistoryEntity::class,
        WatchHistoryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SaikouDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SaikouDatabase? = null

        fun getInstance(context: Context): SaikouDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SaikouDatabase::class.java,
                    "saikou_v2.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
