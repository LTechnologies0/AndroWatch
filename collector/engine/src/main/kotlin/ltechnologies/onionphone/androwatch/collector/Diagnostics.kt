package ltechnologies.onionphone.androwatch.collector

import android.os.Trace
import android.util.Log

private const val LOG_TAG = "AndroWatch"

/** Perfetto / Systrace slice around suspend work. Near no-op when tracing off. */
suspend inline fun <T> awTrace(section: String, crossinline block: suspend () -> T): T {
    Trace.beginSection("AW/$section")
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

fun awLogW(section: String, message: () -> String) {
    if (Log.isLoggable(LOG_TAG, Log.WARN)) Log.w(LOG_TAG, "[$section] ${message()}")
}
