package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Aggregate result of an mDNS/NSD local-network service discovery sweep.
 *
 * @property servicesFound Total number of services discovered across all queried types.
 * @property serviceTypes Per-service-type counts (only types with >0 hits are included).
 * @property discoverySuccess Whether discovery ran successfully (vs. failing to start).
 */
data class NsdDiscoveryResult(
    val servicesFound: Int,
    val serviceTypes: Map<String, Int>,
    val discoverySuccess: Boolean,
)

/** mDNS service types probed to characterize the local network (cast, AirPlay, printers, etc.). */
private val MDNS_SERVICE_TYPES = listOf(
    "_http._tcp.",
    "_googlecast._tcp.",
    "_airplay._tcp.",
    "_ipp._tcp.",
    "_printer._tcp.",
)

/**
 * Discovers mDNS/DNS-SD services on the local network via [NsdManager] for a bounded time.
 *
 * Iterates the [MDNS_SERVICE_TYPES], counting responders per type. This characterizes the
 * user's local network (smart TVs, printers, cast targets) without connecting to any service.
 *
 * Privacy: **high** — local network device presence can identify a home/office network and
 * nearby devices. On Android 13+ this typically requires the `NEARBY_WIFI_DEVICES`
 * permission (see [ltechnologies.onionphone.androwatch.permission.PermissionKind.LocalNetwork]);
 * the caller is responsible for the gate.
 *
 * @param context Context used to obtain [NsdManager].
 * @param timeoutMs Per-service-type discovery window in milliseconds.
 * @return Aggregated [NsdDiscoveryResult]; a zeroed result with `discoverySuccess=false`
 *   if NSD is unavailable.
 */
suspend fun discoverLocalNetworkServices(
    context: Context,
    timeoutMs: Long = 3500L,
): NsdDiscoveryResult {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        ?: return NsdDiscoveryResult(0, emptyMap(), false)

    val typeCounts = mutableMapOf<String, Int>()
    var totalFound = 0
    var anySuccess = false
    var anyFailure = false

    for (serviceType in MDNS_SERVICE_TYPES) {
        val count = discoverType(nsd, serviceType, timeoutMs)
        if (count >= 0) {
            anySuccess = true
            if (count > 0) typeCounts[serviceType] = count
            totalFound += count.coerceAtLeast(0)
        } else {
            anyFailure = true
        }
    }

    return NsdDiscoveryResult(
        servicesFound = totalFound,
        serviceTypes = typeCounts,
        discoverySuccess = anySuccess && (!anyFailure || totalFound > 0),
    )
}

/**
 * Discovers a single mDNS [serviceType], counting responders within [timeoutMs].
 *
 * Bridges the callback-based [NsdManager.DiscoveryListener] into a cancellable coroutine,
 * always stopping discovery via a timeout handler or on cancellation to avoid leaking the
 * listener.
 *
 * @param nsd Network service discovery manager.
 * @param serviceType Fully-qualified mDNS service type (e.g. `_googlecast._tcp.`).
 * @param timeoutMs Discovery window in milliseconds.
 * @return Number of services found, or `-1` if discovery failed to start.
 */
private suspend fun discoverType(
    nsd: NsdManager,
    serviceType: String,
    timeoutMs: Long,
): Int = withTimeoutOrNull(timeoutMs + 500L) {
    suspendCancellableCoroutine { cont ->
        var found = 0
        var stopped = false
        fun finish(value: Int) {
            if (stopped) return
            stopped = true
            if (cont.isActive) cont.resume(value)
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onServiceFound(service: NsdServiceInfo) { found++ }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(type: String) { finish(found) }
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) { finish(-1) }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) { finish(found) }
        }
        runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { finish(-1) }
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { nsd.stopServiceDiscovery(listener) }
            finish(found)
        }, timeoutMs)
        cont.invokeOnCancellation {
            stopped = true
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
} ?: -1
