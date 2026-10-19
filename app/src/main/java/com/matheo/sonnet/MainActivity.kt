package com.matheo.sonnet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.matheo.sonnet.data.FxPrefs
import com.matheo.sonnet.data.NativePlayerGate
import com.matheo.sonnet.data.SettingsRepository
import com.matheo.sonnet.ui.SonnetRoot
import com.matheo.sonnet.ui.theme.accentHex
import com.matheo.sonnet.ui.theme.resolveAccent
import com.matheo.sonnet.ui.theme.SonnetTheme
import com.matheo.sonnet.web.WebHost
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var web: WebHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        web = WebHost(this)

        lifecycleScope.launch {
            SettingsRepository.playerPrefs.collect { NativePlayerGate.enabled = it.useNativePlayer }
        }
        lifecycleScope.launch {
            combine(SettingsRepository.webPrefs, SettingsRepository.accentIndex, SettingsRepository.fxPrefs) { w, a, f ->
                Triple(w, a, f)
            }.collect { (w, a, f) ->
                val hex = accentHex(resolveAccent(a, f.accentHue, f.themeMode == FxPrefs.THEME_LIGHT))
                web.setTheme(w.themeScope, hex, w.customCss)
                web.setFx(f, hex)
            }
        }

        setContent {
            val accent by SettingsRepository.accentIndex.collectAsState(initial = 0)
            val fx by SettingsRepository.fxPrefs.collectAsState(initial = FxPrefs())
            SonnetTheme(accentIndex = accent, themeMode = fx.themeMode, accentHue = fx.accentHue) {
                SonnetRoot(web)
            }
        }
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
