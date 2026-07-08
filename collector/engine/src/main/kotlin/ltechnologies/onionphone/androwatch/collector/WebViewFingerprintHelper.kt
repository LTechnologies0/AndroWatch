package ltechnologies.onionphone.androwatch.collector

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Result of the in-app WebView browser-fingerprint probe.
 *
 * Mirrors the properties a web tracking script could read from this device's WebView.
 * Hash-valued fields are already SHA-256 hashed; text fields are as reported by JS.
 *
 * @property userAgent WebView default user agent.
 * @property platform `navigator.platform`.
 * @property hardwareConcurrency `navigator.hardwareConcurrency`.
 * @property deviceMemory `navigator.deviceMemory`.
 * @property canvasHash Hash of the canvas 2D render.
 * @property webglVendor Unmasked WebGL vendor.
 * @property webglRenderer Unmasked WebGL renderer.
 * @property webglHash Hash of vendor|renderer.
 * @property timezone Intl timezone id.
 * @property languages Comma-separated `navigator.languages`.
 * @property screenSize `WxH` screen size.
 * @property colorDepth `screen.colorDepth`.
 * @property pixelRatio `window.devicePixelRatio`.
 * @property maxTouchPoints `navigator.maxTouchPoints`.
 * @property audioContextHash Hash of the OfflineAudioContext render.
 */
data class WebViewFingerprintResult(
    val userAgent: String,
    val platform: String,
    val hardwareConcurrency: String,
    val deviceMemory: String,
    val canvasHash: String,
    val webglVendor: String,
    val webglRenderer: String,
    val webglHash: String,
    val timezone: String,
    val languages: String,
    val screenSize: String,
    val colorDepth: String,
    val pixelRatio: String,
    val maxTouchPoints: String,
    val audioContextHash: String,
)

/**
 * Self-contained HTML+JS page that computes browser fingerprint signals (canvas, WebGL,
 * audio, navigator/screen properties) and delivers them as JSON via the `AndroWatchBridge`
 * JavaScript interface.
 */
private const val FINGERPRINT_HTML = """
<!DOCTYPE html><html><head><meta charset="utf-8"></head><body>
<script>
(function() {
  function canvasHash() {
    var c = document.createElement('canvas');
    c.width = 200; c.height = 50;
    var ctx = c.getContext('2d');
    ctx.textBaseline = 'top';
    ctx.font = '14px Arial';
    ctx.fillStyle = '#f60';
    ctx.fillRect(0, 0, 200, 50);
    ctx.fillStyle = '#069';
    ctx.fillText('AndroWatch', 2, 15);
    return c.toDataURL();
  }
  function webglInfo() {
    var c = document.createElement('canvas');
    var gl = c.getContext('webgl') || c.getContext('experimental-webgl');
    if (!gl) return { vendor: 'none', renderer: 'none', hash: 'none' };
    var dbg = gl.getExtension('WEBGL_debug_renderer_info');
    var vendor = dbg ? gl.getParameter(dbg.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR);
    var renderer = dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
    return { vendor: vendor, renderer: renderer, hash: vendor + '|' + renderer };
  }
  var cv = canvasHash();
  var gl = webglInfo();
  var payload = {
    platform: navigator.platform || 'unknown',
    hardwareConcurrency: String(navigator.hardwareConcurrency || 0),
    deviceMemory: String(navigator.deviceMemory || 0),
    canvasData: cv,
    webglVendor: gl.vendor,
    webglRenderer: gl.renderer,
    webglHash: gl.hash,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'unknown',
    languages: (navigator.languages || [navigator.language || '']).join(','),
    screenSize: screen.width + 'x' + screen.height,
    colorDepth: String(screen.colorDepth || 0),
    pixelRatio: String(window.devicePixelRatio || 1),
    maxTouchPoints: String(navigator.maxTouchPoints || 0),
    audioData: ''
  };
  function finish() { AndroWatchBridge.deliver(JSON.stringify(payload)); }
  try {
    var AC = window.OfflineAudioContext || window.webkitOfflineAudioContext;
    if (!AC) { finish(); return; }
    var ctx = new AC(1, 5000, 44100);
    var osc = ctx.createOscillator();
    osc.type = 'triangle';
    osc.frequency.value = 10000;
    var comp = ctx.createDynamicsCompressor();
    osc.connect(comp);
    comp.connect(ctx.destination);
    osc.start(0);
    ctx.startRendering().then(function(buf) {
      var ch = buf.getChannelData(0);
      var sum = 0;
      for (var i = 0; i < Math.min(ch.length, 128); i++) sum += Math.abs(ch[i]);
      payload.audioData = String(sum);
      finish();
    }).catch(function() { finish(); });
  } catch (e) { finish(); }
})();
</script>
</body></html>
"""

