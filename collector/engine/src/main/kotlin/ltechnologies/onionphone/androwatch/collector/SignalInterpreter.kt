package ltechnologies.onionphone.androwatch.collector

import android.os.Build

/**
 * Pure functions that turn raw probe values into human-readable, privacy-aware descriptions.
 *
 * Every function is deterministic and side-effect free: it takes already-collected raw data
 * and returns the presentation/rationale text shown in the UI and exports. Keeping this
 * interpretation logic separate from collection makes it easy to test and reuse. Many
 * descriptions explicitly call out the fingerprinting/privacy relevance of each signal.
 */
object SignalInterpreter {

    /**
     * Describes the device's retail identity from build fields.
     *
     * @param manufacturer `Build.MANUFACTURER`.
     * @param brand `Build.BRAND`.
     * @param model `Build.MODEL`.
     * @param device `Build.DEVICE` codename.
     * @return Human-readable retail profile with codename/brand qualifiers.
     */
    fun deviceProfile(manufacturer: String, brand: String, model: String, device: String): String {
        val retail = listOf(manufacturer, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
        return buildString {
            append("Retail profile: ")
            append(if (retail.isNotBlank()) retail else "unknown")
            if (device.isNotBlank() && !device.equals(model, ignoreCase = true)) {
                append(" (codename ")
                append(device)
                append(')')
            }
            if (brand.isNotBlank() && !brand.equals(manufacturer, ignoreCase = true)) {
                append(" — brand ")
                append(brand)
            }
        }
    }

    /**
     * Explains the significance of the app-scoped `ANDROID_ID`.
     *
     * @param raw Raw ANDROID_ID value, or `null`/`n/a` if unavailable.
     * @return Description including its stability/tracking properties.
     */
    fun androidId(raw: String?): String {
        val id = raw?.takeIf { it.isNotBlank() && it != "n/a" } ?: return "No ANDROID_ID returned."
        return "Stable app-scoped ID (${id.length} hex chars). " +
            "Even on hardened Android, any app can read it without asking you. " +
            "Same value after reinstall (same developer signature); changes on factory reset or new user profile."
    }

    /**
     * Interprets the OS build fingerprint string.
     *
     * @param raw Slash-delimited `Build.FINGERPRINT` value.
     * @return Description highlighting the firmware-lineage identifiability.
     */
    fun buildFingerprint(raw: String): String {
        if (raw.isBlank()) return "Build fingerprint unavailable."
        val parts = raw.split('/')
        val product = parts.getOrNull(1) ?: raw
        return "Exact OS build tag: $product. Highly identifying — ties this app to one firmware lineage."
    }

    /**
     * Describes the SoC/board and CPU ABI class.
     *
     * @param hardware `Build.HARDWARE` string.
     * @param abis Supported ABIs (`Build.SUPPORTED_ABIS`).
     * @return Human-readable SoC + CPU class description.
     */
    fun hardwarePlatform(hardware: String, abis: List<String>): String {
        val abiLabel = when {
            abis.any { it.contains("arm64", ignoreCase = true) } -> "64-bit ARM"
            abis.any { it.contains("armeabi", ignoreCase = true) } -> "32-bit ARM"
            abis.any { it.contains("x86_64", ignoreCase = true) } -> "x86_64"
            else -> abis.firstOrNull() ?: "unknown ABI"
        }
        return "SoC/board hint: $hardware. CPU class: $abiLabel (${abis.joinToString()})."
    }

    /**
     * Explains the significance of the Widevine/MediaDRM device id.
     *
     * @param raw Hashed DRM id, or `"unavailable"`.
     * @return Description emphasizing its strength as a tracking anchor.
     */
    fun mediaDrmId(raw: String): String = when {
        raw == "unavailable" -> "Widevine DRM ID not readable (no DRM stack or blocked on this ROM)."
        raw.length >= 8 -> "Hardware DRM ID (Widevine). No permission needed. " +
            "Often survives app reinstall; on many devices persists across factory reset — " +
            "one of the strongest tracking anchors left on hardened Android."
        else -> "Widevine ID returned but unexpectedly short."
    }

    /**
     * Interprets the user-visible Bluetooth device name.
     *
     * @param raw Bluetooth friendly name, or `null`/`n/a`.
     * @return Description noting owner/household identifiability.
     */
    fun bluetoothName(raw: String?): String {
        val name = raw?.takeIf { it.isNotBlank() && it != "n/a" }
        return if (name != null) {
            "User-visible Bluetooth name set to \"$name\" — can identify owner or household."
        } else {
            "Bluetooth friendly name not exposed to this app."
        }
    }

    /**
     * Describes the default keyboard (IME) package.
     *
     * @param raw `component/service` IME id, or `null`/`n/a`.
     * @return Description with the keyboard package as a preference signal.
     */
    fun defaultIme(raw: String?): String {
        val ime = raw?.takeIf { it.isNotBlank() && it != "n/a" } ?: return "Default keyboard unknown."
        val pkg = ime.substringBefore('/')
        return "Default keyboard package: $pkg. Keyboard choice is a long-lived preference signal."
    }

    /**
     * Describes the Android version, API level and security patch cohort.
     *
     * @param sdk `Build.VERSION.SDK_INT`.
     * @param release `Build.VERSION.RELEASE`, or `null`.
     * @param patch Security patch date string.
     * @return Human-readable OS version summary.
     */
    fun osVersion(sdk: Int, release: String?, patch: String): String {
        val rel = release?.takeIf { it.isNotBlank() } ?: "?"
        return "Android $rel (API $sdk). Security patch $patch — pinpoints monthly firmware cohort."
    }

    /**
     * Describes device uptime since last boot as a session-stability signal.
     *
     * @param ms Elapsed time since boot in milliseconds.
     * @return Human-readable uptime description.
     */
    fun uptime(ms: Long): String {
        val sec = ms / 1000
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return when {
            h > 48 -> "Device uptime ${h}h — likely not rebooted recently (session-stable signal)."
            h > 0 -> "Last boot about ${h}h ${m}m ago."
            m > 0 -> "Last boot about ${m} minutes ago."
            else -> "Booted within the last minute."
        }
    }

    /**
     * Interprets the user-assigned device name from system settings.
     *
     * @param raw Device name, or `null`/`n/a`.
     * @return Description noting owner identifiability.
     */
    fun deviceName(raw: String?): String {
        val name = raw?.takeIf { it.isNotBlank() && it != "n/a" }
        return if (name != null) {
            "User-assigned device name: \"$name\" — may directly identify the owner."
        } else {
            "No custom device name set in system settings."
        }
    }

    /**
     * Maps a boolean-style setting value to a descriptive phrase.
     *
     * @param label Setting label used in the fallback text.
     * @param raw Raw value (`"1"`/`"true"`/`"0"`/`"false"`/`"unavailable"`).
     * @param enabledMeaning Text when enabled.
     * @param disabledMeaning Text when disabled.
     * @return The chosen description.
     */
    fun boolSetting(label: String, raw: String, enabledMeaning: String, disabledMeaning: String): String =
        when (raw) {
            "1", "true" -> enabledMeaning
            "0", "false" -> disabledMeaning
            "unavailable" -> "$label: setting not readable."
            else -> "$label: $raw"
        }

    /**
     * Describes a biometric capability's enrollment/availability status.
     *
     * @param kind Biometric kind label (e.g. fingerprint, face).
     * @param raw Status token (`available`, `not_enrolled`, `no_hardware`, ...).
     * @return Human-readable biometric status.
     */
    fun biometricStatus(kind: String, raw: String): String = when (raw) {
        "available" -> "$kind biometrics enrolled and ready."
        "not_enrolled" -> "$kind sensor present but no fingerprint/face enrolled."
        "no_hardware" -> "No $kind biometric hardware."
        "hw_unavailable" -> "$kind biometric hardware temporarily unavailable."
        "permission_denied" -> "$kind biometrics: permission not granted to query."
        else -> "$kind biometrics: $raw"
    }

    /**
     * Describes presence/absence of a named hardware feature flag.
     *
     * @param label Feature label.
     * @param present Whether the feature is reported present.
     * @return Human-readable feature-flag description.
     */
    fun featureFlag(label: String, present: Boolean): String =
        if (present) "Device reports $label support." else "No $label feature flag."

    /**
     * Summarizes screen geometry (resolution, DPI bucket, refresh rate) as a fingerprint axis.
     *
     * @param width Width in pixels.
     * @param height Height in pixels.
     * @param dpi Density in dpi.
     * @param refreshHz Panel refresh rate in Hz, or `null`.
     * @return Human-readable display profile.
     */
    fun displayProfile(width: Int, height: Int, dpi: Int, refreshHz: Float?): String {
        val bucket = when {
            dpi >= 560 -> "xxxhdpi"
            dpi >= 420 -> "xxhdpi"
            dpi >= 280 -> "xhdpi"
            dpi >= 200 -> "hdpi"
            else -> "mdpi-or-lower"
        }
        val refresh = refreshHz?.let { " · ${it.toInt()} Hz panel" }.orEmpty()
        return "${width}×$height px @ ${dpi} dpi ($bucket)$refresh — screen geometry is a classic fingerprint axis."
    }

    /**
     * Describes current screen orientation.
     *
     * @param raw Orientation token (`landscape`/`portrait`/other).
     * @return Human-readable orientation.
     */
    fun orientation(raw: String): String = when (raw) {
        "landscape" -> "Layout currently landscape."
        "portrait" -> "Layout currently portrait."
        else -> "Orientation: $raw"
    }

    /**
     * Describes the dark/light night-mode preference.
     *
     * @param raw Night-mode token (`on`/`off`/other).
     * @return Human-readable theme description.
     */
    fun nightMode(raw: String): String = when (raw) {
        "on" -> "Dark theme active — persistent UI preference."
        "off" -> "Light theme active."
        else -> "Night mode: $raw"
    }

    /**
     * Maps a `Configuration.UI_MODE_TYPE_*` constant to a device-class description.
     *
     * @param type UI mode type constant.
     * @return Human-readable UI mode (phone, TV, automotive, watch, ...).
     */
    fun uiModeType(type: Int): String = when (type) {
        android.content.res.Configuration.UI_MODE_TYPE_NORMAL -> "Normal phone/tablet UI mode."
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION -> "Android TV mode."
        android.content.res.Configuration.UI_MODE_TYPE_CAR -> "Android Automotive mode."
        android.content.res.Configuration.UI_MODE_TYPE_DESK -> "Desktop/desk dock mode."
        android.content.res.Configuration.UI_MODE_TYPE_WATCH -> "Wear OS mode."
        else -> "UI mode type code $type."
    }

    /**
     * Summarizes locale/language and timezone as a regional fingerprint.
     *
     * @param default Default locale tag.
     * @param all All configured locale tags.
     * @param tz Timezone id.
     * @param offsetMinutes UTC offset in milliseconds (despite the name; divided by 3.6e6).
     * @return Human-readable locale/timezone profile.
     */
    fun localeProfile(default: String, all: List<String>, tz: String, offsetMinutes: Int): String {
        val offsetHours = offsetMinutes / 3_600_000.0
        val langs = if (all.isEmpty()) default else all.joinToString()
        return "Languages: $langs. Timezone $tz (UTC${if (offsetHours >= 0) "+" else ""}$offsetHours). Regional fingerprint."
    }

    /**
     * Describes the account-authenticator ecosystem present on the device.
     *
     * @param types Registered account type strings (e.g. `com.google`).
     * @return Human-readable account footprint description.
     */
    fun accountEcosystem(types: List<String>): String = when {
        types.isEmpty() -> "No account authenticators registered."
        types.any { it == "com.google" } ->
            "Google account stack present (${types.size} authenticator type(s))."
        else -> "${types.size} account type(s): ${types.joinToString()} — enterprise/OEM account footprint."
    }

    /**
     * Describes display cutout (notch) presence and geometry.
     *
     * @param hasCutout Whether a cutout is present.
     * @param bounds Cutout bounds description, or `null`.
     * @return Human-readable cutout description.
     */
    fun displayCutout(hasCutout: Boolean, bounds: String?): String = when {
        !hasCutout -> "No display cutout (flat panel or hidden notch)."
        bounds != null -> "Display cutout present ($bounds) — hardware-specific geometry."
        else -> "Display cutout present — hardware-specific geometry."
    }

    /**
     * Describes the build type and tags as an OEM release-channel signal.
     *
     * @param tags `Build.TAGS`.
     * @param type `Build.TYPE`.
     * @return Human-readable build-channel description.
     */
    fun buildTags(tags: String, type: String): String =
        "Build type $type, tags $tags — OEM release channel signal."

    /**
     * Truncates a hex hash for display, appending its full length.
     *
     * @param hex Full hex string.
     * @param visible Number of leading characters to keep.
     * @return Truncated display string, or the original if short enough.
     */
    fun truncateHash(hex: String, visible: Int = 12): String =
        if (hex.length <= visible) hex else "${hex.take(visible)}… (${hex.length} hex chars)"

    /**
     * Renders a [RomProfile] into a human-readable ROM summary with spoofing hints.
     *
     * @param profile Detected ROM profile.
     * @return Multi-sentence description of family, confidence, integrity and anomalies.
     * @see RomCompatibility.detectRomProfile
     */
    fun romProfile(profile: RomProfile): String = buildString {
        append("Detected ROM family: ${profile.family}")
        profile.variant?.let { append(" ($it)") }
        append(". Confidence: ${profile.confidence}. ")
        append("Build integrity: ${profile.buildIntegrity}. ")
        if (profile.spoofingHints.isNotEmpty()) {
            append("Spoofing/custom-ROM hints: ")
            append(profile.spoofingHints.joinToString("; "))
            append('.')
        } else {
            append("No obvious property-spoofing anomalies.")
        }
    }

    /**
     * Describes how many ROM-specific system properties were exposed.
     *
     * @param hints Map of ROM property hints from [RomProfile.propertyHints].
     * @return Human-readable footprint summary.
     */
    fun romPropertyFootprint(hints: Map<String, String>): String = when {
        hints.isEmpty() -> "No custom-ROM system properties readable (stock build or blocked reflection)."
        else -> "${hints.size} ROM-specific property(ies) exposed via SystemProperties — " +
            "custom ROMs (LineageOS, GrapheneOS, /e/OS, crDroid…) leave distinct traces."
    }

    /**
     * Summarizes the accessibility configuration as an assistive-tech usage signal.
     *
     * @param enabled Whether any accessibility services are active.
     * @param touchExplore Whether touch exploration (TalkBack-like) is on.
     * @param serviceCount Number of enabled accessibility services.
     * @param fontScale System font scale factor.
     * @param inversion Whether color inversion is enabled.
     * @return Human-readable accessibility profile.
     */
    fun accessibilityProfile(
        enabled: Boolean,
        touchExplore: Boolean,
        serviceCount: Int,
        fontScale: Float,
        inversion: Boolean,
    ): String = buildString {
        if (enabled) append("Accessibility services active — reveals assistive-tech usage. ")
        else append("No accessibility services enabled. ")
        if (touchExplore) append("Touch exploration (TalkBack-like) on — highly identifying. ")
        if (serviceCount > 0) append("$serviceCount enabled service(s). ")
        if (fontScale != 1.0f) append("Font scale ${fontScale}x — vision preference signal. ")
        if (inversion) append("Color inversion enabled.")
    }.ifBlank { "Default accessibility profile." }

    /**
     * Describes the enumerated hardware sensor inventory.
     *
     * @param count Number of sensors enumerated.
     * @param typeSummary Summary of sensor types.
     * @param sample Optional sample of sensor identifiers.
     * @return Human-readable sensor-inventory description.
     */
    fun sensorInventory(count: Int, typeSummary: String, sample: String): String =
        "$count hardware sensor(s) enumerated without permission. " +
            "Vendor/model strings form a device-unique hardware list. Types: $typeSummary. " +
            if (sample.isNotBlank()) "Sample: $sample." else ""

    /**
     * Summarizes battery state as a session-correlation signal.
     *
     * @param levelPct Battery level percentage.
     * @param charging Whether the device is charging.
     * @param health Battery health string, or `"unknown"`.
     * @param voltageMv Battery voltage in millivolts (0 if unknown).
     * @return Human-readable battery profile.
     */
    fun batteryProfile(levelPct: Int, charging: Boolean, health: String, voltageMv: Int): String =
        buildString {
            append("Battery at $levelPct%")
            if (charging) append(", charging")
            append(". ")
            append("Level + temperature drift can correlate sessions across apps (no permission). ")
            if (health != "unknown") append("Health: $health. ")
            if (voltageMv > 0) append("Voltage ${voltageMv}mV.")
        }

    /**
     * Summarizes storage capacity/free space and volume counts.
     *
     * @param totalGb Total storage in GB.
     * @param freeGb Free storage in GB.
     * @param volumes Number of storage volumes.
     * @param removable Number of removable volumes.
     * @return Human-readable storage profile.
     */
    fun storageProfile(totalGb: Double, freeGb: Double, volumes: Int, removable: Int): String =
        String.format(
            "%.1f GB total, %.1f GB free. %d volume(s), %d removable. " +
                "Storage tier and free-space ratio narrow device cohort and usage habits.",
            totalGb, freeGb, volumes, removable,
        )

    /**
     * Summarizes the active network transports and LAN topology.
     *
     * @param transports Active transport labels (e.g. wifi, cellular).
     * @param validated Whether internet connectivity is validated.
     * @param metered Whether the connection is metered.
     * @param localIps Local IP addresses string.
     * @param vpn Whether a VPN tunnel is active.
     * @return Human-readable network profile.
     */
    fun networkProfile(
        transports: List<String>,
        validated: Boolean,
        metered: Boolean,
        localIps: String,
        vpn: Boolean,
    ): String = buildString {
        append("Active: ")
        append(transports.joinToString().ifBlank { "offline" })
        append(if (validated) " (internet validated)" else " (not validated)")
        append(if (metered) ", metered" else ", unmetered")
        if (vpn) append(", VPN tunnel")
        append(". Local IPs: ")
        append(localIps.ifBlank { "none" })
        append(" — LAN topology fingerprint; readable with ACCESS_NETWORK_STATE only.")
    }

    /**
     * Describes the system font inventory and probed families.
     *
     * @param count Number of system fonts (or negative if the API is unavailable).
     * @param probes Probed font family names.
     * @return Human-readable font-inventory description.
     */
    fun fontInventory(count: Int, probes: List<String>): String =
        if (count >= 0) {
            "$count system fonts (API 29+). Probed families: ${probes.joinToString()}. " +
                "Font list + fallback chain used in WebView/canvas fingerprinting."
        } else {
            "System font API unavailable (API < 29). Family probes still reveal OEM font stack."
        }

    /**
     * Summarizes installed text-to-speech engines and voices.
     *
     * @param engineCount Number of TTS engines.
     * @param voiceCount Number of installed voices.
     * @param defaultEngine Default engine package.
     * @return Human-readable TTS profile.
     */
    fun ttsProfile(engineCount: Int, voiceCount: Int, defaultEngine: String): String =
        "$engineCount TTS engine(s), $voiceCount installed voice(s). Default: $defaultEngine. " +
            "Voice/locale inventory is stable and differs by OEM + user installs."

    /**
     * Describes clipboard metadata without reading its contents.
     *
     * @param hasClip Whether the clipboard has content.
     * @param mimeTypes MIME types present on the clip.
     * @param sensitive Sensitivity flag (Android 13+), or `null` if unknown.
     * @return Human-readable clipboard metadata description.
     */
    fun clipboardMeta(hasClip: Boolean, mimeTypes: List<String>, sensitive: Boolean?): String = buildString {
        append(if (hasClip) "Clipboard has content" else "Clipboard empty")
        append(if (mimeTypes.isNotEmpty()) " (${mimeTypes.joinToString()})" else "")
        append(". ")
        when (sensitive) {
            true -> append("Marked sensitive (API 33+) — apps still see metadata without reading text. ")
            false -> append("Not flagged sensitive. ")
            null -> Unit
        }
        append("Since Android 10, background apps cannot read clipboard contents, but metadata timing still leaks copy activity.")
    }

    /**
     * Summarizes audio outputs, ringer mode and volume levels.
     *
     * @param outputs Number of audio output devices.
     * @param types Output device type summary.
     * @param ringer Ringer mode string.
     * @param volumes Volume levels summary.
     * @return Human-readable audio profile.
     */
    fun audioProfile(outputs: Int, types: String, ringer: String, volumes: String): String =
        "$outputs audio output(s): ${types.ifBlank { "builtin only" }}. Ringer: $ringer. " +
            "Volumes: $volumes. Route + volume levels are preference/session signals."

    /**
     * Summarizes GPU vendor/renderer and extension hash as a WebGL/GLES fingerprint axis.
     *
     * @param renderer GL renderer string.
     * @param vendor GL vendor string.
     * @param socModel SoC/board model, or `"n/a"`.
     * @param extHash Hash of GL extensions.
     * @return Human-readable GPU profile.
     */
    fun gpuProfile(renderer: String, vendor: String, socModel: String, extHash: String): String =
        "GPU $vendor / $renderer" +
            if (socModel.isNotBlank() && socModel != "n/a") " on $socModel" else "" +
            ". Extension hash $extHash — classic WebGL/GLES fingerprint axis."

    /**
     * Summarizes SIM/carrier/radio metadata as a fingerprint that survives IMEI restrictions.
     *
     * @param simState SIM state string.
     * @param simCountry SIM country code, or `"n/a"`.
     * @param operator Carrier/operator name.
     * @param networkType Radio access technology.
     * @param modemCount Number of modems.
     * @return Human-readable telephony profile.
     */
    fun telephonyProfile(
        simState: String,
        simCountry: String,
        operator: String,
        networkType: String,
        modemCount: Int,
    ): String = buildString {
        append("SIM $simState")
        if (simCountry.isNotBlank() && simCountry != "n/a") append(", country $simCountry")
        append(". Carrier: ${operator.ifBlank { "n/a" }}. ")
        append("Radio: $networkType. ")
        if (modemCount > 0) append("$modemCount modem(s). ")
        append("IMEI blocked since Android 10, but carrier + RAT metadata still fingerprint without location permission.")
    }

    /**
     * Describes HDR display capability.
     *
     * @param supported Whether HDR modes are reported.
     * @param types Supported HDR type labels.
     * @return Human-readable HDR profile.
     */
    fun hdrProfile(supported: Boolean, types: List<String>): String = when {
        !supported -> "No HDR modes reported — panel capability signal."
        else -> "HDR supported (${types.joinToString()}). Display pipeline fingerprint beyond resolution."
    }

    /**
     * Describes extended form-factor feature flags (TV, watch, automotive, PC mode).
     *
     * @param features Map of form-factor feature label to presence.
     * @return Human-readable form-factor description.
     */
    fun formFactorFeatures(features: Map<String, Boolean>): String {
        val present = features.filterValues { it }.keys
        return if (present.isEmpty()) {
            "No extended form-factor flags (phone/tablet class)."
        } else {
            "Reports: ${present.joinToString()}. Distinguishes TV, watch, automotive, PC mode."
        }
    }

    /**
     * Explains the readability status of `Build.getSerial()`.
     *
     * @param raw Serial status token (`blocked...`, `permission_denied`, `unavailable`, or value).
     * @return Human-readable serial-access explanation.
     */
    fun buildSerialStatus(raw: String): String = when {
        raw.startsWith("blocked") -> "Blocked since Android 10 — Google removed serial access for third-party apps."
        raw == "permission_denied" -> "Serial API exists but READ_PHONE_STATE denied."
        raw.contains("unavailable") -> raw
        else -> "Serial readable on this API level (rare on modern devices) — permanent hardware identifier."
    }

    /** Sentinel tokens indicating a WebView probe failed, used to short-circuit interpretations. */
    private val webProbeFailed = setOf("timeout", "parse_error", "unknown", "")

    /**
     * Interprets the WebView user-agent string.
     *
     * @param ua WebView default user agent.
     * @return Description highlighting cross-site fingerprinting relevance.
     */
    fun webViewUserAgent(ua: String): String {
        if (ua.isBlank()) return "WebView user agent unavailable."
        val chromeVer = Regex("Chrome/([\\d.]+)").find(ua)?.groupValues?.getOrNull(1)
        return buildString {
            append("Chromium/WebView stack")
            chromeVer?.let { append(" (Chrome $it)") }
            append(". Sent on every HTTP request — anchors cross-site browser fingerprinting.")
        }
    }

    /**
     * Interprets `navigator.platform` from the WebView probe.
     *
     * @param raw Reported platform string, or a failure sentinel.
     * @return Human-readable platform description.
     */
    fun webViewPlatform(raw: String): String = when {
        raw in webProbeFailed -> "Navigator.platform not readable (WebView probe failed or blocked)."
        raw.startsWith("Linux") -> "JS platform \"$raw\" — typical Android WebView Linux ARM masquerade."
        else -> "JS platform \"$raw\" — complements user agent in browser fingerprint scripts."
    }

    /**
     * Interprets `navigator.hardwareConcurrency` (logical CPU cores).
     *
     * @param raw Reported core count as a string.
     * @return Human-readable CPU-core description.
     */
    fun hardwareConcurrency(raw: String): String {
        val n = raw.toIntOrNull() ?: return "CPU core count unavailable."
        return when {
            n <= 0 -> "navigator.hardwareConcurrency returned 0 (hidden or unsupported)."
            n == 1 -> "Browser reports 1 logical CPU core."
            else -> "Browser reports $n logical CPU cores — matches device SoC core layout."
        }
    }

    /**
     * Interprets `navigator.deviceMemory` (RAM tier in GB).
     *
     * @param raw Reported memory in GB as a string.
     * @return Human-readable RAM-tier description.
     */
    fun deviceMemoryGb(raw: String): String {
        val gb = raw.toDoubleOrNull() ?: return "navigator.deviceMemory unavailable."
        return when {
            gb <= 0.0 -> "deviceMemory API not exposed (0) — Chrome may hide RAM tier."
            gb < 4.0 -> "Browser reports ${gb}GB RAM tier — low/mid memory class."
            gb < 8.0 -> "Browser reports ${gb}GB RAM tier — mid-range cohort."
            else -> "Browser reports ${gb}GB RAM tier — high-RAM device class."
        }
    }

    /**
     * Interprets the canvas 2D render hash.
     *
     * @param hash Canvas fingerprint hash, or a failure sentinel.
     * @return Human-readable canvas-fingerprint description.
     */
    fun canvasFingerprint(hash: String): String = when {
        hash in webProbeFailed -> "Canvas fingerprint probe failed — WebView timeout or JS error."
        hash.length >= 8 -> "Canvas 2D render hash ${truncateHash(hash)} — unique per GPU driver + font stack."
        else -> "Canvas hash unexpectedly short."
    }

    /**
     * Interprets the unmasked WebGL vendor string.
     *
     * @param raw WebGL vendor, `"none"`, or a failure sentinel.
     * @return Human-readable WebGL-vendor description.
     */
    fun webglVendor(raw: String): String = when {
        raw in webProbeFailed || raw == "none" -> "WebGL vendor not readable (no WebGL or probe failed)."
        else -> "Unmasked WebGL vendor: $raw (WEBGL_debug_renderer_info)."
    }

    /**
     * Interprets the unmasked WebGL renderer string.
     *
     * @param raw WebGL renderer, `"none"`, or a failure sentinel.
     * @return Human-readable WebGL-renderer description.
     */
    fun webglRenderer(raw: String): String = when {
        raw in webProbeFailed || raw == "none" -> "WebGL renderer not readable (no WebGL or probe failed)."
        else -> "Unmasked WebGL renderer: $raw — highly identifying GPU string."
    }

    /**
     * Interprets the combined WebGL vendor|renderer hash.
     *
     * @param hash SHA-256 of vendor|renderer, or a failure sentinel.
     * @return Human-readable compact GPU-fingerprint description.
     */
    fun webglHash(hash: String): String = when {
        hash in webProbeFailed -> "WebGL hash unavailable."
        hash.length >= 8 -> "SHA-256 of vendor|renderer: ${truncateHash(hash)} — compact GPU fingerprint."
        else -> "WebGL hash unexpectedly short."
    }

    /**
     * Interprets the JavaScript-reported timezone.
     *
     * @param tz Intl timezone id, or a failure sentinel.
     * @return Human-readable timezone description.
     */
    fun jsTimezone(tz: String): String = when {
        tz in webProbeFailed -> "JS timezone unavailable."
        else -> "JavaScript timezone $tz (Intl API) — may match system TZ or reveal browser quirks."
    }

    /**
     * Interprets the ordered `navigator.languages` list.
     *
     * @param raw Comma-separated language tags, or a failure sentinel.
     * @return Human-readable Accept-Language fingerprint description.
     */
    fun navigatorLanguages(raw: String): String {
        if (raw in webProbeFailed) return "Navigator language list unavailable."
        val langs = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            langs.isEmpty() -> "No languages reported."
            langs.size == 1 -> "Primary browser language: ${langs.first()}."
            else -> "Browser languages: ${langs.joinToString()} — ordered Accept-Language fingerprint."
        }
    }

