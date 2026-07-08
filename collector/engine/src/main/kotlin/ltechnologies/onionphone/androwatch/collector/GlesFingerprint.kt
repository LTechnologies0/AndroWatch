package ltechnologies.onionphone.androwatch.collector

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import java.nio.IntBuffer

/**
 * Immutable result of an OpenGL ES / EGL fingerprint probe.
 *
 * @property glEsVersion GL ES version reported by [ActivityManager.getDeviceConfigurationInfo].
 * @property reqGlEsVersion Packed required GL ES version from the device configuration.
 * @property renderer `GL_RENDERER` string (GPU model).
 * @property vendor `GL_VENDOR` string (GPU vendor).
 * @property version `GL_VERSION` string.
 * @property extensionsHash SHA-256 of the sorted GL extensions list.
 * @property eglExtensionsHash SHA-256 of the EGL extensions list.
 * @property glslVersion `GL_SHADING_LANGUAGE_VERSION` string.
 * @property glMaxTexture `GL_MAX_TEXTURE_SIZE`.
 * @property glMaxViewport `GL_MAX_VIEWPORT_DIMS` formatted as `WxH`.
 */
data class GlesFingerprintResult(
    val glEsVersion: String,
    val reqGlEsVersion: Int,
    val renderer: String,
    val vendor: String,
    val version: String,
    val extensionsHash: String,
    val eglExtensionsHash: String,
    val glslVersion: String,
    val glMaxTexture: String,
    val glMaxViewport: String,
)

/**
 * Collects an OpenGL ES / EGL fingerprint by briefly creating an offscreen GL context.
 *
 * Reads the GL ES version from [ActivityManager], then (via [readEglStrings]) initializes
 * EGL with a 1x1 pbuffer surface and queries renderer/vendor/version, GLSL version,
 * max texture/viewport limits, and hashed extension lists. GPU renderer/vendor strings are
 * among the most distinctive passive fingerprinting signals.
 *
 * Privacy: no permission required, but the GPU renderer/vendor are strongly identifying.
 * All EGL failures degrade gracefully to `"unavailable"` values.
 *
 * @param context Context used to obtain [ActivityManager].
 * @return A populated [GlesFingerprintResult]; EGL fields fall back to `"unavailable"` on failure.
 */
fun collectGlesFingerprint(context: Context): GlesFingerprintResult {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val config = am.deviceConfigurationInfo
    val glEsFromAm = config.glEsVersion.toString()
    val reqGlEs = config.reqGlEsVersion

    val eglStrings = runCatching { readEglStrings() }.getOrElse {
        GlesStrings("unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable", "unavailable")
    }

    return GlesFingerprintResult(
        glEsVersion = glEsFromAm,
        reqGlEsVersion = reqGlEs,
        renderer = eglStrings.renderer,
        vendor = eglStrings.vendor,
        version = eglStrings.version,
        extensionsHash = sha256Hex(eglStrings.extensions),
        eglExtensionsHash = sha256Hex(eglStrings.eglExtensions),
        glslVersion = eglStrings.glslVersion,
        glMaxTexture = eglStrings.maxTexture,
        glMaxViewport = eglStrings.maxViewport,
    )
}

/** Raw (unhashed) GL/EGL strings captured from an active context, prior to hashing. */
private data class GlesStrings(
    val renderer: String,
    val vendor: String,
    val version: String,
    val extensions: String,
    val eglExtensions: String,
    val glslVersion: String,
    val maxTexture: String,
    val maxViewport: String,
)

/**
 * Initializes an EGL context on a 1x1 pbuffer surface and reads GL/EGL identity strings.
 *
 * The context, surface and (when owned) display are always torn down in the `finally`
 * block to avoid leaking GPU resources.
 *
 * @param display Optional pre-initialized display to reuse; when `null`, a default display
 *   is created and terminated by this function.
 * @return Captured [GlesStrings] from the active GL context.
 * @throws IllegalStateException if no display/config is available or EGL init fails.
 */
private fun readEglStrings(display: EGLDisplay? = null): GlesStrings {
    val eglDisplay: EGLDisplay = display ?: EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    val ownsDisplay = display == null
    if (eglDisplay == EGL14.EGL_NO_DISPLAY) error("no display")
    val version = IntArray(2)
    if (ownsDisplay && !EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) error("egl init failed")

    var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    try {
        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        val eglConfig = configs[0] ?: error("no config")

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0)

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "unknown"
        val glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: "unknown"
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
        val glsl = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION) ?: "unknown"
        val texBuf = IntBuffer.allocate(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, texBuf)
        val vpBuf = IntBuffer.allocate(2)
        GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, vpBuf)
        val eglExt = EGL14.eglQueryString(eglDisplay, EGL14.EGL_EXTENSIONS) ?: ""

        return GlesStrings(
            renderer, vendor, glVersion, extensions, eglExt, glsl,
            texBuf.get(0).toString(), "${vpBuf.get(0)}x${vpBuf.get(1)}",
        )
    } finally {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
        if (ownsDisplay) EGL14.eglTerminate(eglDisplay)
    }
}
