package ltechnologies.onionphone.androwatch.collector

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Bounded IO pool for binder/disk/sensor reads — matches [CollectorRuntime.passiveSemaphore].
 *
 * Centralizing the dispatchers keeps collector concurrency capped so simultaneous
 * probing does not saturate the device with binder/disk traffic.
 */
object CollectorDispatchers {
    /** IO dispatcher limited to 4 concurrent operations for binder/disk/sensor reads. */
    val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)

    /** Default (CPU-bound) dispatcher for hashing and in-memory processing. */
    val default: CoroutineDispatcher = Dispatchers.Default
}
