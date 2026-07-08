# Android API Map — Sensitive Data Returns for AndroWatch

Reference mapping of public Android APIs to privacy-sensitive return data, aligned with [Loupe](https://github.com/mysk-research/loupe) tiers and [Android CDD privacy sections](https://source.android.com/docs/compatibility/17/android-17-cdd).

**Legend**
- **None** — no manifest permission
- **Normal** — install-time permission, no runtime prompt
- **Runtime** — dangerous/special permission with user prompt
- **Readable** — `Settings.*` key readable without `READ_SECURE_SETTINGS`

---

## Tier 1: Passive (no runtime prompt)

### Device Identity

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `Settings.Secure.ANDROID_ID` | Readable | 64-bit hex ID scoped per app signing key + user |
| `Build.BRAND/MODEL/DEVICE/PRODUCT/HARDWARE/FINGERPRINT` | None | Hardware and build fingerprint |
| `Build.SUPPORTED_ABIS` | None | CPU ABI list |
| `Build.getSerial()` | Runtime/privileged | Hardware serial (blocked for third-party API 29+) |
| `MediaDrm(PROPERTY_DEVICE_UNIQUE_ID)` | None | Widevine device-unique ID (persistent fingerprint) |
| `Settings.Secure.BLUETOOTH_NAME` | Readable | Device friendly name |
| `Settings.Secure.DEFAULT_INPUT_METHOD` | Readable | Default keyboard package |

### Google Account

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `AccountManager.getAuthenticatorTypes()` | None | Installed account types (`com.google`, Exchange, etc.) |
| `AccountManager.getAccountsByType()` | Runtime `GET_ACCOUNTS` (API 26+: filtered) | Account names + types |

### System Info

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `Build.VERSION.*` | None | SDK, release, security patch |
| `SystemClock.elapsedRealtime()` | None | Boot uptime (session signal) |
| `Settings.Global.DEVICE_NAME` | Readable | User-assigned device name |
| `PackageManager.hasSystemFeature()` | None | Telephony, watch, TV, automotive flags |

### Display

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `DisplayMetrics` | None | Resolution, density, DPI |
| `Configuration` | None | Screen size, orientation, font scale, night mode |
| `Display.getRefreshRate()` / `getMode()` | None | Refresh rate, supported modes |
| `Display.getCutout()` | None | Notch/cutout geometry |
| `UiModeManager.getCurrentModeType()` | None | Car, TV, desk, watch mode |

### Locale & Region

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `Locale.getDefault()` | None | Language, region, script |
| `TimeZone.getDefault()` | None | Timezone ID and UTC offset |
| `Configuration.getLocales()` | None | Full locale list |

### Accessibility

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `AccessibilityManager.isEnabled()` | None | Any accessibility service active |
| `AccessibilityManager.isTouchExplorationEnabled()` | None | TalkBack-like exploration |
| `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` | Readable | Enabled service component list |
| `Settings.Secure.ACCESSIBILITY_DISPLAY_*` | Readable | Color inversion, daltonizer |

### Device Motion (inventory only)

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `SensorManager.getSensorList(TYPE_ALL)` | None | Sensor names, vendors, types, resolution |
| `Sensor.getName/getVendor/getType` | None | Hardware sensor fingerprint |

### Battery & Power

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `BatteryManager.getIntProperty(CAPACITY)` | None | Battery percentage |
| `BatteryManager.isCharging()` | None | Charging state |
| `Intent.ACTION_BATTERY_CHANGED` | None | Level, voltage, temperature, health |
| `PowerManager.isPowerSaveMode()` | None | Battery saver active |
| `PowerManager.getCurrentThermalStatus()` | None | Thermal throttling state |

### Storage

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `StatFs.getTotalBytes/getAvailableBytes` | None | Total/free storage |
| `StorageManager.getStorageVolumes()` | None | Volume count, removable flags |

### Network

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `ConnectivityManager.getActiveNetwork()` | Normal `ACCESS_NETWORK_STATE` | Active network handle |
| `NetworkCapabilities.hasTransport()` | Normal | Wi-Fi, cellular, VPN, Ethernet |
| `LinkProperties.getLinkAddresses()` | Normal | Local IPv4/IPv6 addresses |
| `LinkProperties.getDnsServers()` | Normal | DNS resolver list |
| `NetworkCapabilities.getTransportInfo()` | Normal | Wi-Fi info redacted without location |

### Fonts

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `SystemFonts.getAvailableFonts()` | None (API 29+) | System font file inventory |
| `Typeface.create()` probes | None | OEM font fallback behavior |

### Installed Voices (TTS)

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `TextToSpeech.Engine.getEngines()` | None | Installed TTS engine packages |
| `TextToSpeech.getVoices()` | None | Voice locales and features |

### App & Bundle

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `PackageManager.getPackageInfo(self)` | None | Version, first/last install time |
| `ApplicationInfo.targetSdkVersion` | None | Target SDK |
| `PackageManager.getInstallSourceInfo()` | None | Installer package |

### Pasteboard (Clipboard)

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `ClipboardManager.hasPrimaryClip()` | None | Clipboard non-empty (focus-gated API 29+) |
| `ClipDescription.getMimeType()` | None | Content type metadata |
| `ClipDescription.EXTRA_IS_SENSITIVE` | None | Sensitive-content flag (API 33) |

### Audio

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `AudioManager.getDevices()` | None | Connected device types and product names |
| `AudioManager.getRingerMode()` | None | Silent/vibrate/normal |
| `AudioManager.getStreamVolume()` | None | Volume levels per stream |

### Graphics

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `Build.HARDWARE` | None | GPU platform string |
| `GLES20.glGetString(GL_RENDERER/GL_VENDOR)` | None (needs GL context) | GPU renderer/vendor |

### Telephony

| API | Permission | Sensitive returns |
|-----|------------|-------------------|
| `TelephonyManager.getSimOperatorName()` | None | Carrier name |
| `TelephonyManager.getSimCountryIso()` | None | SIM country |
| `TelephonyManager.getSimState()` | None | SIM presence/state |
| `TelephonyManager.getDataNetworkType()` | Normal `READ_BASIC_PHONE_STATE` | LTE/NR/etc. |
| `TelephonyManager.getImei/getDeviceId()` | Runtime/privileged | IMEI (blocked API 29+) |

---

## Tier 2: Needs Permission (runtime prompt)

| Category | Permissions | APIs | Aggregated signals (no raw PII) |
|----------|-------------|------|--------------------------------|
| Motion | `ACTIVITY_RECOGNITION`, `BODY_SENSORS` | `SensorManager`, step counter | Sensor inventory, step bucket |
| Location | `ACCESS_FINE/COARSE_LOCATION` | `LocationManager.getLastKnownLocation` | Accuracy bucket, provider list, fix age |
| Cameras | `CAMERA` | `CameraManager.getCameraCharacteristics` | Camera count, facing, capabilities |
| Bluetooth | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | `BluetoothAdapter.getBondedDevices` | Bonded count, device class distribution |
| Local Network | `NEARBY_WIFI_DEVICES`, mDNS | `WifiManager` + `NsdManager` | AP count, mDNS service types |
| Contacts | `READ_CONTACTS` | `ContactsContract` queries | Contact count, account types, label distribution |
| Photos | `READ_MEDIA_IMAGES/VIDEO` | `MediaStore` queries | Image/video counts, year distribution |
| Calendar | `READ_CALENDAR` | `CalendarContract` | Calendar/event counts |
| Reminders | `READ_CALENDAR` | `CalendarContract.Reminders` | Reminder count |
| Music | `READ_MEDIA_AUDIO` | `MediaStore.Audio` | Audio file count |

---

## Tier 3: Advanced (side-channels)

| Category | Technique | APIs | Sensitive returns |
|----------|-----------|------|-------------------|
| Installed Apps Probe | Intent scheme resolution | `PackageManager.resolveActivity` + `<queries>` | Which apps handle known URI schemes |
| WebView Fingerprint | Hidden WebView + JS | Canvas hash, WebGL, UA, hardwareConcurrency, screen, colorDepth, pixelRatio, maxTouchPoints | Full browser fingerprint surface |
| Previous Installs Log | Encrypted local persistence | `EncryptedSharedPreferences` + Keystore | First-seen timestamp, launch count |

---

## Implementation coverage (2026-06)

Probes use `addOptional` / `probeOrNull`: a failed API call is **skipped** (logcat `collect/skip`), not a category-wide failure.

| API | Signal | Module |
|-----|--------|--------|
| `AdvertisingIdClient` (GMS, reflection) | `gaidHash`, `gaidLimited` | DeviceIdentity |
| `AccountManager.accounts` | `accountNamesHash` | GoogleAccount |
| `PackageManager.getInstalledPackages` | `visible/system/userPackageCount` | InstalledAppsProbe |
| `Settings.System` ringtone/notification | `ringtoneUri`, `notificationSoundUri` | SystemInfo |
| `WallpaperManager.getWallpaperFile` | `wallpaperHash` | SystemInfo |
| `NfcAdapter` | `nfcEnabled` | SystemInfo |
| `ENABLED_INPUT_METHODS` | `enabledImeCount`, `enabledImeHash` | DeviceIdentity |
| WebView `OfflineAudioContext` | `audioContextHash` | WebViewFingerprint |

### Passive expansion (2026-06)

186 additional passive methods implemented via engine probes (`SettingsProbes`, `FeatureProbes`, `HardwareProbes`, `CollectorProbes`, `DefaultAppsProbes`) and [`PassiveCollectorExtras.kt`](../collector/tier-passive/src/main/kotlin/ltechnologies/onionphone/androwatch/collector/passive/PassiveCollectorExtras.kt). Full checklist: [PASSIVE_FINGERPRINT_TODO.md](PASSIVE_FINGERPRINT_TODO.md).

Highlights by category:

| Area | New signals (sample) |
|------|------------------------|
| DeviceIdentity | `buildId`, `buildTags`, `sku`, `enabledImeHash`, `inputDevicesHash`, ClearKey/PlayReady DRM |
| SystemInfo | `bootCount`, `zenMode`, `systemFeaturesHash`, `cpuCount`, `cameraCount`, `esimEnabled`, ROM `ro.*` props |
| Display | `cutoutGeometry`, `hdrTypes`, `physicalDpi`, `mcc/mnc` |
| DeviceMotion | `sensorInventoryHash`, `sensorRanges`, `dynamicSensorCount` |
| Battery | `chargeCounter`, `currentDraw`, `cycleCount` |
| Network | `networkCount`, `netCapsHash`, Wi‑Fi passive (`wifi5g`, `wifi6g`), BT LE caps |
| Audio | `audioProductNamesHash`, `outputSampleRate`, `audioMode` |
| Telephony | `operatorMccMnc`, `carrierId`, `multiSim` |
| Graphics | `eglExtensionsHash`, `glMaxTexture`, `codecListHash`, Vulkan flags |
| AppInfo | `defaultBrowser`, `webviewPackage`, `gmsVersion` |
| Locale | `dstProfile`, `localeCount`, `defaultScript` |
| InstalledVoices | `voiceInventoryHash` (wired `probeTtsVoices`) |

`addSafe` still emits a per-signal error card when a **required** reading fails; optional probes above never do.

---

| Signal | iOS (Loupe) | Android (AndroWatch) |
|--------|-------------|----------------------|
| Device ID | `identifierForVendor` | `ANDROID_ID` (scoped) + `MediaDrm` ID |
| Installed apps | `canOpenURL` (~50 schemes) | `<queries>` + `resolveActivity` (API 30+ limited) |
| Keychain persistence | Survives reinstall | `EncryptedSharedPreferences` (same signature only) |
| Local network | Bonjour prompt | `NEARBY_WIFI_DEVICES` + mDNS (API 37+: `ACCESS_LOCAL_NETWORK`) |
| Apple account | iCloud signals | Google authenticator types only (no email without grant) |

---

## Custom ROM compatibility (`RomCompatibility.kt`)

Heuristic detection via `Build.*` + reflected `SystemProperties` (safe fallback if blocked).

| ROM family | Property keys probed |
|------------|---------------------|
| LineageOS | `ro.lineage.version`, `ro.lineage.device`, `ro.build.reference.fingerprint` |
| GrapheneOS | `ro.grapheneos.version` |
| CalyxOS | `ro.calyxos.version` |
| /e/OS | `ro.eos.version`, `ro.eos.build.version` |
| crDroid | `ro.crdroid.version` |
| PixelExperience | `ro.aospa.version` |
| MIUI/HyperOS | `ro.miui.ui.version.name` |
| One UI | `ro.build.version.oneui` |
| OxygenOS | `ro.oxygen.version` |
| ColorOS | `ro.build.version.oplusrom` |

Spoofing signals: `test-keys`, `userdebug`/`eng`, `ro.build.reference.fingerprint`, model property mismatch.

---

## References

- [Device identifiers (AOSP)](https://source.android.com/docs/core/connect/device-identifiers)
- [Package visibility (API 30+)](https://developer.android.com/training/package-visibility)
- [Settings.Secure](https://developer.android.com/reference/android/provider/Settings.Secure)
- [Android 17 CDD — Privacy (9.8)](https://source.android.com/docs/compatibility/17/android-17-cdd)
