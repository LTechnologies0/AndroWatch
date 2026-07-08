package ltechnologies.onionphone.androwatch

import android.app.Application
import android.os.StrictMode
import android.util.Log

/**
 * Application entry point wiring process-wide setup for AndroWatch.
 *
 * Installs a global uncaught-exception handler and, in debug builds only, enables
 * [StrictMode] thread/VM policies to surface accidental disk/network work on the main thread.
 *
 * @see PrivacyLog
 */
class AndroWatchApp : Application() {
    /**
     * Initializes crash handling and (debug-only) StrictMode when the process starts.
     */
    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, "trace on: adb logcat -s AndroWatch:* ; perfetto via profileable shell")
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build(),
        )
    }

    /**
     * Installs a default uncaught-exception handler that records a privacy-safe crash flag,
     * logs the stack trace in debug builds, then delegates to the previous handler.
     */
    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            PrivacyLog.flag("crash_handler", ok = false)
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Uncaught on ${thread.name}", throwable)
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "AndroWatch"
    }
}
