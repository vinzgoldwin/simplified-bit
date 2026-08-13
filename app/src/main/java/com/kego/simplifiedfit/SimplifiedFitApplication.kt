package com.kego.simplifiedfit

import android.app.Application
import com.kego.simplifiedfit.data.HealthDatabase
import com.kego.simplifiedfit.data.HealthRepository
import com.kego.simplifiedfit.data.SecureStore
import com.kego.simplifiedfit.sync.HealthSyncWorker

class SimplifiedFitApplication : Application() {
    val secureStore by lazy { SecureStore(this) }
    val healthRepository by lazy { HealthRepository(HealthDatabase(this), secureStore) }

    override fun onCreate() {
        super.onCreate()
        HealthSyncWorker.schedule(this)
    }
}
