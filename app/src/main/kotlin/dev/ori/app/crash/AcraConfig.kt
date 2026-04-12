package dev.ori.app.crash

import android.app.Application
import dev.ori.app.BuildConfig
import org.acra.ReportField
import org.acra.config.httpSender
import org.acra.config.limiter
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import java.util.concurrent.TimeUnit

/**
 * Initializes ACRA crash reporting.
 *
 * Must be called from `Application.attachBaseContext`. ACRA itself is started
 * unconditionally when guards pass; the user-facing opt-in toggle is consulted
 * asynchronously in `Application.onCreate()` via `ACRA.errorReporter.setEnabled()`,
 * which defaults reports to suppressed until the user opts in.
 */
internal object AcraConfig {
    private const val PLACEHOLDER_URL = "https://acra.invalid"

    fun initIfEnabled(application: Application) {
        if (BuildConfig.DEBUG) return
        if (BuildConfig.ACRA_BACKEND_URL == PLACEHOLDER_URL) return

        application.initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            reportContent = listOf(
                ReportField.APP_VERSION_CODE,
                ReportField.APP_VERSION_NAME,
                ReportField.PHONE_MODEL,
                ReportField.ANDROID_VERSION,
                ReportField.STACK_TRACE,
                ReportField.STACK_TRACE_HASH,
                ReportField.USER_CRASH_DATE,
            )
            // PII scrubbing is performed by [ScrubbingReportingAdministrator],
            // discovered via META-INF/services. No reflection here keeps R8 happy.
            httpSender {
                uri = BuildConfig.ACRA_BACKEND_URL
                basicAuthLogin = BuildConfig.ACRA_BASIC_AUTH_LOGIN
                basicAuthPassword = BuildConfig.ACRA_BASIC_AUTH_PASSWORD
                httpMethod = HttpSender.Method.POST
            }
            limiter {
                enabled = true
                exceptionClassLimit = LIMITER_EXCEPTION_CLASS_LIMIT
                stacktraceLimit = LIMITER_STACKTRACE_LIMIT
                period = LIMITER_PERIOD_DAYS
                periodUnit = TimeUnit.DAYS
            }
        }
    }

    private const val LIMITER_EXCEPTION_CLASS_LIMIT = 3
    private const val LIMITER_STACKTRACE_LIMIT = 1
    private const val LIMITER_PERIOD_DAYS = 1L
}
