package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.os.Build
import android.provider.Settings

/** Reads a non-blank `Settings.Secure` string, or `null`. @param key Secure setting key. */
fun readSecureString(context: Context, key: String): String? =
    Settings.Secure.getString(context.contentResolver, key)?.takeIf { it.isNotBlank() }

/** Reads a non-blank `Settings.System` string, or `null`. @param key System setting key. */
fun readSystemString(context: Context, key: String): String? =
    Settings.System.getString(context.contentResolver, key)?.takeIf { it.isNotBlank() }

/** Reads a non-blank `Settings.Global` string, or `null`. @param key Global setting key. */
fun readGlobalString(context: Context, key: String): String? =
    Settings.Global.getString(context.contentResolver, key)?.takeIf { it.isNotBlank() }

/**
 * Reads an integer from `Settings.Global`, or `null` on failure.
 *
 * @param key Global setting key.
 * @param default Value used when the key is absent.
 */
fun readGlobalInt(context: Context, key: String, default: Int = -1): Int? =
    runCatching { Settings.Global.getInt(context.contentResolver, key, default) }.getOrNull()

/**
 * Reads an integer from `Settings.Secure`, or `null` on failure.
 *
 * @param key Secure setting key.
 * @param default Value used when the key is absent.
 */
fun readSecureInt(context: Context, key: String, default: Int = -1): Int? =
    runCatching { Settings.Secure.getInt(context.contentResolver, key, default) }.getOrNull()

/**
 * Reads an integer from `Settings.System`, or `null` on failure.
 *
 * @param key System setting key.
 * @param default Value used when the key is absent.
 */
fun readSystemInt(context: Context, key: String, default: Int = -1): Int? =
    runCatching { Settings.System.getInt(context.contentResolver, key, default) }.getOrNull()

/**
 * Reads a secure setting and returns its SHA-256 hash (for sensitive string values).
 *
 * @param key Secure setting key.
 * @return Hashed value, or `null` if the setting is unset.
 */
fun probeSecureHash(context: Context, key: String): String? =
    readSecureString(context, key)?.let { sha256Hex(it) }

/**
 * Probes a profile of user-facing `Settings.System` values (brightness, timeouts, haptics,
 * rotation, pointer speed, alarm URI hash).
 *
 * Privacy: individually low-entropy personalization settings that combine into a behavioral
 * fingerprint. No permission required; the alarm URI is hashed.
 *
 * @param context Context providing the content resolver.
 * @return Map of probe keys to system-setting values.
 */
fun probeSystemSettingsProfile(context: Context): Map<String, String> = buildMap {
    readSystemInt(context, Settings.System.SCREEN_BRIGHTNESS)?.let { put("brightness", it.toString()) }
    readSystemInt(context, Settings.System.SCREEN_BRIGHTNESS_MODE)?.let { put("brightnessMode", it.toString()) }
    readSystemInt(context, Settings.System.SCREEN_OFF_TIMEOUT)?.let { put("screenTimeout", it.toString()) }
    readSystemInt(context, Settings.System.SOUND_EFFECTS_ENABLED)?.let { put("soundEffects", it.toString()) }
    readSystemInt(context, Settings.System.VIBRATE_ON)?.let { put("vibrateOn", it.toString()) }
    readSystemInt(context, Settings.System.VIBRATE_WHEN_RINGING)?.let { put("vibrateRing", it.toString()) }
    readSystemInt(context, Settings.System.HAPTIC_FEEDBACK_ENABLED)?.let { put("haptic", it.toString()) }
    readSystemInt(context, Settings.System.ACCELEROMETER_ROTATION)?.let { put("accelRotation", it.toString()) }
    readSystemInt(context, Settings.System.USER_ROTATION)?.let { put("userRotation", it.toString()) }
    readSystemInt(context, "pointer_speed")?.let { put("pointerSpeed", it.toString()) }
    readSystemString(context, "default_alarm_alert")?.let { put("alarmUri", sha256Hex(it)) }
}

/**
 * Probes a profile of `Settings.Global` values (boot count, airplane/auto-time, provisioning,
 * Wi-Fi/mobile data, zen mode, HTTP proxy hash, private DNS host hash on Android 9+).
 *
 * Privacy: boot count is a semi-stable counter; proxy/private-DNS values are network-config
 * signals and are hashed. No permission required to read these globals.
 *
 * @param context Context providing the content resolver.
 * @return Map of probe keys to global-setting values.
 */
