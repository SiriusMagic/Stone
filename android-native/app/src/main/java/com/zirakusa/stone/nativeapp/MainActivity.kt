package com.zirakusa.stone.nativeapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import java.util.Locale

@SuppressLint("RestrictedApi")
class MainActivity : ComponentActivity() {
    private var pictureInPicture by mutableStateOf(false)

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode)) {
            com.zirakusa.stone.nativeapp.playback.StonePlaybackService.handleVolumeKey(
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5, event.repeatCount > 0)
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
            if (event.action == KeyEvent.ACTION_DOWN) {
                service.handleVolumeKey(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5,
                    event.repeatCount > 0)
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
        pictureInPicture = isInPictureInPictureMode
        setContent { com.zirakusa.stone.nativeapp.ui.StoneApp(isPictureInPicture = pictureInPicture) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean,
        newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPicture = isInPictureInPictureMode
    }
}
