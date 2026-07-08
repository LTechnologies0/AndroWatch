package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.os.Build
import android.provider.Settings
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Shared runtime scaffolding used by collectors: concurrency limits, per-category
 * timeouts, API-level guards, safe `Settings` readers and a bounded/timed collect wrapper.
 *
 * Centralizing these concerns keeps individual collectors small and ensures every probe
 * runs under consistent timeout, concurrency and error-handling policies.
 */
object CollectorRuntime {
    /** Max concurrent passive collectors — limits CPU/IO spikes on refresh. */
    val passiveSemaphore = Semaphore(permits = 4)

    /** Default per-collector collection timeout in milliseconds. */
    const val DEFAULT_COLLECT_TIMEOUT_MS = 20_000L

    /** Timeout for the WebView fingerprint collector, which spins up a WebView. */
    const val WEBVIEW_COLLECT_TIMEOUT_MS = 12_000L

    /** Timeout for the OpenGL ES fingerprint probe, which creates a GL context. */
    const val GLES_COLLECT_TIMEOUT_MS = 8_000L

    /**
     * Returns `true` if the running platform is at least the given SDK [level].
     *
     * @param level Minimum `Build.VERSION.SDK_INT` required.
     */
    fun apiAtLeast(level: Int): Boolean = Build.VERSION.SDK_INT >= level

    /**
     * Runs [block] only when the platform is at least SDK [level], returning [fallback]
     * otherwise or if [block] throws.
     *
     * @param level Minimum SDK level required to attempt [block].
     * @param fallback Value returned when the API is unavailable or the block fails.
     * @param block Work to run when the API level is satisfied.
     * @return The block result, or [fallback].
     */
    inline fun <T> ifApi(level: Int, fallback: T, block: () -> T): T =
        if (Build.VERSION.SDK_INT >= level) runCatching(block).getOrElse { fallback } else fallback

    /**
     * Safely reads a value from `Settings.Global`, swallowing any exception.
     *
     * @param key Global settings key.
     * @return The value, or `null` if unreadable.
     */
    fun safeGlobalString(context: Context, key: String): String? = runCatching {
        Settings.Global.getString(context.contentResolver, key)
    }.getOrNull()

    /**
     * Safely reads an integer from `Settings.Global` as a string.
     *
     * @param key Global settings key.
     * @param default Value used when the key is absent.
     * @return The value as a string, or `"unavailable"` on failure.
     */
    fun safeGlobalInt(context: Context, key: String, default: Int = 0): String = runCatching {
        Settings.Global.getInt(context.contentResolver, key, default).toString()
    }.getOrElse { "unavailable" }

    /**
     * Safely reads a value from `Settings.Secure`, swallowing any exception.
     *
     * @param key Secure settings key.
     * @return The value, or `null` if unreadable.
     */
    fun safeSecureString(context: Context, key: String): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, key)
    }.getOrNull()

    /**
     * Runs a collector on the bounded IO dispatcher, wrapped in a trace slice, a timeout
     * and a `runCatching` guard so a slow or failing probe cannot stall the app.
     *
     * @param collector Collector to execute.
     * @param context Android context passed to [SignalCollector.collect].
     * @param timeoutMs Maximum time to allow the collector to run.
     * @return A [Result] with the collected signals, or a failure if it timed out/threw.
     */
    suspend fun collectBounded(
        collector: SignalCollector,
        context: Context,
        timeoutMs: Long = DEFAULT_COLLECT_TIMEOUT_MS,
    ): Result<List<FingerprintSignal>> = withContext(CollectorDispatchers.io) {
        awTrace("collect/${collector.category.name}") {
            runCatching {
                withTimeout(timeoutMs) { collector.collect(context) }
            }.onFailure { e ->
                awLogW("collect/${collector.category.name}") { e.message ?: e.javaClass.simpleName }
            }
        }
    }

    /**
     * Executes [block] while holding a permit from [passiveSemaphore], capping concurrent
     * passive collection.
     *
     * @param block Suspending work to run under the concurrency limit.
     * @return The block's result.
     */
    suspend fun <T> withPassivePermit(block: suspend () -> T): T =
        passiveSemaphore.withPermit { block() }
}
