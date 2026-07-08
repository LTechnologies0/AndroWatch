# Passive Fingerprint TODO — 186 methods

**Status: implemented (2026-06)** — all items below wired via `PassiveCollectorExtras.kt` + engine probes.

Checklist of passive (no runtime dangerous permission) fingerprint signals for AndroWatch.
**Legend:** `[x]` implemented · `[ ]` pending · **Category** = `SignalCategory` target

Scope: normal/install-time permissions OK (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `READ_BASIC_PHONE_STATE`).

---

## Phase 1 — Depth gaps in existing collectors

### DeviceIdentity (#1–16)
- [x] 1 `Build.ID` → `buildId`
- [x] 2 `Build.DISPLAY` → `buildDisplay`
- [x] 3 `Build.TAGS` → `buildTags`
- [x] 4 `Build.TYPE` → `buildType`
- [x] 5 `Build.HOST` → `buildHost`
- [x] 6 `Build.USER` → `buildUser`
- [x] 7 `Build.TIME` → `buildTime`
- [x] 8 `Build.BOOTLOADER` → `bootloader`
- [x] 9 `Build.getRadioVersion()` → `radioVersion`
- [x] 10 `Build.SKU` / `Build.ODM_SKU` → `sku` / `odmSku`
- [x] 11 `Build.VERSION.CODENAME` → `codename`
- [x] 12 `Build.VERSION.BASE_OS` → `baseOs`
- [x] 13 `Build.VERSION.PREVIEW_SDK_INT` → `previewSdk`
- [x] 14 `Build.IS_EMULATOR` / `IS_DEBUGGABLE` / `IS_USER` → `buildFlags`
- [x] 15 `Settings.Secure.ENABLED_INPUT_METHODS` hash → `enabledImeHash`
- [x] 16 `Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE` → `imeSubtype`

### SystemInfo (#17–32)
- [x] 17 `Settings.Global.BOOT_COUNT` → `bootCount`
- [x] 18 `Settings.Global.AIRPLANE_MODE_ON` → `airplaneMode`
- [x] 19 `Settings.Global.AUTO_TIME` / `AUTO_TIME_ZONE` → `autoTime`
- [x] 20 `Settings.Global.DEVICE_PROVISIONED` → `provisioned`
- [x] 21 `Settings.Global.WIFI_ON` → `wifiOn`
- [x] 22 `Settings.Global.MOBILE_DATA` → `mobileData`
- [x] 23 `Settings.Global.HTTP_PROXY` host hash → `httpProxy`
- [x] 24 `Settings.Global.ZEN_MODE` → `zenMode`
- [x] 25 `Settings.Global.PRIVATE_DNS_SPECIFIER` hash → `privateDnsHost`
- [x] 26 `Settings.Global.NETWORK_RECOMMENDATIONS_ENABLED` → `networkRecommendations`
- [x] 27 `Settings.Global.STAY_ON_WHILE_PLUGGED_IN` → `stayAwakePlugged`
- [x] 28 `Settings.Secure.NAVIGATION_MODE` → `navigationMode`
- [x] 29 `Settings.Secure.NIGHT_DISPLAY_*` → `nightDisplay`
- [x] 30 `Settings.Secure.AUTOFILL_SERVICE` → `autofillService`
- [x] 31 `Settings.Secure.DEFAULT_VOICE_INPUT_METHOD` → `voiceIme`
- [x] 32 `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` hash → `notificationListenersHash`

### Accessibility (#33–37)
- [x] 33 `ENABLED_ACCESSIBILITY_SERVICES` hash → `a11yServicesHash`
- [x] 34 `ACCESSIBILITY_DISPLAY_DALTONIZER` mode → `daltonizerMode`
- [x] 35 `ACCESSIBILITY_CAPTIONING_*` → `captioningProfile`
- [x] 36 `ACCESSIBILITY_SPEAK_PASSWORD` → `speakPassword`
- [x] 37 `ONE_HANDED_MODE_ACTIVATED` → `oneHandedMode`

### Display (#38–47)
- [x] 38 `Display.Cutout` geometry hash → `cutoutGeometry`
- [x] 39 `Display.HdrCapabilities` → `hdrTypes`
- [x] 40 `Display.isHdr()` / `getColorMode()` → `hdrActive` / `colorMode`
- [x] 41 `isWideColorGamut()` + color space → `wideColorGamut`
- [x] 42 `DisplayMetrics.xdpi` / `ydpi` → `physicalDpi`
- [x] 43 `Configuration.mcc` / `mnc` → `simMccMncConfig`
- [x] 44 `touchscreen` / `keyboard` / `navigation` → `inputConfig`
- [x] 45 `screenLayout` buckets → `screenLayout`
- [x] 46 `Display.getFlags()` → `displayFlags`
- [x] 47 `UiModeManager.getNightMode()` → `uiNightMode`

