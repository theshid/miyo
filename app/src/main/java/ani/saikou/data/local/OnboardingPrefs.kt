package ani.saikou.data.local

import android.content.Context
import androidx.core.content.edit

class OnboardingPrefs(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    fun hasSeenHomeTour(): Boolean = prefs.getBoolean(KEY_HOME_TOUR, false)

    fun markHomeTourSeen() {
        prefs.edit { putBoolean(KEY_HOME_TOUR, true) }
    }

    fun resetHomeTour() {
        prefs.edit { remove(KEY_HOME_TOUR) }
    }

    fun hasSeenReaderTour(): Boolean = prefs.getBoolean(KEY_READER_TOUR, false)

    fun markReaderTourSeen() {
        prefs.edit { putBoolean(KEY_READER_TOUR, true) }
    }

    fun resetReaderTour() {
        prefs.edit { remove(KEY_READER_TOUR) }
    }

    private companion object {
        const val KEY_HOME_TOUR = "home_tour_seen"
        const val KEY_READER_TOUR = "reader_tour_seen"
    }
}
