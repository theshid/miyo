package ani.saikou.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadEntity::class, DownloadedMangaEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SaikouDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: SaikouDatabase? = null

        fun getInstance(context: Context): SaikouDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SaikouDatabase::class.java,
                    "saikou_v2.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