/**
 * Runs the [FINGERPRINT_HTML] page in an offscreen [WebView] and collects the resulting
 * browser fingerprint.
 *
 * All WebView operations run on the main thread. JavaScript delivers its payload through a
 * `@JavascriptInterface` bridge; the WebView is always destroyed (on completion, timeout,
 * or cancellation) to avoid leaks. A 5s watchdog yields a [fallbackResult] on timeout.
 *
 * Privacy: **high** — this reproduces exactly what a hostile web page could fingerprint
 * (canvas, WebGL, audio, UA). Hash-valued fields are SHA-256 hashed before being surfaced.
 * Backs the advanced WebViewFingerprint category.
 *
 * @param context Context used to build the WebView (application context is used).
 * @return A populated [WebViewFingerprintResult], or a fallback with failure sentinels.
 */
suspend fun collectWebViewFingerprint(context: Context): WebViewFingerprintResult {
    val appContext = context.applicationContext
    val ua = WebSettings.getDefaultUserAgent(appContext)
    return suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            val webView = WebView(appContext)
            webView.settings.javaScriptEnabled = true
            var finished = false
            fun disposeWebView() {
                runCatching { webView.removeJavascriptInterface("AndroWatchBridge") }
                webView.stopLoading()
                webView.destroy()
            }
            fun complete(result: WebViewFingerprintResult) {
                // ponytail: WebView APIs must run on main; deliver() fires on JavaBridge thread
                mainHandler.post {
                    if (finished) return@post
                    finished = true
                    disposeWebView()
                    if (cont.isActive) cont.resume(result)
                }
            }
            cont.invokeOnCancellation {
                mainHandler.post {
                    if (finished) return@post
                    finished = true
                    disposeWebView()
                }
            }
            webView.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun deliver(json: String) {
                        runCatching {
                            val obj = JSONObject(json)
                            WebViewFingerprintResult(
                                userAgent = ua,
                                platform = obj.optString("platform", "unknown"),
                                hardwareConcurrency = obj.optString("hardwareConcurrency", "0"),
                                deviceMemory = obj.optString("deviceMemory", "0"),
                                canvasHash = sha256Hex(obj.optString("canvasData", "")),
                                webglVendor = obj.optString("webglVendor", "unknown"),
                                webglRenderer = obj.optString("webglRenderer", "unknown"),
                                webglHash = sha256Hex(obj.optString("webglHash", "")),
                                timezone = obj.optString("timezone", "unknown"),
                                languages = obj.optString("languages", "unknown"),
                                screenSize = obj.optString("screenSize", "unknown"),
                                colorDepth = obj.optString("colorDepth", "0"),
                                pixelRatio = obj.optString("pixelRatio", "1"),
                                maxTouchPoints = obj.optString("maxTouchPoints", "0"),
                                audioContextHash = obj.optString("audioData", "").let {
                                    if (it.isBlank()) "" else sha256Hex(it)
                                },
                            )
                        }.fold(
                            onSuccess = { complete(it) },
                            onFailure = { complete(fallbackResult(ua, "parse_error")) },
                        )
                    }
                },
                "AndroWatchBridge",
            )
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // JS runs inline; bridge may already have fired.
                }
            }
            webView.loadDataWithBaseURL(null, FINGERPRINT_HTML, "text/html", "UTF-8", null)
            mainHandler.postDelayed({
                if (!finished) complete(fallbackResult(ua, "timeout"))
            }, 5_000L)
        }
    }
}

/**
 * Builds a [WebViewFingerprintResult] where every JS-derived field carries a failure
 * [reason] sentinel (e.g. `timeout`, `parse_error`), keeping the user agent intact.
 *
 * @param ua WebView user agent (still readable without JS).
 * @param reason Failure reason token propagated to all probe fields.
 * @return A fallback result signaling probe failure.
 */
private fun fallbackResult(ua: String, reason: String) = WebViewFingerprintResult(
    userAgent = ua,
    platform = reason,
    hardwareConcurrency = "0",
    deviceMemory = "0",
    canvasHash = reason,
    webglVendor = reason,
    webglRenderer = reason,
    webglHash = reason,
    timezone = reason,
    languages = reason,
    screenSize = reason,
    colorDepth = reason,
    pixelRatio = reason,
    maxTouchPoints = reason,
    audioContextHash = reason,
)
