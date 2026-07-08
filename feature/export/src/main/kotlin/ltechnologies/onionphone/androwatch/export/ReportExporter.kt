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
 * storage permission is required.
 *
 * @property context Context used for cache access, URI grants and launching the chooser.
 */
class ReportExporter(private val context: Context) {
    /**
     * Persists [json] to a cache file and opens the Android share sheet for it.
     *
     * File writing runs on [Dispatchers.IO]; the chooser is launched on the main thread. The
     * shared URI carries a temporary read grant to the receiving app.
     *
     * @param json Serialized [ExportReport] JSON to share.
     * @param chooserTitle Title shown on the system share chooser.
     */
    suspend fun share(json: String, chooserTitle: String) {
        val uri = withContext(Dispatchers.IO) {
            trace("AW/export/write") {
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "androwatch-report.json")
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
}
