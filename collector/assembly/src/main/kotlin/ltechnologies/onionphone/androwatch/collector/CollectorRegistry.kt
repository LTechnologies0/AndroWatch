package ltechnologies.onionphone.androwatch.collector

import ltechnologies.onionphone.androwatch.collector.advanced.InstalledAppsProbeCollector
import ltechnologies.onionphone.androwatch.collector.advanced.PreviousInstallsLogCollector
import ltechnologies.onionphone.androwatch.collector.advanced.WebViewFingerprintCollector
import ltechnologies.onionphone.androwatch.collector.passive.AccessibilityCollector
import ltechnologies.onionphone.androwatch.collector.passive.AppInfoCollector
import ltechnologies.onionphone.androwatch.collector.passive.AudioRouteCollector
import ltechnologies.onionphone.androwatch.collector.passive.BatteryCollector
import ltechnologies.onionphone.androwatch.collector.passive.ClipboardCollector
import ltechnologies.onionphone.androwatch.collector.passive.DeviceIdentityCollector
import ltechnologies.onionphone.androwatch.collector.passive.DeviceMotionCollector
import ltechnologies.onionphone.androwatch.collector.passive.DisplayCollector
import ltechnologies.onionphone.androwatch.collector.passive.FontsCollector
import ltechnologies.onionphone.androwatch.collector.passive.GoogleAccountCollector
import ltechnologies.onionphone.androwatch.collector.passive.GpuCollector
import ltechnologies.onionphone.androwatch.collector.passive.LocaleCollector
import ltechnologies.onionphone.androwatch.collector.passive.NetworkCollector
import ltechnologies.onionphone.androwatch.collector.passive.StorageCollector
import ltechnologies.onionphone.androwatch.collector.passive.SystemInfoCollector
import ltechnologies.onionphone.androwatch.collector.passive.TelephonyCollector
import ltechnologies.onionphone.androwatch.collector.passive.TtsVoicesCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.BluetoothCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.CalendarCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.CamerasCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.ContactsCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.LocalNetworkCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.LocationCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.MotionCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.MusicLibraryCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.PhotosCollector
import ltechnologies.onionphone.androwatch.collector.permissioned.RemindersCollector
import ltechnologies.onionphone.androwatch.collector.LiveSignalCollector
import ltechnologies.onionphone.androwatch.model.SignalCategory

/**
 * Central assembly point wiring every concrete [SignalCollector] into a single registry.
 *
 * This is the only module that depends on all collector tiers (passive, permissioned,
 * advanced); the rest of the app looks up collectors here by [SignalCategory]. Adding a
 * new collector requires appending its constructor to [all].
 *
 * @see SignalCollector
 * @see LiveSignalCollector
 */
object CollectorRegistry {
    /** Every registered collector instance, one per supported [SignalCategory]. */
    val all: List<SignalCollector> = listOf(
        DeviceIdentityCollector(),
        GoogleAccountCollector(),
        SystemInfoCollector(),
        DisplayCollector(),
        LocaleCollector(),
        AccessibilityCollector(),
        DeviceMotionCollector(),
        BatteryCollector(),
        StorageCollector(),
        NetworkCollector(),
        FontsCollector(),
        TtsVoicesCollector(),
        AppInfoCollector(),
        ClipboardCollector(),
        AudioRouteCollector(),
        GpuCollector(),
        TelephonyCollector(),
        MotionCollector(),
        LocationCollector(),
        CamerasCollector(),
        BluetoothCollector(),
        LocalNetworkCollector(),
        ContactsCollector(),
        PhotosCollector(),
        CalendarCollector(),
        RemindersCollector(),
        MusicLibraryCollector(),
        InstalledAppsProbeCollector(),
        WebViewFingerprintCollector(),
        PreviousInstallsLogCollector(),
    )

    /** Fast lookup of a collector by its [SignalCategory]. */
    val byCategory: Map<SignalCategory, SignalCollector> = all.associateBy { it.category }

    /** Categories whose collector supports live streaming via [LiveSignalCollector.liveFlow]. */
    val liveCategories: Set<SignalCategory> = all
        .filterIsInstance<LiveSignalCollector>()
        .map { it.category }
        .toSet()
}
