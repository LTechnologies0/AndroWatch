package ltechnologies.onionphone.androwatch.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ltechnologies.onionphone.androwatch.model.FingerprintSignal
import androidx.tracing.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

/**
 * One category's slice of an exported report.
 *
 * @property category Category name (see [ltechnologies.onionphone.androwatch.model.SignalCategory]).
 * @property sensitivity Category sensitivity tier name.
 * @property signals Collected signals belonging to this category.
 */
@Serializable
data class ExportCategory(
    val category: String,
    val sensitivity: String,
    val signals: List<FingerprintSignal>,
)

/**
 * Top-level serializable structure written to the exported JSON report.
 *
 * @property generatedAt Report creation time in epoch milliseconds.
 * @property categories Per-category signal groups included in the report.
 */
@Serializable
data class ExportReport(
    val generatedAt: Long,
    val categories: List<ExportCategory>,
)

/**
 * Writes a fingerprint report to a shareable file and launches the system share sheet.
 *
 * The report JSON is written to app cache and shared via a [FileProvider] URI so no external
 * storage permission is required. Previous export files are deleted on the next [share] and
 * via [purgeStaleExports] at process start so identifier-bearing JSON does not linger.
 *
 * @property context Context used for cache access, URI grants and launching the chooser.
 */
class ReportExporter(private val context: Context) {
    /**
     * Persists [json] to a cache file and opens the Android share sheet for it.
     *
     * File writing runs on [Dispatchers.IO]; the chooser is launched on the main thread. The
     * shared URI carries a temporary read grant to the receiving app. Any previous export
     * file is deleted before the new write; the just-shared file is deleted on the next
     * process start (or the next [share]) because the receiver may still be reading it.
     *
     * @param json Serialized [ExportReport] JSON to share.
     * @param chooserTitle Title shown on the system share chooser.
     */
    suspend fun share(json: String, chooserTitle: String) {
        val uri = withContext(Dispatchers.IO) {
            trace("AW/export/write") {
                val dir = exportDir(context)
                purgeStaleExports(context)
                dir.mkdirs()
                val file = File(dir, EXPORT_FILE)
                file.writeText(json, Charsets.UTF_8)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
        withContext(Dispatchers.Main.immediate) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, chooserTitle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    companion object {
        private const val EXPORT_DIR = "exports"
        private const val EXPORT_FILE = "androwatch-report.json"

        private val IDENTIFIER_KEY_HINTS = listOf(
            "id", "drm", "serial", "mac", "imei", "uuid", "advertis",
            "widevine", "clearkey", "playready", "ssaid", "gsf",
            "androidid", "productname", "buildhost", "builduser",
        )

        fun exportDir(context: Context): File = File(context.cacheDir, EXPORT_DIR)

        /** Deletes leftover export files from a previous share / crashed write. */
        fun purgeStaleExports(context: Context) {
            val dir = exportDir(context)
            if (!dir.exists()) return
            dir.listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
        }

        /**
         * Hashes [FingerprintSignal.rawValue] when the signal looks like a stable identifier
         * so an exported report cannot leak the original token.
         */
        fun sanitizeSignal(signal: FingerprintSignal): FingerprintSignal {
            val raw = signal.rawValue ?: return signal
            if (!isIdentifierSignal(signal.id)) return signal
            if (isSha256Hex(raw)) return signal
            return signal.copy(rawValue = sha256Hex(raw))
        }

        fun sanitizeSignals(signals: List<FingerprintSignal>): List<FingerprintSignal> =
            signals.map(::sanitizeSignal)

        internal fun isIdentifierSignal(id: String): Boolean {
            val key = id.substringAfterLast('.').lowercase()
            return IDENTIFIER_KEY_HINTS.any { key.contains(it) }
        }

        private fun isSha256Hex(value: String): Boolean =
            value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            val hex = CharArray(digest.size * 2)
            val alphabet = "0123456789abcdef"
            digest.forEachIndexed { i, b ->
                val v = b.toInt() and 0xff
                hex[i * 2] = alphabet[v ushr 4]
                hex[i * 2 + 1] = alphabet[v and 0x0f]
            }
            return String(hex)
        }
    }
}
