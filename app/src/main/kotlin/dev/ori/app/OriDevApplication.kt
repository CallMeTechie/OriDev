package dev.ori.app

import android.app.Application
import android.content.Context
import android.os.Trace
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.ori.app.crash.AcraConfig
import dev.ori.app.wear.WearDataSyncPublisher
import dev.ori.core.security.preferences.CrashReportingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.acra.ACRA
import javax.inject.Inject

@HiltAndroidApp
class OriDevApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var wearDataSyncPublisher: WearDataSyncPublisher

    @Inject lateinit var crashReportingPreferences: CrashReportingPreferences

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        AcraConfig.initIfEnabled(this)
    }

    override fun onCreate() {
        Trace.beginSection("OriDevApplication.onCreate")
        try {
            super.onCreate()
            wearDataSyncPublisher.start()
            applicationScope.launch(Dispatchers.IO) {
                val enabled = crashReportingPreferences.enabled.first()
                if (ACRA.isInitialised) {
                    ACRA.errorReporter.setEnabled(enabled)
                }
            }
        } finally {
            Trace.endSection()
        }
    }
}
