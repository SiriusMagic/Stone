package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.StoneAvatar
import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.feature.player.VideoScale
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@Composable internal fun AboutDetail(onPage: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        StoneAvatar(Modifier.size(112.dp), stringResource(R.string.stone))
        Text(stringResource(R.string.stone), fontSize = 24.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 14.dp))
        Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), color = StonePalette.Muted,
            modifier = Modifier.padding(top = 10.dp))
        SettingsGroup {
            SettingsRow(Icons.Default.ThumbUp, R.string.rate_stone, "") { openStoneRepository(context) }
        }
        SettingsGroup {
            SettingsRow(Icons.Default.Policy, R.string.privacy_policy, "") { onPage("privacy") }
            SettingsRow(Icons.Default.Description, R.string.terms_service, "") { onPage("terms") }
            SettingsRow(Icons.Default.Verified, R.string.open_source_licenses, "") { onPage("licenses") }
        }
        Text(stringResource(R.string.copyright_stone), color = StonePalette.Muted,
            fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp))
    }
}

@Composable internal fun VideoSettingsDetail(scale: VideoScale,
    onScaleChange: (VideoScale) -> Unit) {
    Text(stringResource(R.string.video_settings_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 14.dp))
    Text(stringResource(R.string.video_scale), color = StonePalette.Yellow,
        fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 4.dp))
    SettingsGroup {
        SettingsChoice(R.string.video_scale_auto, scale == VideoScale.AUTO) {
            onScaleChange(VideoScale.AUTO)
        }
        SettingsChoice(R.string.video_scale_fit, scale == VideoScale.FIT) {
            onScaleChange(VideoScale.FIT)
        }
        SettingsChoice(R.string.video_scale_zoom, scale == VideoScale.ZOOM) {
            onScaleChange(VideoScale.ZOOM)
        }
        SettingsChoice(R.string.video_scale_fill, scale == VideoScale.FILL) {
            onScaleChange(VideoScale.FILL)
        }
    }
}

@Composable internal fun ImmersiveSettingsDetail(enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit) {
    Text(stringResource(R.string.immersive_screen_hint), color = StonePalette.Muted,
        modifier = Modifier.padding(bottom = 14.dp))
    SettingsGroup {
        ToggleSetting(R.string.immersive_screen, R.string.immersive_screen_hint,
            enabled, onEnabledChange)
    }
}
