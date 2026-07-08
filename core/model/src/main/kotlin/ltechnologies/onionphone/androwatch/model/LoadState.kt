package ltechnologies.onionphone.androwatch.model

/**
 * Lifecycle state of an asynchronous category collection, surfaced to the UI.
 *
 * - [Idle] — collection has not been requested yet.
 * - [Loading] — collection is in progress.
 * - [Loaded] — collection completed successfully and signals are available.
 * - [Denied] — a required runtime permission was not granted, so collection was skipped.
 * - [Error] — collection failed unexpectedly.
 */
enum class LoadState {
    Idle,
    Loading,
    Loaded,
    Denied,
    Error,
}
