package ltechnologies.onionphone.androwatch.model

/**
 * Aggregate tally of collected signals for a category.
 *
 * @property total Total number of signals (successful + failed).
 * @property failed Number of signals whose probe failed ([DisplayHint.Error]).
 * @property ok Number of successfully collected signals (`total - failed`).
 */
data class SignalCounts(val total: Int, val failed: Int) {
    val ok: Int get() = total - failed
}

/**
 * Counts signals in this list whose probe failed (rendered as [DisplayHint.Error]).
 *
 * @return Number of failed signals.
 */
fun List<FingerprintSignal>.failedCount(): Int {
    var failed = 0
    for (signal in this) {
        if (signal.displayHint == DisplayHint.Error) failed++
    }
    return failed
}

/**
 * Counts successfully collected signals in this list.
 *
 * @return Number of non-failed signals (`size - failedCount()`).
 */
fun List<FingerprintSignal>.okCount(): Int = size - failedCount()

/**
 * Computes total and failed counts in a single pass when both totals are needed (e.g. home badges).
 *
 * @return A [SignalCounts] holding the total and failed tallies.
 */
fun List<FingerprintSignal>.signalCounts(): SignalCounts {
    val failed = failedCount()
    return SignalCounts(size, failed)
}
