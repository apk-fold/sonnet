package com.matheo.sonnet.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.matheo.sonnet.BuildConfig
import com.matheo.sonnet.data.FxPrefs
import com.matheo.sonnet.data.JellyfinSession
import com.matheo.sonnet.data.NativePlayerGate
import com.matheo.sonnet.data.SessionStore
import com.matheo.sonnet.data.SettingsRepository
import com.matheo.sonnet.player.PlayRequest
import org.json.JSONObject

/**
 * Possède les deux WebViews de l'app (Jellyfin + Bridge Hub) pour toute la vie de l'activité :
 * changer d'onglet est instantané (plus de rechargement), plier/déplier le Fold ne recharge rien.
 */
class WebHost(private val activity: ComponentActivity) {

    @Volatile var serverUrl: String = SettingsRepository.DEFAULT_SERVER
        private set
    @Volatile var bridgeUrl: String = SettingsRepository.DEFAULT_BRIDGE
        private set

    var jellyfinLoading by mutableStateOf(true); private set
    var jellyfinError by mutableStateOf<String?>(null); private set
    var bridgeLoading by mutableStateOf(false); private set
    var bridgeError by mutableStateOf<String?>(null); private set
    var fullscreenActive by mutableStateOf(false); private set

    private var loadedJellyfin: String? = null
    private var loadedBridge: String? = null
    private var themeScope = 1
    private var themeCss = ""

    // Sonnet FX : GSAP + sonnet-fx.js (dossier assets), injectés dans les deux WebViews
    private var fxPrefs = FxPrefs()
    private var fxAccent = "#7F77DD"
    private val gsapJs: String by lazy { readAsset("gsap.min.js") }
    private val fxJs: String by lazy { readAsset("sonnet-fx.js") }

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val fileLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    val jellyfin: WebView = createWebView(isJellyfin = true)
    val bridge: WebView = createWebView(isJellyfin = false)

    // ------------------------------------------------------------ Navigation

    fun loadJellyfin(server: String, force: Boolean = false) {
        val clean = server.trim().trimEnd('/')
        serverUrl = clean
        if (clean.isEmpty()) return // pas encore configuré : l'écran de démarrage demande l'adresse
        if (force || loadedJellyfin != clean) {
            loadedJellyfin = clean
            jellyfinError = null
            jellyfin.loadUrl("$clean/web/")
        }
    }

    fun loadBridge(url: String, force: Boolean = false) {
        val clean = url.trim().trimEnd('/')
        bridgeUrl = clean
        if (clean.isEmpty()) return
        if (force || loadedBridge != clean) {
            loadedBridge = clean
            bridgeError = null
            bridge.loadUrl("$clean/")
        }
    }

    /** Ouvre une page de Jellyfin web (ex. "#/dashboard/plugins") sans recharger l'application. */
    fun navigateJellyfin(route: String) {
        if (serverUrl.isEmpty()) return
        val hash = route.removePrefix("#")
        val js = "(function(){ if (location.pathname.indexOf('/web') !== -1 && document.readyState !== 'loading') " +
            "{ location.hash = ${JSONObject.quote(hash)}; return 'ok'; } return 'no'; })()"
        jellyfin.evaluateJavascript(js) { result ->
            if (result?.contains("ok") != true) {
                loadedJellyfin = serverUrl
                jellyfin.loadUrl("$serverUrl/web/#$hash")
            }
        }
    }

    fun reloadJellyfin() = loadJellyfin(serverUrl, force = true)
    fun reloadBridge() = loadBridge(bridgeUrl, force = true)

    fun setTheme(scope: Int, accentHex: String, customCss: String) {
        themeScope = scope
        themeCss = InjectedScripts.themeCss(accentHex, customCss)
        pushTheme()
    }

    private fun pushTheme() {
        val js = "window.__sonnetSetTheme && window.__sonnetSetTheme($themeScope, ${JSONObject.quote(themeCss)});"
        jellyfin.evaluateJavascript(js, null)
    }

    // ------------------------------------------------------------ Sonnet FX

    /** Applique les options FX (animations, gestes, thème) à la page courante et aux prochaines. */
    fun setFx(prefs: FxPrefs, accentHex: String) {
        fxPrefs = prefs
        fxAccent = accentHex
        val bg = when (prefs.themeMode) {
            FxPrefs.THEME_AMOLED -> Color.BLACK
            FxPrefs.THEME_LIGHT -> Color.WHITE
            else -> Color.parseColor("#050208")
        }
        jellyfin.setBackgroundColor(bg)
        bridge.setBackgroundColor(bg)
        jellyfin.evaluateJavascript("window.__sonnetFxInit && window.__sonnetFxInit(${fxConfig(true)});", null)
        bridge.evaluateJavascript("window.__sonnetFxInit && window.__sonnetFxInit(${fxConfig(false)});", null)
    }

