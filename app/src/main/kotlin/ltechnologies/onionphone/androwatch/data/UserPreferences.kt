package ltechnologies.onionphone.androwatch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "androwatch_prefs")

/**
 * Persists lightweight user preferences via Jetpack [DataStore][preferencesDataStore].
 *
 * DataStore is used instead of `SharedPreferences` to keep reads/writes off the main thread.
 *
 * @property context Context owning the backing preferences DataStore.
 */
class UserPreferences(private val context: Context) {
    /** Emits `true` once onboarding has been completed; defaults to `false`. */
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETE] ?: false
    }

    /** Marks onboarding as complete, persisting the flag asynchronously. */
    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETE] = true
        }
    }

    companion object {
        /** DataStore key backing [onboardingComplete]. */
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
