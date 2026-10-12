package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Probes extended audio-hardware characteristics via [AudioManager] and the supplied
 * output [AudioDeviceInfo]s.
 *
 * Reads device product names (hashed), supported sample rates/channel counts, output
 * sample rate and buffer size, mic-mute/volume-fixed flags and audio mode. On Android 12+
 * it also reports the active communication device type.
 *
 * Privacy: audio hardware capabilities and product-name hashes contribute to a device
 * fingerprint but require no runtime permission.
 *
 * @param context Context used to obtain the [AudioManager] system service.
 * @param outputs Output devices previously enumerated by the caller.
 * @return Map of probe keys to stringified values for the Audio category.
 */
fun probeAudioExtended(context: Context, outputs: Array<AudioDeviceInfo>): Map<String, String> = buildMap {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    putSafe("productNamesHash") {
        val names = outputs.mapNotNull { it.productName?.toString() }.sorted()
        names.takeIf { it.isNotEmpty() }?.let { sha256Hex(it.joinToString("|")) }
    }
    putSafe("audioHwCaps") {
        val rates = outputs.flatMap { it.sampleRates.toList() }.distinct().sorted()
        val channels = outputs.flatMap { it.channelCounts.toList() }.distinct().sorted()
        "rates=${rates.take(8)};ch=${channels.take(8)}"
    }
    putSafe("outputSampleRate") { am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "n/a" }
    putSafe("outputBufferFrames") { am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "n/a" }
    putSafe("micMute") { am.isMicrophoneMute.toString() }
    putSafe("volumeFixed") { am.isVolumeFixed.toString() }
    putSafe("audioMode") { am.mode.toString() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        putSafe("commDevice") { am.communicationDevice?.type?.toString() ?: "none" }
    }
}

/**
 * Probes extended network characteristics from [ConnectivityManager] and the active
 * network's [NetworkCapabilities] / [android.net.LinkProperties].
 *
 * Reports active network count, a hash of capability bits, captive-portal / roaming flags,
 * estimated up/down bandwidth, MTU, route count and data-saver status.
 *
 * Privacy: exposes network topology characteristics useful for fingerprinting; requires no
 * runtime permission for the passive fields read here.
 *
 * @param cm Connectivity manager used to enumerate networks and data-saver state.
 * @param caps Capabilities of the active network, or `null` if unavailable.
 * @param link Link properties of the active network, or `null` if unavailable.
 * @return Map of probe keys to stringified values for the Network category.
 */
fun probeNetworkExtended(
    cm: ConnectivityManager,
    caps: NetworkCapabilities?,
    link: android.net.LinkProperties?,
): Map<String, String> = buildMap {
    putSafe("networkCount") { cm.allNetworks.size.toString() }
    if (caps != null) {
        putSafe("netCapsHash") {
            val capBits = (0 until 32).filter { caps.hasCapability(it) }
            sha256Hex(capBits.joinToString(","))
        }
        putSafe("captivePortal") {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL).toString()
        }
        putSafe("notRoaming") {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING).toString()
        }
        putSafe("downKbps") { caps.linkDownstreamBandwidthKbps.toString() }
        putSafe("upKbps") { caps.linkUpstreamBandwidthKbps.toString() }
    }
    putSafe("mtu") { link?.mtu?.toString() }
    putSafe("routeCount") { link?.routes?.size?.toString() }
    putSafe("dataSaver") { cm.restrictBackgroundStatus.toString() }
}

/**
 * Probes extended battery telemetry from [BatteryManager] and the sticky battery
 * broadcast intent.
 *
 * Reports charge counter, instantaneous/average current, energy counter, and (on
 * Android 14+) charge cycle count, plus battery technology and plugged state from the
 * sticky intent.
 *
 * Privacy: battery counters are relatively stable and can aid device fingerprinting;
 * no runtime permission is required.
 *
 * @param bm Battery manager used to read integer/long battery properties.
 * @param sticky The sticky `ACTION_BATTERY_CHANGED` intent, or `null` if unavailable.
 * @return Map of probe keys to stringified values for the Battery category.
 */
