package ltechnologies.onionphone.androwatch.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ltechnologies.onionphone.androwatch.collector.CollectorDispatchers
import ltechnologies.onionphone.androwatch.collector.CollectorRegistry
import ltechnologies.onionphone.androwatch.collector.CollectorRuntime
import ltechnologies.onionphone.androwatch.collector.CollectorRuntime.collectBounded
import ltechnologies.onionphone.androwatch.collector.CollectorRuntime.withPassivePermit
import ltechnologies.onionphone.androwatch.collector.LiveSignalCollector
import ltechnologies.onionphone.androwatch.collector.awTrace
import ltechnologies.onionphone.androwatch.export.ExportCategory
import ltechnologies.onionphone.androwatch.export.ExportReport
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.LoadState
import ltechnologies.onionphone.androwatch.model.Sensitivity
import ltechnologies.onionphone.androwatch.model.SignalCategory
import ltechnologies.onionphone.androwatch.permission.PermissionCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Immutable UI state exposed by [CategoryViewModel].
 *
 * @property signals Collected signals keyed by category.
 * @property loadStates Per-category [LoadState]; all start [LoadState.Idle].
 * @property errors Per-category error messages for categories in [LoadState.Error].
 */
data class UiState(
    val signals: Map<SignalCategory, List<FingerprintSignal>> = emptyMap(),
    val loadStates: Map<SignalCategory, LoadState> = SignalCategory.entries.associateWith { LoadState.Idle },
    val errors: Map<SignalCategory, String> = emptyMap(),
)

/**
 * [AndroidViewModel] that drives signal collection and exposes results as [UiState].
 *
 * Coordinates the [CollectorRegistry] under bounded concurrency/timeouts ([CollectorRuntime]),
 * gates permissioned categories via [PermissionCenter], observes [LiveSignalCollector] streams
 * and serializes reports for export. All state mutations are applied on the main dispatcher.
 *
 * @param app Application context backing collection and preferences.
 * @see UiState
 * @see CollectorRegistry
 */
class CategoryViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val permissionCenter = PermissionCenter()
    private val _state = MutableStateFlow(UiState())

    /** Observable UI state consumed by the Compose layer. */
    val state: StateFlow<UiState> = _state
    private var liveJob: Job? = null

    companion object {
        /** Pretty-printing JSON encoder used for report export. */
        private val exportJson = Json { prettyPrint = true }
    }

    /**
     * Collects all passive (no-permission) categories concurrently under the passive semaphore
     * and publishes their results.
     */
    fun refreshPassive() {
        val context = getApplication<Application>()
        val passive = SignalCategory.entries.filter { it.sensitivity == Sensitivity.Passive }
        viewModelScope.launch {
            awTrace("refreshPassive") {
                patchLoadStates(passive.associateWith { LoadState.Loading })
                val results = withContext(CollectorDispatchers.io) {
                    passive.map { category ->
                        async {
                            withPassivePermit {
                                val timeout = when (category) {
                                    SignalCategory.Graphics -> CollectorRuntime.GLES_COLLECT_TIMEOUT_MS
                                    else -> CollectorRuntime.DEFAULT_COLLECT_TIMEOUT_MS
                                }
                                category to collectBounded(
                                    CollectorRegistry.byCategory.getValue(category),
                                    context,
                                    timeout,
                                )
                            }
                        }
                    }.awaitAll()
                }
                patchResults(results)
            }
        }
    }

    /**
     * Refreshes a category after a permission decision, or marks it [LoadState.Denied].
     *
     * If the category needs no permission it refreshes directly; otherwise it refreshes only
     * when the permission is (now) granted.
     *
     * @param category Category to (re)collect.
     * @param granted Whether the just-completed permission request was granted.
     */
    fun enableAndRefresh(category: SignalCategory, granted: Boolean) {
        val context = getApplication<Application>()
        val kind = category.permissionKind ?: return refreshCategory(category)
        if (!granted && !permissionCenter.isGranted(context, kind)) {
            viewModelScope.launch { patchLoadStates(mapOf(category to LoadState.Denied)) }
            return
        }
        refreshCategory(category)
    }

    /**
     * Collects a single [category] under a bounded timeout and publishes its result.
     *
     * @param category Category to collect.
     */
    fun refreshCategory(category: SignalCategory) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            awTrace("refresh/${category.name}") {
                patchLoadStates(mapOf(category to LoadState.Loading))
                val result = withPassivePermit {
                    collectBounded(
                        CollectorRegistry.byCategory.getValue(category),
                        context,
                        when (category) {
                            SignalCategory.WebViewFingerprint -> CollectorRuntime.WEBVIEW_COLLECT_TIMEOUT_MS
                            SignalCategory.Graphics -> CollectorRuntime.GLES_COLLECT_TIMEOUT_MS
                            else -> CollectorRuntime.DEFAULT_COLLECT_TIMEOUT_MS
                        },
                    )
                }
                patchSingleResult(category, result)
            }
        }
    }

    /**
     * Starts observing a live category's [LiveSignalCollector] stream, replacing any prior
     * observation. No-op if the category has no live collector.
     *
     * @param category Category to observe live.
     */
    fun observeLiveCategory(category: SignalCategory) {
        stopLiveObservation()
        val collector = CollectorRegistry.byCategory[category] as? LiveSignalCollector ?: return
        val context = getApplication<Application>()
        liveJob = viewModelScope.launch {
            awTrace("live/${category.name}") {
                collector.liveFlow(context).collect { liveSignals ->
                    patchLiveSignals(category, liveSignals)
                }
            }
        }
    }

    /** Cancels the current live-observation job, if any. */
    fun stopLiveObservation() {
        liveJob?.cancel()
        liveJob = null
    }

    /**
     * Merges live signals into a static snapshot, with live values overriding static ones of
     * the same id.
     *
     * @param static Previously collected static signals.
     * @param live Freshly emitted live signals.
     * @return Combined list where live entries replace matching static entries.
     */
    private fun mergeSignals(
        static: List<FingerprintSignal>,
        live: List<FingerprintSignal>,
    ): List<FingerprintSignal> {
        if (live.isEmpty()) return static
        if (static.isEmpty()) return live
        val liveIds = HashSet<String>(live.size)
        live.forEach { liveIds.add(it.id) }
        return buildList(static.size + live.size) {
            static.forEach { if (it.id !in liveIds) add(it) }
            addAll(live)
        }
    }

    /** Cancels live observation when the ViewModel is destroyed. */
    override fun onCleared() {
        stopLiveObservation()
        super.onCleared()
    }

    /**
     * Serializes the current signal snapshot into a pretty-printed [ExportReport] JSON string.
     *
     * Runs on the IO dispatcher; categories are sorted by name for stable output.
     *
     * @return The report JSON.
     */
    suspend fun exportReportJson(): String = withContext(CollectorDispatchers.io) {
        awTrace("export/json") {
            val snapshot = _state.value
            val categories = snapshot.signals.map { (category, signals) ->
                ExportCategory(category = category.name, sensitivity = category.sensitivity.name, signals = signals)
            }.sortedBy { it.category }
            exportJson.encodeToString(
                ExportReport(
                    generatedAt = System.currentTimeMillis(),
                    categories = categories,
                ),
            )
        }
    }

    /**
     * Applies a batch of load-state changes on the main dispatcher.
     *
     * @param patch Category→[LoadState] updates to merge into state.
     */
    private suspend fun patchLoadStates(patch: Map<SignalCategory, LoadState>) {
        withContext(Dispatchers.Main.immediate) {
            _state.update { it.copy(loadStates = it.loadStates + patch) }
        }
    }

    /**
     * Applies a batch of collection results, updating signals/load-states/errors per category.
     *
     * @param results Category→[Result] pairs from concurrent collection.
     */
    private suspend fun patchResults(results: List<Pair<SignalCategory, Result<List<FingerprintSignal>>>>) {
        withContext(Dispatchers.Main.immediate) {
            _state.update { old ->
                val signals = old.signals.toMutableMap()
                val loads = old.loadStates.toMutableMap()
                val errors = old.errors.toMutableMap()
                results.forEach { (category, result) ->
                    if (result.isSuccess) {
                        signals[category] = result.getOrDefault(emptyList())
                        loads[category] = LoadState.Loaded
                        errors.remove(category)
                    } else {
                        loads[category] = LoadState.Error
                        errors[category] = result.exceptionOrNull()?.message ?: "error"
                    }
                }
                old.copy(signals = signals, loadStates = loads, errors = errors)
            }
        }
    }

    /**
     * Applies a single category's collection result on the main dispatcher.
     *
     * @param category Category the result belongs to.
     * @param result Success (signals) or failure (error message) outcome.
     */
    private suspend fun patchSingleResult(category: SignalCategory, result: Result<List<FingerprintSignal>>) {
        withContext(Dispatchers.Main.immediate) {
            _state.update {
                if (result.isSuccess) {
                    it.copy(
                        signals = it.signals + (category to result.getOrDefault(emptyList())),
                        loadStates = it.loadStates + (category to LoadState.Loaded),
                        errors = it.errors - category,
                    )
                } else {
                    it.copy(
                        loadStates = it.loadStates + (category to LoadState.Error),
                        errors = it.errors + (category to (result.exceptionOrNull()?.message ?: "error")),
                    )
                }
            }
        }
    }

    /**
     * Merges a live emission into state for [category] and marks it [LoadState.Loaded].
     *
     * @param category Category being observed live.
     * @param liveSignals Latest live signal snapshot.
     */
    private suspend fun patchLiveSignals(category: SignalCategory, liveSignals: List<FingerprintSignal>) {
        withContext(Dispatchers.Main.immediate) {
            _state.update { state ->
                val merged = mergeSignals(state.signals[category].orEmpty(), liveSignals)
                state.copy(
                    signals = state.signals + (category to merged),
                    loadStates = state.loadStates + (category to LoadState.Loaded),
                )
            }
        }
    }
}
