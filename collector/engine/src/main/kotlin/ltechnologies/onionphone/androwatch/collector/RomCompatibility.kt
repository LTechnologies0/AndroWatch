package ltechnologies.onionphone.androwatch.collector

import android.os.Build

/**
 * Result of ROM detection: the identified family plus integrity/spoofing hints.
 *
 * @property family Detected ROM family (e.g. `GrapheneOS`, `LineageOS`, `Stock OEM`).
 * @property variant ROM version/variant string, if determinable; otherwise `null`.
 * @property confidence Heuristic confidence: `high`, `medium`, or `low`.
 * @property buildIntegrity Summary of build type/signing keys (e.g. release/user).
 * @property spoofingHints Human-readable hints suggesting property/fingerprint spoofing.
 * @property propertyHints Selected `ro.*` system properties (sensitive ones hashed).
 */
data class RomProfile(
    val family: String,
    val variant: String?,
    val confidence: String,
    val buildIntegrity: String,
    val spoofingHints: List<String>,
    val propertyHints: Map<String, String>,
)

/**
 * Heuristic custom-ROM detection and cross-ROM safe property reads.
 * Works on stock OEM, LineageOS, GrapheneOS, /e/OS, crDroid, PixelExperience, MIUI, etc.
 *
 * Reads hidden `android.os.SystemProperties` via reflection (no public API) and combines
 * `ro.*` properties with [Build] fields to classify the ROM and flag likely spoofing.
 *
 * Privacy: build/ROM properties are OS-fingerprinting signals; sensitive values such as the
 * vendor fingerprint are hashed. No runtime permission is required.
 */
object RomCompatibility {

