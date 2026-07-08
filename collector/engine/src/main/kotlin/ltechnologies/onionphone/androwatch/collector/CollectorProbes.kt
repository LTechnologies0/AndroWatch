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
    val names = outputs.mapNotNull { it.productName?.toString() }.sorted()
    if (names.isNotEmpty()) put("productNamesHash", sha256Hex(names.joinToString("|")))
    val rates = outputs.flatMap { it.sampleRates.toList() }.distinct().sorted()
    val channels = outputs.flatMap { it.channelCounts.toList() }.distinct().sorted()
    put("audioHwCaps", "rates=${rates.take(8)};ch=${channels.take(8)}")
    put("outputSampleRate", am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "n/a")
    put("outputBufferFrames", am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "n/a")
    put("micMute", am.isMicrophoneMute.toString())
    put("volumeFixed", am.isVolumeFixed.toString())
    put("audioMode", am.mode.toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        put("commDevice", am.communicationDevice?.type?.toString() ?: "none")
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
    put("networkCount", cm.allNetworks.size.toString())
    if (caps != null) {
        val capBits = (0 until 32).filter { caps.hasCapability(it) }
        put("netCapsHash", sha256Hex(capBits.joinToString(",")))
        put("captivePortal", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL).toString())
        put("notRoaming", caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING).toString())
        put("downKbps", caps.linkDownstreamBandwidthKbps.toString())
        put("upKbps", caps.linkUpstreamBandwidthKbps.toString())
    }
    link?.mtu?.let { put("mtu", it.toString()) }
    link?.routes?.size?.let { put("routeCount", it.toString()) }
    put("dataSaver", cm.restrictBackgroundStatus.toString())
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
  put("chargeCounter", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toString())
  put("currentNow", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toString())
  put("currentAvg", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE).toString())
  put("energyCounter", bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER).toString())
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      put("cycleCount", bm.getIntProperty(34 /* BATTERY_PROPERTY_CYCLE_COUNT */).toString())
  }
  sticky?.let { intent ->
      intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.let { put("technology", it) }
      intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1).let { put("plugged", it.toString()) }
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
    put("networkMccMnc", tm.networkOperator ?: "n/a")
    put("simMccMnc", tm.simOperator ?: "n/a")
    put("voiceNetworkType", safeString { tm.voiceNetworkType.toString() })
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        put("dataEnabled", runCatching { tm.isDataEnabled }.getOrDefault(false).toString())
    }
    put("dataState", tm.dataState.toString())
    put("callState", tm.callState.toString())
    put("voiceCapable", tm.isVoiceCapable.toString())
    put("smsCapable", tm.isSmsCapable.toString())
    put("dataCapable", tm.isDataCapable.toString())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        put("carrierId", tm.simCarrierId.toString())
        put("carrierName", tm.simCarrierIdName?.toString() ?: "n/a")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        put("multiSim", tm.isMultiSimSupported.toString())
        put("supportedModems", tm.supportedModemCount.toString())
    }
    val tac = runCatching { tm.typeAllocationCode }.getOrElse { "blocked" }
    put("tacStatus", if (tac.isNullOrBlank() || tac == "blocked") "blocked" else "readable")
}
