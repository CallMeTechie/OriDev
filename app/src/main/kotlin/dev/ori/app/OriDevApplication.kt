package dev.ori.app

import android.app.Application
import android.content.Context
import android.os.Trace
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.ori.app.crash.AcraConfig
import dev.ori.app.wear.WearDataSyncPublisher
import dev.ori.core.security.crash.LocalCrashLogger
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
        // Install AFTER ACRA so our handler captures ACRA's handler as its
        // chain target — ours fires first (writes Downloads/oridev-crash-*.txt),
        // then ACRA fires, then the OS kills the process.
        LocalCrashLogger.install(this)
    }

    override fun onCreate() {
        Trace.beginSection("OriDevApplication.onCreate")
        try {
            super.onCreate()
            // Initialize Google Mobile Ads SDK (UMP consent handled automatically by GMA v23+)
            com.google.android.gms.ads.MobileAds.initialize(this)
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
