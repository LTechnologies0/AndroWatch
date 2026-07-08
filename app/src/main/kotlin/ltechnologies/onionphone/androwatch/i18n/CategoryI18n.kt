package ltechnologies.onionphone.androwatch.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ltechnologies.onionphone.androwatch.R
import ltechnologies.onionphone.androwatch.model.SignalCategory

/**
 * Maps each [SignalCategory] to its localized title/subtitle string resources.
 *
 * Centralizing the enum→resource mapping keeps category display strings translatable and
 * decoupled from collector code.
 */
object CategoryStrings {
    /**
     * Returns the title string resource for [category].
     *
     * @param category Category to look up.
     * @return `@StringRes` id of the localized title.
     */
    @StringRes
    fun titleRes(category: SignalCategory): Int = when (category) {
        SignalCategory.DeviceIdentity -> R.string.cat_device_identity_title
        SignalCategory.GoogleAccount -> R.string.cat_google_account_title
        SignalCategory.SystemInfo -> R.string.cat_system_info_title
        SignalCategory.Display -> R.string.cat_display_title
        SignalCategory.Locale -> R.string.cat_locale_title
        SignalCategory.Accessibility -> R.string.cat_accessibility_title
        SignalCategory.DeviceMotion -> R.string.cat_device_motion_title
        SignalCategory.Battery -> R.string.cat_battery_title
        SignalCategory.Storage -> R.string.cat_storage_title
        SignalCategory.Network -> R.string.cat_network_title
        SignalCategory.Fonts -> R.string.cat_fonts_title
        SignalCategory.InstalledVoices -> R.string.cat_installed_voices_title
        SignalCategory.AppInfo -> R.string.cat_app_info_title
        SignalCategory.Pasteboard -> R.string.cat_pasteboard_title
        SignalCategory.Audio -> R.string.cat_audio_title
        SignalCategory.Graphics -> R.string.cat_graphics_title
        SignalCategory.Telephony -> R.string.cat_telephony_title
        SignalCategory.Motion -> R.string.cat_motion_title
        SignalCategory.Location -> R.string.cat_location_title
        SignalCategory.Cameras -> R.string.cat_cameras_title
        SignalCategory.Bluetooth -> R.string.cat_bluetooth_title
        SignalCategory.LocalNetwork -> R.string.cat_local_network_title
        SignalCategory.Contacts -> R.string.cat_contacts_title
        SignalCategory.Photos -> R.string.cat_photos_title
        SignalCategory.Calendar -> R.string.cat_calendar_title
        SignalCategory.Reminders -> R.string.cat_reminders_title
        SignalCategory.MusicLibrary -> R.string.cat_music_library_title
        SignalCategory.InstalledAppsProbe -> R.string.cat_installed_apps_probe_title
        SignalCategory.WebViewFingerprint -> R.string.cat_webview_fingerprint_title
        SignalCategory.PreviousInstallsLog -> R.string.cat_previous_installs_log_title
    }

    /**
     * Returns the subtitle string resource for [category].
     *
     * @param category Category to look up.
     * @return `@StringRes` id of the localized subtitle.
     */
    @StringRes
    fun subtitleRes(category: SignalCategory): Int = when (category) {
        SignalCategory.DeviceIdentity -> R.string.cat_device_identity_subtitle
        SignalCategory.GoogleAccount -> R.string.cat_google_account_subtitle
        SignalCategory.SystemInfo -> R.string.cat_system_info_subtitle
        SignalCategory.Display -> R.string.cat_display_subtitle
        SignalCategory.Locale -> R.string.cat_locale_subtitle
        SignalCategory.Accessibility -> R.string.cat_accessibility_subtitle
        SignalCategory.DeviceMotion -> R.string.cat_device_motion_subtitle
        SignalCategory.Battery -> R.string.cat_battery_subtitle
        SignalCategory.Storage -> R.string.cat_storage_subtitle
        SignalCategory.Network -> R.string.cat_network_subtitle
        SignalCategory.Fonts -> R.string.cat_fonts_subtitle
        SignalCategory.InstalledVoices -> R.string.cat_installed_voices_subtitle
        SignalCategory.AppInfo -> R.string.cat_app_info_subtitle
        SignalCategory.Pasteboard -> R.string.cat_pasteboard_subtitle
        SignalCategory.Audio -> R.string.cat_audio_subtitle
        SignalCategory.Graphics -> R.string.cat_graphics_subtitle
        SignalCategory.Telephony -> R.string.cat_telephony_subtitle
        SignalCategory.Motion -> R.string.cat_motion_subtitle
        SignalCategory.Location -> R.string.cat_location_subtitle
        SignalCategory.Cameras -> R.string.cat_cameras_subtitle
        SignalCategory.Bluetooth -> R.string.cat_bluetooth_subtitle
        SignalCategory.LocalNetwork -> R.string.cat_local_network_subtitle
        SignalCategory.Contacts -> R.string.cat_contacts_subtitle
        SignalCategory.Photos -> R.string.cat_photos_subtitle
        SignalCategory.Calendar -> R.string.cat_calendar_subtitle
        SignalCategory.Reminders -> R.string.cat_reminders_subtitle
        SignalCategory.MusicLibrary -> R.string.cat_music_library_subtitle
        SignalCategory.InstalledAppsProbe -> R.string.cat_installed_apps_probe_subtitle
        SignalCategory.WebViewFingerprint -> R.string.cat_webview_fingerprint_subtitle
        SignalCategory.PreviousInstallsLog -> R.string.cat_previous_installs_log_subtitle
    }
}

/**
 * Composable extension resolving this category's localized title.
 *
 * @return The translated title for the current locale.
 */
@Composable
fun SignalCategory.localizedTitle(): String = stringResource(CategoryStrings.titleRes(this))

/**
 * Composable extension resolving this category's localized subtitle.
 *
 * @return The translated subtitle for the current locale.
 */
@Composable
fun SignalCategory.localizedSubtitle(): String = stringResource(CategoryStrings.subtitleRes(this))
