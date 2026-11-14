package com.zirakusa.stone.nativeapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.stone.nativeapp.data.persistence.StoneSettingsStore
import com.zirakusa.stone.nativeapp.playback.AudioDiagnostics
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService
import com.zirakusa.stone.nativeapp.ui.ImmersiveSystemBarsController
import java.util.Locale

@SuppressLint("RestrictedApi")
class MainActivity : ComponentActivity() {
    internal val immersiveSystemBars by lazy { ImmersiveSystemBarsController(this) }
    private var pictureInPicture by mutableStateOf(false)
    private var windowOrientation by mutableIntStateOf(Configuration.ORIENTATION_UNDEFINED)

    override fun attachBaseContext(newBase: Context) {
        val language = runCatching { StoneSettingsStore(newBase).read().language }.getOrDefault("es")
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ImmersiveSystemBarsController is the single owner of edge-to-edge and
        // system-bar visibility for the Activity and its Compose windows.
        immersiveSystemBars.attach()
        pictureInPicture = isInPictureInPictureMode
        windowOrientation = resources.configuration.orientation
        AudioDiagnostics.log(this, "MainActivity.onCreate", state = StonePlaybackService.diagnosticState(),
            includeSystemEffects = true)
        setContent {
            com.zirakusa.stone.nativeapp.ui.StoneApp(
                isPictureInPicture = pictureInPicture,
                windowOrientation = windowOrientation
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The manifest keeps the playback Activity alive while rotating. Push
        // the new orientation into Compose explicitly so the three-pane layout
        // is shown immediately instead of waiting for a process recreation.
        windowOrientation = newConfig.orientation
        immersiveSystemBars.onConfigurationChanged()
        AudioDiagnostics.log(this, "MainActivity.onConfigurationChanged(orientation=${newConfig.orientation})",
            state = StonePlaybackService.diagnosticState())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        immersiveSystemBars.onWindowFocusChanged(hasFocus)
        AudioDiagnostics.log(this, "MainActivity.onWindowFocusChanged($hasFocus)",
            state = StonePlaybackService.diagnosticState())
        if (hasFocus && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val bounds = window.decorView
            val rect = android.graphics.Rect(0, 0, bounds.width, bounds.height)
            setPictureInPictureParams(
                android.app.PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(true)
                    .setSourceRectHint(rect)
                    .build()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        immersiveSystemBars.onResume()
        val before = AudioDiagnostics.capture(this, StonePlaybackService.diagnosticState())
        StonePlaybackService.syncWithAndroidVolume()
        AudioDiagnostics.log(this, "MainActivity.onResume", before,
            StonePlaybackService.diagnosticState())
    }

    override fun onPause() {
        AudioDiagnostics.log(this, "MainActivity.onPause", state = StonePlaybackService.diagnosticState())
        immersiveSystemBars.onPause()
        super.onPause()
    }

    override fun onStop() {
        AudioDiagnostics.log(this, "MainActivity.onStop", state = StonePlaybackService.diagnosticState())
        immersiveSystemBars.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        AudioDiagnostics.log(this, "MainActivity.onDestroy", state = StonePlaybackService.diagnosticState())
        immersiveSystemBars.dispose()
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean,
        newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPicture = isInPictureInPictureMode
    }
}