    /** Lazily-resolved reflective accessor for hidden `SystemProperties.get(key, default)`. */
    private val systemPropertyReader: ((String, String) -> String)? by lazy {
        runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val reader: (String, String) -> String = { key, default ->
                (method.invoke(null, key, default) as? String).orEmpty().ifBlank { default }
            }
            reader
        }.getOrNull()
    }

    /**
     * Safely reads a system property via reflection, returning [default] when unavailable.
     *
     * @param key `ro.*`/system property key.
     * @param default Value returned if the property is missing or reflection failed.
     * @return The property value, or [default].
     */
    fun readProperty(key: String, default: String = ""): String =
        systemPropertyReader?.invoke(key, default) ?: default

    /**
     * Classifies the running ROM and collects integrity/spoofing signals.
     *
     * Inspects vendor-specific `ro.*` version properties and [Build] fields (fingerprint,
     * tags, type, model) to determine the ROM [RomProfile.family] and confidence, and flags
     * conditions such as test-keys, non-release build types, model mismatches and emulator
     * fingerprints.
     *
     * @return A populated [RomProfile] describing the detected ROM.
     */
    fun detectRomProfile(): RomProfile {
        val fingerprint = (Build.FINGERPRINT ?: "").lowercase()
        val display = (Build.DISPLAY ?: "").lowercase()
        val tags = (Build.TAGS ?: "").lowercase()
        val flavor = readProperty("ro.build.flavor").lowercase()
        val lineageDevice = readProperty("ro.lineage.device")
        val lineageVersion = readProperty("ro.lineage.version")
        val lineageBuild = readProperty("ro.lineage.build.version")
        val eosVersion = readProperty("ro.eos.version")
        val eosBuild = readProperty("ro.eos.build.version")
        val crdroid = readProperty("ro.crdroid.version")
        val graphene = readProperty("ro.grapheneos.version")
        val calyx = readProperty("ro.calyxos.version")
        val pixelExp = readProperty("ro.aospa.version")
        val evolution = readProperty("ro.evolution.version")
        val miui = readProperty("ro.miui.ui.version.name")
        val oneui = readProperty("ro.build.version.oneui")
        val oxygen = readProperty("ro.oxygen.version")
        val coloros = readProperty("ro.build.version.oplusrom")
        val modversion = readProperty("ro.modversion")

        val spoofingHints = mutableListOf<String>()
        val propertyHints = buildMap {
            if (lineageVersion.isNotBlank()) put("ro.lineage.version", lineageVersion)
            if (lineageDevice.isNotBlank()) put("ro.lineage.device", lineageDevice)
            if (lineageBuild.isNotBlank()) put("ro.lineage.build.version", lineageBuild)
            if (eosVersion.isNotBlank()) put("ro.eos.version", eosVersion)
            if (eosBuild.isNotBlank()) put("ro.eos.build.version", eosBuild)
            if (crdroid.isNotBlank()) put("ro.crdroid.version", crdroid)
            if (graphene.isNotBlank()) put("ro.grapheneos.version", graphene)
            if (calyx.isNotBlank()) put("ro.calyxos.version", calyx)
            if (pixelExp.isNotBlank()) put("ro.aospa.version", pixelExp)
            if (evolution.isNotBlank()) put("ro.evolution.version", evolution)
            if (miui.isNotBlank()) put("ro.miui.ui.version.name", miui)
            if (oneui.isNotBlank()) put("ro.build.version.oneui", oneui)
            if (oxygen.isNotBlank()) put("ro.oxygen.version", oxygen)
            if (coloros.isNotBlank()) put("ro.build.version.oplusrom", coloros)
            if (modversion.isNotBlank()) put("ro.modversion", modversion)
            val refFp = readProperty("ro.build.reference.fingerprint")
            if (refFp.isNotBlank()) put("ro.build.reference.fingerprint", refFp)
            readProperty("ro.build.flavor").takeIf { it.isNotBlank() }?.let { put("ro.build.flavor", it) }
            readProperty("ro.build.description").takeIf { it.isNotBlank() }?.let { put("ro.build.description", it) }
            readProperty("ro.bootloader").takeIf { it.isNotBlank() }?.let { put("ro.bootloader", it) }
            readProperty("ro.hardware").takeIf { it.isNotBlank() }?.let { put("ro.hardware", it) }
            readProperty("ro.board.platform").takeIf { it.isNotBlank() }?.let { put("ro.board.platform", it) }
            readProperty("ro.product.cpu.abilist").takeIf { it.isNotBlank() }?.let { put("ro.product.cpu.abilist", it) }
            readProperty("ro.vendor.build.fingerprint").takeIf { it.isNotBlank() }?.let {
                put("ro.vendor.build.fingerprint", sha256Hex(it))
            }
            readProperty("ro.debuggable").takeIf { it.isNotBlank() }?.let { put("ro.debuggable", it) }
            readProperty("ro.secure").takeIf { it.isNotBlank() }?.let { put("ro.secure", it) }
            readProperty("ro.adb.secure").takeIf { it.isNotBlank() }?.let { put("ro.adb.secure", it) }
            readProperty("ro.build.user").takeIf { it.isNotBlank() }?.let { put("ro.build.user", it) }
            readProperty("ro.build.host").takeIf { it.isNotBlank() }?.let { put("ro.build.host", it) }
        }

        val (family, variant, confidence) = when {
            graphene.isNotBlank() || fingerprint.contains("graphene") ->
                Triple("GrapheneOS", graphene.ifBlank { null }, "high")
            calyx.isNotBlank() || fingerprint.contains("calyx") ->
                Triple("CalyxOS", calyx.ifBlank { null }, "high")
            eosVersion.isNotBlank() || eosBuild.isNotBlank() || fingerprint.contains("/e/") ->
                Triple("/e/OS", eosVersion.ifBlank { eosBuild }.ifBlank { null }, "high")
            lineageVersion.isNotBlank() || lineageDevice.isNotBlank() ||
                fingerprint.contains("lineage") || flavor.contains("lineage") ->
                Triple("LineageOS", lineageVersion.ifBlank { lineageDevice }.ifBlank { null }, "high")
            crdroid.isNotBlank() || fingerprint.contains("crdroid") ->
                Triple("crDroid", crdroid.ifBlank { null }, "high")
            pixelExp.isNotBlank() || fingerprint.contains("aospa") ->
                Triple("PixelExperience", pixelExp.ifBlank { null }, "high")
            evolution.isNotBlank() || fingerprint.contains("evolution") ->
                Triple("Evolution X", evolution.ifBlank { null }, "medium")
            miui.isNotBlank() || fingerprint.contains("miui") ->
                Triple("MIUI/HyperOS", miui.ifBlank { null }, "high")
            oneui.isNotBlank() || fingerprint.contains("oneui") ->
                Triple("One UI", oneui.ifBlank { null }, "high")
            oxygen.isNotBlank() || fingerprint.contains("oxygen") ->
                Triple("OxygenOS", oxygen.ifBlank { null }, "high")
            coloros.isNotBlank() || fingerprint.contains("coloros") ->
                Triple("ColorOS", coloros.ifBlank { null }, "medium")
            fingerprint.contains("aosp") && Build.MANUFACTURER.equals("Google", ignoreCase = true) ->
                Triple("AOSP / Pixel stock", Build.VERSION.INCREMENTAL, "medium")
            tags.contains("test-keys") || Build.TYPE != "user" ->
                Triple("Custom / engineering build", Build.TYPE, "medium")
            else -> Triple("Stock OEM", Build.MANUFACTURER ?: "unknown", "low")
        }

        if (propertyHints.containsKey("ro.build.reference.fingerprint")) {
            spoofingHints += "Reference fingerprint present — Lineage PixelProps-style spoofing may be active for GMS."
        }
        if (tags.contains("test-keys")) {
            spoofingHints += "Build signed with test-keys (typical of unofficial/community ROMs)."
        }
        if (Build.TYPE == "userdebug" || Build.TYPE == "eng") {
            spoofingHints += "Non-release build type (${Build.TYPE}) — developer or custom ROM channel."
        }
        if ((Build.MODEL ?: "") != readProperty("ro.product.model") && readProperty("ro.product.model").isNotBlank()) {
            spoofingHints += "Model property differs from Build.MODEL — possible property spoofing layer."
        }
        if (fingerprint.contains("generic") || display.contains("sdk_")) {
            spoofingHints += "Emulator or generic AOSP image fingerprint detected."
        }

        val buildIntegrity = when {
            tags.contains("release-keys") && Build.TYPE == "user" -> "release/user (Play-integrity friendly)"
            tags.contains("test-keys") -> "test-keys (custom ROM / dev build)"
            Build.TYPE != "user" -> "${Build.TYPE}/${Build.TAGS}"
            else -> "${Build.TYPE}/${Build.TAGS}"
        }

        return RomProfile(
            family = family,
            variant = variant,
            confidence = confidence,
            buildIntegrity = buildIntegrity,
            spoofingHints = spoofingHints,
            propertyHints = propertyHints,
        )
    }

    /**
     * Reads `ro.build.reference.fingerprint`, used by PixelProps-style spoofing layers.
     *
     * @return The reference fingerprint, or an empty string if absent.
     */
    fun referenceFingerprint(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readProperty("ro.build.reference.fingerprint")
        } else {
            readProperty("ro.build.reference.fingerprint")
        }
}
