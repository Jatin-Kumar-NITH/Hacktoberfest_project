package app.vercel.campusmate_nith

import android.app.Application
import app.vercel.campusmate_nith.data.Api
import app.vercel.campusmate_nith.data.Store

class CampusMateApplication : Application() {
    lateinit var api: Api
        private set
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        api = Api(this)
        store = Store(this)
    }
}
