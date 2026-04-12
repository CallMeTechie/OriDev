package dev.ori.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.ori.app.wear.WearDataSyncPublisher
import javax.inject.Inject

@HiltAndroidApp
class OriDevApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var wearDataSyncPublisher: WearDataSyncPublisher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        wearDataSyncPublisher.start()
    }
}
