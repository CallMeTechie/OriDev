package dev.ori.core.security.crash

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Counterpart to [LocalCrashLogger] for *caught* exceptions that don't
 * terminate the app — failed SSH connects, FTP auth errors, proxmox API
 * timeouts, etc. Writes a plain-text report to `Downloads/oridev-error-
 * {category}-{timestamp}.txt` so the user can share it via the Files
 * app without needing `adb logcat`.
 *
 * Install once in `OriDevApplication.attachBaseContext` alongside
 * [LocalCrashLogger.install]; after that any layer can call
 * [NonFatalErrorLogger.log] without a Context parameter.
 */
public object NonFatalErrorLogger {

    private const val TAG = "NonFatalErrorLogger"
    private const val FILE_PREFIX = "oridev-error-"
    private const val FILE_EXTENSION = ".txt"
    private const val MIME_TEXT_PLAIN = "text/plain"
    private const val LOGCAT_TAIL_LINES = "200"

    private val appContextRef = AtomicReference<Context?>(null)

    /**
     * Captures the application Context for later use. Safe to call
     * multiple times; the last value wins. Use `applicationContext`
     * — not an Activity — to avoid leaks.
     */
    public fun install(context: Context) {
        appContextRef.set(context.applicationContext ?: context)
    }

    /**
     * Writes a non-fatal error report to Downloads. Best-effort: any
     * failure in the logger itself is caught and sent to logcat instead
     * of propagating to the caller.
     *
     * @param category short slug embedded in the file name (e.g.
     *   `connect-ssh`, `ftp-auth`) — alphanumeric + hyphens only.
     * @param throwable the caught exception whose stack trace should
     *   be recorded.
     * @param contextNote optional one-line human-readable hint about
     *   the call site ("host=192.168.1.10, port=22"). PII-sensitive
     *   strings should be scrubbed by the caller.
     */
    public fun log(category: String, throwable: Throwable, contextNote: String? = null) {
        val context = appContextRef.get() ?: run {
            Log.w(TAG, "NonFatalErrorLogger.log called before install(); dropping report for $category")
            return
        }
        @Suppress("TooGenericExceptionCaught")
        try {
            writeErrorLog(context, category, throwable, contextNote)
        } catch (loggerFailure: Throwable) {
            // Never let the logger itself throw into the caller.
            Log.e(TAG, "NonFatalErrorLogger failed to write report for $category", loggerFailure)
        }
    }

    private fun writeErrorLog(
        context: Context,
        category: String,
        throwable: Throwable,
        contextNote: String?,
    ) {
        val safeCategory = category.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "error" }
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val fileName = "$FILE_PREFIX$safeCategory-$timestamp$FILE_EXTENSION"
        val report = buildReport(category, throwable, contextNote)

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TEXT_PLAIN)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore returned null URI for $fileName")
        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(report.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Could not open output stream for $uri")
    }

    internal fun buildReport(category: String, throwable: Throwable, contextNote: String?): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("=== Ori:Dev Non-Fatal Error Report ===")
        pw.println("Category:     $category")
        pw.println("Timestamp:    ${Date()}")
        if (!contextNote.isNullOrBlank()) {
            pw.println("Context:      $contextNote")
        }
        pw.println("Thread:       ${Thread.currentThread().name}")
        pw.println("Manufacturer: ${Build.MANUFACTURER}")
        pw.println("Model:        ${Build.MODEL}")
        pw.println("Android:      ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println()
        pw.println("=== Stack trace ===")
        throwable.printStackTrace(pw)
        pw.println()
        pw.println("=== Recent logcat (last $LOGCAT_TAIL_LINES lines) ===")
        appendLogcatTail(pw)
        return sw.toString()
    }

    private fun appendLogcatTail(pw: PrintWriter) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val process = ProcessBuilder("logcat", "-d", "-t", LOGCAT_TAIL_LINES)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.copyTo(pw)
            }
        } catch (logcatError: Throwable) {
            pw.println("(could not capture logcat: ${logcatError.javaClass.simpleName}: ${logcatError.message})")
        }
    }
}