### DeviceMotion (#48–55)
- [x] 48 `boundedSensorList` hash → `sensorInventoryHash`
- [x] 49 `Sensor.getMaximumRange()` → `sensorRanges`
- [x] 50 `getMinDelay()` / `getMaxDelay()` → `sensorDelays`
- [x] 51 `getPower()` → `sensorPower`
- [x] 52 `getFifoMaxEventCount()` → `sensorFifo`
- [x] 53 `isWakeUpSensor()` / `getReportingMode()` → `sensorWakeReport`
- [x] 54 `getDynamicSensorList` count → `dynamicSensorCount`
- [x] 55 Extended sensor presence → `sensorPresenceExtended`

### Battery (#56–61)
- [x] 56 `BATTERY_PROPERTY_CHARGE_COUNTER` → `chargeCounter`
- [x] 57 `CURRENT_NOW` / `CURRENT_AVERAGE` → `currentDraw`
- [x] 58 `ENERGY_COUNTER` → `energyCounter`
- [x] 59 `CYCLE_COUNT` → `cycleCount`
- [x] 60 `EXTRA_TECHNOLOGY` / `EXTRA_PLUGGED` → `batteryTechnology`
- [x] 61 `isBatteryPresent` → `batteryPresent`

### Network (#62–68)
- [x] 62 `getAllNetworks()` count → `networkCount`
- [x] 63 `NetworkCapabilities` hash → `netCapsHash`
- [x] 64 Link bandwidth → `linkBandwidth`
- [x] 65 `LinkProperties.getMtu()` → `mtu`
- [x] 66 `getRoutes()` count → `routeCount`
- [x] 67 Captive portal / not roaming → `captivePortal`
- [x] 68 `getRestrictBackgroundStatus()` → `dataSaverStatus`

### Audio (#69–74)
- [x] 69 `AudioDeviceInfo.getProductName()` hash → `audioProductNamesHash`
- [x] 70 Sample rates / channel counts → `audioHwCaps`
- [x] 71–72 `OUTPUT_SAMPLE_RATE` / `OUTPUT_FRAMES_PER_BUFFER` → `outputSampleRate` / `outputBufferFrames`
- [x] 73 `isMicrophoneMute` / `isVolumeFixed` → `audioMuteFixed`
- [x] 74 `getMode()` / communication device → `audioMode`

### Telephony (#75–82)
- [x] 75 Operator MCCMNC → `operatorMccMnc`
- [x] 76 `getVoiceNetworkType()` → `voiceNetworkType`
- [x] 77 `isDataEnabled()` / `getDataState()` → `dataEnabled`
- [x] 78 `getCallState()` → `callState`
- [x] 79 Voice/SMS/data capable → `telephonyCaps`
- [x] 80 `getSimCarrierId()` → `carrierId`
- [x] 81 Multi-SIM / modem count → `multiSim`
- [x] 82 TAC readability → `tacStatus`

### Graphics (#83–87)
- [x] 83 `reqGlEsVersion` + GL version → `glVersionFull`
- [x] 84 EGL extensions hash → `eglExtensionsHash`
- [x] 85 `MAX_TEXTURE_SIZE` → `glMaxTexture`
- [x] 86 `MAX_VIEWPORT_DIMS` → `glMaxViewport`
- [x] 87 `GL_SHADING_LANGUAGE_VERSION` → `glslVersion`

### InstalledVoices (#88–90)
- [x] 88 `probeTtsVoices()` hash → `voiceInventoryHash`
- [x] 89 Voice features / latency → `voiceFeatures`
- [x] 90 Network-required voices → `voiceNetworkRequired`

---

## Phase 2 — Settings.System & hardware flags (#91–127)

### Settings.System → SystemInfo (#91–99)
- [x] 91–99 Screen brightness, timeout, vibrate, rotation, pointer speed, alarm URI

### SystemFeatures → SystemInfo (#100–115)
- [x] 100–101 System features + shared libs hash
- [x] 102–115 Extended `hasSystemFeature` flags → `featureFlagsExtended`

### WiFi passive → Network (#116–122)
- [x] 116–122 WiFi enabled/state/bands/direct/country

### Bluetooth passive → Network (#123–127)
- [x] 123–127 BT adapter state + LE capabilities

---

## Phase 3 — Runtime, defaults, input, locale (#128–150)

- [x] 128–134 Runtime / ActivityManager memory → SystemInfo
- [x] 135–142 Default apps + WebView/GMS versions → AppInfo
- [x] 143–147 Input devices + IME lists → DeviceIdentity
- [x] 148–150 Locale/time extensions → Locale

---

## Phase 4 — Storage, camera, codec, DRM, SDK, misc (#151–173)

- [x] 151–155 Storage extensions → Storage
- [x] 156–158 Camera inventory → SystemInfo
- [x] 159–161 MediaCodec → Graphics
- [x] 162–163 Extended DRM → DeviceIdentity
- [x] 164–165 SdkExtensions → SystemInfo
- [x] 166–173 Misc hardware → SystemInfo

---

## Phase 5 — ROM properties (#174–183)

- [x] 174–183 `ro.*` SystemProperties expansion → SystemInfo / romFamily

---

## Phase 6 — Vulkan (#184–186)

- [x] 184–186 Vulkan feature flags (+ optional device hash) → Graphics

---

See [ANDROID_API_MAP.md](ANDROID_API_MAP.md) for API permission notes.
