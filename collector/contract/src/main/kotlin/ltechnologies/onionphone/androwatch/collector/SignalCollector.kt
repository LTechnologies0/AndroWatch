package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.SignalCategory
import kotlinx.coroutines.flow.Flow

/**
 * Contract implemented by every category collector.
 *
 * A collector probes the device for one [SignalCategory] and returns the resulting
 * [FingerprintSignal]s. Implementations are registered in
 * [CollectorRegistry][ltechnologies.onionphone.androwatch.collector.CollectorRegistry]
 * and dispatched by the runtime. Implementations must be side-effect free beyond reading
 * device state and must tolerate missing permissions/APIs gracefully.
 *
 * @see LiveSignalCollector
 * @see SignalCategory
 * @see FingerprintSignal
 */
interface SignalCollector {
    /** The single category this collector is responsible for. */
    val category: SignalCategory

    /**
     * Probes the device once and returns the collected signals.
     *
     * @param context Android context used to access system services.
     * @return Signals for [category]; empty or error signals if probing was not possible.
     */
    suspend fun collect(context: Context): List<FingerprintSignal>
}

/**
 * A [SignalCollector] that can additionally emit continuous updates.
 *
 * Used by categories whose values change over time (e.g. battery, motion), allowing
 * the UI to observe a stream instead of polling [collect].
 */
interface LiveSignalCollector : SignalCollector {
    /**
     * Returns a cold [Flow] emitting fresh signal lists as the underlying state changes.
     *
     * @param context Android context used to register/observe the relevant listeners.
     * @return A flow of signal snapshots for [category].
     */
    fun liveFlow(context: Context): Flow<List<FingerprintSignal>>
}
