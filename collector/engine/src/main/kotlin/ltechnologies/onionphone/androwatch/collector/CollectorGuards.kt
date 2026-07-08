package ltechnologies.onionphone.androwatch.collector

import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import ltechnologies.onionphone.androwatch.model.SignalCategory

fun buildSignals(
    category: SignalCategory,
    block: MutableList<FingerprintSignal>.() -> Unit,
): List<FingerprintSignal> {
    val out = mutableListOf<FingerprintSignal>()
    runCatching { out.block() }.onFailure { e ->
        runCatching {
            awLogW("collect/${category.name}") { "uncaught: ${e.message ?: e.javaClass.simpleName}" }
        }
        // ponytail: keep probes already collected; only synthesize a row when nothing ran
        if (out.isEmpty()) {
            out.add(failedSignal(category, "_collect", category.name, e))
        }
    }
    return out
}

/** Runs [block] to produce a probe map; each entry is collected via [addSafe]. A failing batch is skipped. */
inline fun MutableList<FingerprintSignal>.forEachProbe(
    category: SignalCategory,
    rationale: String = "Device API fingerprint signal.",
    keyPrefix: String = "",
    namePrefix: String = "",
    crossinline block: () -> Map<String, String>,
) {
    val map = runCatching(block).onFailure { e ->
        runCatching {
            awLogW("collect/probes") { "${category.name}: ${e.message ?: e.javaClass.simpleName}" }
        }
    }.getOrNull() ?: return
    map.forEach { (k, v) ->
        val key = "$keyPrefix$k"
        val name = if (namePrefix.isEmpty()) k else "$namePrefix$k"
        addSafe(category, key, name, rationale) { signal(category, key, name, v, rationale) }
    }
}

inline fun MutableList<FingerprintSignal>.addSafe(
    category: SignalCategory,
    key: String,
    name: String,
    rationale: String = "Device API fingerprint signal.",
    block: () -> FingerprintSignal,
) {
    runCatching(block)
        .onSuccess { add(it) }
        .onFailure { e ->
            runCatching {
                awLogW("collect/signal") { "${category.name}.$key: ${e.message ?: e.javaClass.simpleName}" }
            }
            add(failedSignal(category, key, name, e, rationale))
        }
}

/** Adds signal only when block succeeds; failed probes are skipped (logged at WARN). */
inline fun MutableList<FingerprintSignal>.addOptional(
    category: SignalCategory,
    key: String,
    name: String,
    rationale: String = "Device API fingerprint signal.",
    block: () -> FingerprintSignal,
) {
    runCatching(block)
        .onSuccess { add(it) }
        .onFailure { e ->
            runCatching {
                awLogW("collect/skip") { "${category.name}.$key: ${e.message ?: e.javaClass.simpleName}" }
            }
        }
}