    /**
     * Interprets the JavaScript-reported screen size.
     *
     * @param raw `WxH` string, or a failure sentinel.
     * @return Human-readable screen-size description.
     */
    fun jsScreenSize(raw: String): String = when {
        raw in webProbeFailed -> "JS screen dimensions unavailable."
        'x' in raw -> "JS screen.size $raw — may differ from native DisplayMetrics (chrome UI, multi-window)."
        else -> "Screen size: $raw"
    }

    /**
     * Interprets `screen.colorDepth`.
     *
     * @param bits Color depth in bits as a string.
     * @return Human-readable color-depth description.
     */
    fun colorDepth(bits: String): String {
        val d = bits.toIntOrNull()
        return when (d) {
            null, 0 -> "screen.colorDepth unavailable."
            24 -> "24-bit color depth — standard mobile panel."
            32 -> "32-bit color depth — typical RGBA framebuffer."
            else -> "$d-bit color depth — display pipeline fingerprint."
        }
    }

    /**
     * Interprets `window.devicePixelRatio`.
     *
     * @param raw Pixel ratio as a string.
     * @return Human-readable pixel-ratio description.
     */
    fun devicePixelRatio(raw: String): String {
        val r = raw.toDoubleOrNull() ?: return "devicePixelRatio unavailable."
        return when {
            r <= 0.0 -> "devicePixelRatio not reported."
            r == 1.0 -> "CSS pixel ratio 1.0 — ldpi or desktop layout mode."
            r in 2.0..3.5 -> "CSS pixel ratio $r — typical phone/tablet density."
            else -> "CSS pixel ratio $r — links CSS pixels to physical DPI."
        }
    }

    /**
     * Interprets `navigator.maxTouchPoints`.
     *
     * @param raw Max touch points as a string.
     * @return Human-readable multi-touch capability description.
     */
    fun maxTouchPoints(raw: String): String {
        val n = raw.toIntOrNull() ?: return "maxTouchPoints unavailable."
        return when (n) {
            0 -> "0 touch points — desktop UA mode or no touchscreen."
            1 -> "Single touch point — typical phone class."
            in 2..5 -> "$n concurrent touch points — multi-touch capability signal."
            else -> "$n max touch points reported."
        }
    }

    /**
     * Interprets the OfflineAudioContext render hash.
     *
     * @param hash Audio fingerprint hash, blank, or a failure sentinel.
     * @return Human-readable audio-fingerprint description.
     */
    fun audioContextFingerprint(hash: String): String = when {
        hash.isBlank() || hash in webProbeFailed ->
            "OfflineAudioContext fingerprint unavailable (API missing or probe failed)."
        hash.length >= 8 ->
            "Audio render hash ${truncateHash(hash)} — oscillator+compressor sum; stable per audio stack."
        else -> "Audio fingerprint hash unexpectedly short."
    }
}
