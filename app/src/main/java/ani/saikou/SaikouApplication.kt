package ani.saikou

import android.app.Application
import ani.saikou.di.AppModule

class SaikouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }
}