fun probeGlobalSettingsProfile(context: Context): Map<String, String> = buildMap {
    readGlobalInt(context, Settings.Global.BOOT_COUNT)?.let { put("bootCount", it.toString()) }
    readGlobalInt(context, Settings.Global.AIRPLANE_MODE_ON)?.let { put("airplane", it.toString()) }
    readGlobalInt(context, Settings.Global.AUTO_TIME)?.let { put("autoTime", it.toString()) }
    readGlobalInt(context, Settings.Global.AUTO_TIME_ZONE)?.let { put("autoTz", it.toString()) }
    readGlobalInt(context, Settings.Global.DEVICE_PROVISIONED)?.let { put("provisioned", it.toString()) }
    readGlobalInt(context, Settings.Global.WIFI_ON)?.let { put("wifiOn", it.toString()) }
    readGlobalInt(context, "mobile_data", 0)?.let { put("mobileData", it.toString()) }
    readGlobalInt(context, "zen_mode")?.let { put("zenMode", it.toString()) }
    readGlobalInt(context, "network_recommendations_enabled")?.let {
        put("netRecommendations", it.toString())
    }
    readGlobalInt(context, Settings.Global.STAY_ON_WHILE_PLUGGED_IN)?.let { put("stayAwake", it.toString()) }
    readGlobalString(context, Settings.Global.HTTP_PROXY)?.let { put("httpProxy", sha256Hex(it)) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        readGlobalString(context, "private_dns_specifier")?.let { put("privateDnsHost", sha256Hex(it)) }
    }
}

/**
 * Probes a profile of `Settings.Secure` values (navigation mode, autofill/voice IME package,
 * accessibility speak-password, one-handed mode, night display, notification-listener hash,
 * IME subtype).
 *
 * Privacy: reveals configured autofill/keyboard providers and enabled notification listeners
 * (the latter hashed). No permission required to read these settings.
 *
 * @param context Context providing the content resolver.
 * @return Map of probe keys to secure-setting values.
 */
fun probeSecureSettingsProfile(context: Context): Map<String, String> = buildMap {
    readSecureInt(context, "navigation_mode")?.let { put("navMode", it.toString()) }
    readSecureString(context, "autofill_service")?.let { put("autofill", it.substringBefore('/')) }
    readSecureString(context, "default_voice_input_method")?.let { put("voiceIme", it.substringBefore('/')) }
    readSecureInt(context, Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD)?.let { put("speakPassword", it.toString()) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        readSecureInt(context, "one_handed_mode_activated")?.let { put("oneHanded", it.toString()) }
    }
    readSecureInt(context, "night_display_activated")?.let { put("nightDisplayOn", it.toString()) }
    readSecureInt(context, "night_display_auto_mode")?.let { put("nightDisplayAuto", it.toString()) }
    readSecureString(context, "enabled_notification_listeners")?.let {
        put("notifListenersHash", sha256Hex(it))
    }
    readSecureString(context, Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE)?.let { put("imeSubtype", it) }
}

/**
 * Probes accessibility-related `Settings.Secure` values (enabled a11y services hash,
 * daltonizer/color-inversion, captioning preset and font scale).
 *
 * Privacy: enabled accessibility services can indicate assistive-tech usage (health-adjacent);
 * the service list is hashed. No permission required.
 *
 * @param context Context providing the content resolver.
 * @return Map of probe keys to accessibility-setting values.
 */
fun probeAccessibilitySettings(context: Context): Map<String, String> = buildMap {
    readSecureString(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.let {
        put("a11yServicesHash", sha256Hex(it))
    }
    readSecureInt(context, "accessibility_display_daltonizer")?.let { put("daltonizerMode", it.toString()) }
    readSecureInt(context, Settings.Secure.ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)?.let {
        put("inversion", it.toString())
    }
    readSecureInt(context, "accessibility_captioning_preset")?.let { put("captionPreset", it.toString()) }
    readSecureInt(context, "accessibility_captioning_font_scale")?.let { put("captionFontScale", it.toString()) }
}

/**
 * Returns a SHA-256 hash of the enabled input-methods list.
 *
 * Privacy: enabled keyboards can reveal installed third-party IMEs; the list is hashed.
 * No permission required.
 *
 * @param context Context providing the content resolver.
 * @return Hashed enabled-IME list, or `null` if unreadable.
 */
fun probeEnabledImeHash(context: Context): String? =
    readSecureString(context, Settings.Secure.ENABLED_INPUT_METHODS)?.let { sha256Hex(it) }
