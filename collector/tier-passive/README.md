# Tier 1 — Passive collectors

One class per signal category. All implement `SignalCollector` from `:collector:contract`.

| File / class | SignalCategory |
|--------------|----------------|
| `DeviceIdentityCollector` | DeviceIdentity |
| `GoogleAccountCollector` | GoogleAccount |
| `SystemInfoCollector` | SystemInfo |
| `DisplayCollector` | Display |
| `LocaleCollector` | Locale |
| `AccessibilityCollector` | Accessibility |
| `DeviceMotionCollector` | DeviceMotion (live) |
| `BatteryCollector` | Battery (live) |
| `StorageCollector` | Storage |
| `NetworkCollector` | Network |
| `FontsCollector` | Fonts |
| `TtsVoicesCollector` | InstalledVoices |
| `AppInfoCollector` | AppInfo |
| `ClipboardCollector` | Pasteboard |
| `AudioRouteCollector` | Audio (live) |
| `GpuCollector` | Graphics |
| `TelephonyCollector` | Telephony |

Depends only on `:collector:engine` (never on other tiers).
