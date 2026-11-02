package com.zirakusa.stone.nativeapp

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.util.Locale

class MainActivity : ComponentActivity() {
    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode)) {
            if (event.repeatCount == 0) {
                com.zirakusa.stone.nativeapp.playback.StonePlaybackService.handleVolumeKey(
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volumeKey = isVolumeKey(event.keyCode)
        val service = com.zirakusa.stone.nativeapp.playback.StonePlaybackService
        if (volumeKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                service.handleVolumeKey(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun attachBaseContext(newBase: Context) {
        val language = runCatching { StoneSettingsStore(newBase).read().language }.getOrDefault("es")
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { com.zirakusa.stone.nativeapp.ui.StoneApp() }
    }
}