    private fun fxConfig(isJellyfin: Boolean): String {
        val p = fxPrefs
        val on = isJellyfin || p.bridgeFx
        return JSONObject()
            .put("mode", if (isJellyfin) "jellyfin" else "bridge")
            .put("anim", on && p.animations)
            .put("max", on && p.animations && p.crazyFx)
            .put("press", on && p.pressFeedback)
            .put("haptics", on && p.haptics)
            .put("pull", on && p.pullShortcuts)
            .put("dbl", isJellyfin && p.doubleTapFavorite)
            .put("swipe", isJellyfin && p.swipeRows)
            .put("theme", p.themeMode)
            .put("accent", fxAccent)
            .toString()
    }

    private fun injectFx(view: WebView?, isJellyfin: Boolean) {
        if (view == null || gsapJs.isEmpty() || fxJs.isEmpty()) return
        view.evaluateJavascript(gsapJs + "\n" + fxJs + "\n;window.__sonnetFxInit(" + fxConfig(isJellyfin) + ");", null)
    }

    private fun readAsset(name: String): String =
        runCatching { activity.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")

    fun exitFullscreen() {
        val view = customView ?: return
        (activity.window.decorView as? FrameLayout)?.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        fullscreenActive = false
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    fun destroy() {
        listOf(jellyfin, bridge).forEach {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
    }

    // ------------------------------------------------------------ Pont JS

    private inner class NativeBridge {
        @JavascriptInterface
        fun shouldIntercept(): Boolean = NativePlayerGate.shouldIntercept()

        @JavascriptInterface
        fun playVideo(json: String) {
            val request = PlayRequest.fromJson(json, serverUrl) ?: return
            activity.runOnUiThread {
                runCatching { activity.startActivity(request.toIntent(activity)) }
            }
        }

        @JavascriptInterface
        fun onSession(json: String) {
            runCatching {
                val o = JSONObject(json)
                val token = o.optString("token")
                val user = o.optString("userId")
                if (token.isBlank() || user.isBlank() || token == "null") {
                    SessionStore.clear()
                } else {
                    val server = o.optString("server").takeIf { it.startsWith("http") }?.trimEnd('/') ?: serverUrl
                    SessionStore.update(JellyfinSession(server, token, user))
                }
            }
        }
    }

    /** Seule fonction exposée aux pages : une petite vibration. Rien d'autre (le Bridge Hub peut être un site externe). */
    private inner class FxBridge {
        @JavascriptInterface
        fun haptic(kind: String) {
            activity.runOnUiThread {
                val type = if (kind == "heavy") HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
                runCatching { jellyfin.performHapticFeedback(type) }
            }
        }
    }

    // ------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(isJellyfin: Boolean): WebView = WebView(activity).apply {
        setBackgroundColor(Color.parseColor("#050208"))
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString SonnetApp/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        if (isJellyfin) addJavascriptInterface(NativeBridge(), "SonnetNative")
        addJavascriptInterface(FxBridge(), "SonnetFx")

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                setLoading(isJellyfin, true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                setLoading(isJellyfin, false)
                if (isJellyfin) {
                    view?.evaluateJavascript(InjectedScripts.CORE, null)
                    pushTheme()
                }
                injectFx(view, isJellyfin)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val message = error?.description?.toString() ?: "Erreur réseau"
                    if (isJellyfin) jellyfinError = message else bridgeError = message
                    setLoading(isJellyfin, false)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return false
                val scheme = target.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    if (!isJellyfin) return false // le Bridge Hub peut ouvrir d'autres serveurs dans l'app
                    val base = Uri.parse(serverUrl)
                    if (target.host == base.host && target.port == base.port) return false
                }
                if (scheme == "about" || scheme == "blob" || scheme == "data" || scheme == "javascript") return false
                // Liens externes (IMDb, TMDB, YouTube, mailto…) : application dédiée / navigateur
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                (activity.window.decorView as? FrameLayout)?.addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                fullscreenActive = true
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: return false
                return runCatching {
                    fileLauncher.launch(intent)
                    true
                }.getOrElse {
                    fileCallback = null
                    false
                }
            }
        }

        // "Télécharger" dans Jellyfin → gestionnaire de téléchargements Android
        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            runCatching {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val req = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                activity.getSystemService(DownloadManager::class.java)?.enqueue(req)
                Toast.makeText(activity, "Téléchargement de $name lancé", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(isJellyfin: Boolean, value: Boolean) {
        if (isJellyfin) {
            jellyfinLoading = value
            if (value) jellyfinError = null
        } else {
            bridgeLoading = value
            if (value) bridgeError = null
        }
    }
}
