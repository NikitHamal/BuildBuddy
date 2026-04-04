package com.build.buddyai

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class BuildBuddyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            CrashHandler.init(this)
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            val builder = Configuration.Builder()
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)

            if (this::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            }
            return builder.build()
        }
}
