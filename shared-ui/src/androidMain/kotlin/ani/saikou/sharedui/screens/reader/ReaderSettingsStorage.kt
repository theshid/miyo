package ani.saikou.sharedui.screens.reader

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit

class ReaderSettingsStorage(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    fun load(): ReaderSettings {
        val modeOrdinal = prefs.getInt(KEY_MODE, ReadingMode.WEBTOON.ordinal)
        return ReaderSettings(
            mode = ReadingMode.entries.getOrNull(modeOrdinal) ?: ReadingMode.WEBTOON,
            background = Color(prefs.getInt(KEY_BACKGROUND, Color.White.toArgb())),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            showPageNumber = prefs.getBoolean(KEY_SHOW_PAGE_NUMBER, true),
            doublePage = prefs.getBoolean(KEY_DOUBLE_PAGE, false),
            cropBorders = prefs.getBoolean(KEY_CROP_BORDERS, false),
            suggestDownloads = prefs.getBoolean(KEY_SUGGEST_DOWNLOADS, true),
        )
    }

    fun save(settings: ReaderSettings) {
        prefs.edit {
            putInt(KEY_MODE, settings.mode.ordinal)
            putInt(KEY_BACKGROUND, settings.background.toArgb())
            putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            putBoolean(KEY_SHOW_PAGE_NUMBER, settings.showPageNumber)
            putBoolean(KEY_DOUBLE_PAGE, settings.doublePage)
            putBoolean(KEY_CROP_BORDERS, settings.cropBorders)
            putBoolean(KEY_SUGGEST_DOWNLOADS, settings.suggestDownloads)
        }
    }

    private companion object {
        const val KEY_MODE = "reading_mode"
        const val KEY_BACKGROUND = "background"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_SHOW_PAGE_NUMBER = "show_page_number"
        const val KEY_DOUBLE_PAGE = "double_page"
        const val KEY_CROP_BORDERS = "crop_borders"
        const val KEY_SUGGEST_DOWNLOADS = "suggest_downloads"
    }
}
