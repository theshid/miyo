package ani.saikou.di

import android.content.Context
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.repository.AnilistRepositoryImpl
import ani.saikou.domain.repository.AnilistRepository

/**
 * Simple service locator. Keeps things lightweight without adding Hilt/Koin
 * as a dependency for now — can be swapped later if needed.
 */
object AppModule {

    private var tokenStorage: TokenStorage? = null
    private var api: AnilistApi? = null
    private var repository: AnilistRepository? = null

    fun init(context: Context) {
        tokenStorage = TokenStorage(context.applicationContext)
        api = AnilistApi(tokenProvider = { tokenStorage!!.getToken() })
        repository = AnilistRepositoryImpl(api!!, tokenStorage!!)
    }

    fun repository(): AnilistRepository =
        repository ?: throw IllegalStateException("AppModule not initialized. Call init() in Application.onCreate()")

    fun tokenStorage(): TokenStorage =
        tokenStorage ?: throw IllegalStateException("AppModule not initialized.")
}
