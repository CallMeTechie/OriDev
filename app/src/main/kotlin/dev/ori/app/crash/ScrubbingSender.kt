package dev.ori.app.crash

import android.content.Context
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.config.CoreConfiguration
import org.acra.config.ReportingAdministrator
import org.acra.data.CrashReportData

private val ABSOLUTE_PATH = Regex("""(/(?:sdcard|storage|data|system|vendor|root|home)/[^\s:()'"]*)""")
private val FQDN = Regex(
    """\b([a-z0-9-]+\.)+(com|net|org|io|dev|app|internal|local|lan)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Replaces absolute filesystem paths and FQDN-like tokens in `text` with
 * `<path>` and `<host>` placeholders. Used to scrub stack traces before they
 * leave the device.
 */
internal fun scrub(text: String): String =
    text.replace(ABSOLUTE_PATH, "<path>")
        .replace(FQDN, "<host>")

/**
 * ACRA [ReportingAdministrator] that strips PII from the STACK_TRACE field
 * before any [org.acra.sender.ReportSender] runs. Discovered by ACRA via
 * `META-INF/services/org.acra.config.ReportingAdministrator`.
 *
 * Must be a public class with a no-arg constructor.
 */
class ScrubbingReportingAdministrator : ReportingAdministrator {

    override fun shouldStartCollecting(
        context: Context,
        config: CoreConfiguration,
        reportBuilder: ReportBuilder,
    ): Boolean = true

    override fun shouldSendReport(
        context: Context,
        config: CoreConfiguration,
        crashReportData: CrashReportData,
    ): Boolean {
        val original = crashReportData.getString(ReportField.STACK_TRACE).orEmpty()
        crashReportData.put(ReportField.STACK_TRACE, scrub(original))
        return true
    }
}