fun probeBatteryExtended(
    bm: BatteryManager,
    sticky: android.content.Intent?,
): Map<String, String> = buildMap {
    putSafe("chargeCounter") { bm.intPropertyOrNull(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.toString() }
    putSafe("currentNow") { bm.intPropertyOrNull(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.toString() }
    putSafe("currentAvg") { bm.intPropertyOrNull(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.toString() }
    putSafe("energyCounter") { bm.longPropertyOrNull(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)?.toString() }
    sticky?.let { intent ->
        putSafe("technology") { intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) }
        putSafe("plugged") {
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, Int.MIN_VALUE)
                .takeUnless { it == Int.MIN_VALUE }?.toString()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            putSafe("cycleCount") {
                intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, Int.MIN_VALUE)
                    .takeUnless { it == Int.MIN_VALUE }?.toString()
            }
        }
    }
}

/**
 * Probes extended telephony characteristics from [TelephonyManager].
 *
 * Reports network/SIM MCC+MNC, voice network type, data enabled/state, call state,
 * voice/SMS/data capability flags, carrier id/name (Android 9+), multi-SIM support and
 * modem count (Android 11+), and whether the Type Allocation Code is readable or blocked.
 *
 * Privacy: carrier and SIM operator codes are moderately identifying. All reads here are
 * wrapped defensively; identifiers restricted by the platform surface as `blocked`/`n/a`
 * rather than throwing. No `READ_PHONE_STATE` permission is used.
 *
 * @param tm Telephony manager used to read carrier/SIM metadata.
 * @return Map of probe keys to stringified values for the Telephony category.
 */
fun probeTelephonyExtended(tm: TelephonyManager): Map<String, String> = buildMap {
    putSafe("networkMccMnc") { tm.networkOperator ?: "n/a" }
    putSafe("simMccMnc") { tm.simOperator ?: "n/a" }
    putSafe("voiceNetworkType") { tm.voiceNetworkType.toString() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        putSafe("dataEnabled") { tm.isDataEnabled.toString() }
    }
    putSafe("dataState") { tm.dataState.toString() }
    putSafe("callState") { tm.callState.toString() }
    putSafe("voiceCapable") { tm.isVoiceCapable.toString() }
    putSafe("smsCapable") { tm.isSmsCapable.toString() }
    putSafe("dataCapable") { tm.isDataCapable.toString() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        putSafe("carrierId") { tm.simCarrierId.toString() }
        putSafe("carrierName") { tm.simCarrierIdName?.toString() ?: "n/a" }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        putSafe("multiSim") { tm.isMultiSimSupported.toString() }
        putSafe("supportedModems") { tm.supportedModemCount.toString() }
    }
    putSafe("tacStatus") {
        val tac = runCatching { tm.typeAllocationCode }.getOrElse { "blocked" }
        if (tac.isNullOrBlank() || tac == "blocked") "blocked" else "readable"
    }
}

/**
 * Puts [block]'s result under [key] only when the read succeeds and is non-null/non-blank.
 * A [SecurityException] (or any other failure) skips that one field and leaves the rest of
 * the probe map intact.
 */
internal inline fun MutableMap<String, String>.putSafe(key: String, block: () -> String?) {
    runCatching { block() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { put(key, it) }
}

private fun BatteryManager.intPropertyOrNull(property: Int): Int? {
    val value = runCatching { getIntProperty(property) }.getOrNull() ?: return null
    return value.takeUnless { it == Int.MIN_VALUE }
}

private fun BatteryManager.longPropertyOrNull(property: Int): Long? {
    val value = runCatching { getLongProperty(property) }.getOrNull() ?: return null
    return value.takeUnless { it == Long.MIN_VALUE }
}
