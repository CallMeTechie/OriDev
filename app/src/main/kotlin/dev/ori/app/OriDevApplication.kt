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
        // Install BEFORE super.attachBaseContext so we capture crashes
        // that happen during Hilt's @HiltAndroidApp graph validation
        // (which runs INSIDE super.attachBaseContext on first launch).
        // The previous "install after ACRA" order missed exactly the
        // class of startup crashes we built the logger to capture —
        // because the crash fires before our handler is set.
        //
        // Chain at install time:
        //   1. our handler captures the system default → our handler runs first
        //   2. ACRA installs after us, captures our handler → ACRA runs first,
        //      then chains down to ours, then to the system default.
        // Either way the file gets written: if the crash hits before ACRA's
        // install, our handler runs directly; if it hits after, ACRA delegates
        // to us. The OS kill is always the last link in the chain.
        LocalCrashLogger.install(base)
        super.attachBaseContext(base)
        AcraConfig.initIfEnabled(this)
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
