package ani.saikou.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class TokenStorage(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("saikou_auth", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "anilist_token"
        private const val KEY_USER_ID = "user_id"
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String) {
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    fun saveUserId(id: Int) {
        prefs.edit { putInt(KEY_USER_ID, id) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    fun isLoggedIn(): Boolean = getToken() != null
}
