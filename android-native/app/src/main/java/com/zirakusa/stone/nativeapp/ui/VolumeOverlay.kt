package com.zirakusa.stone.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.playback.StonePlaybackService

@Composable fun VolumeOverlay(state: StonePlaybackService.VolumeUiState, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().padding(horizontal = 12.dp)
        .border(1.dp, Color(0xFF3A343F), RoundedCornerShape(24.dp)),
        color = Color(0xF217151C), shape = RoundedCornerShape(24.dp), shadowElevation = 14.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, stringResource(R.string.volume), tint = Color(0xFFF5C518),
                    modifier = Modifier.size(26.dp))
                Text(stringResource(R.string.volume), Modifier.weight(1f).padding(start = 10.dp),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${state.percent}%", color = Color(0xFFF5C518), fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold)
                Text(stringResource(if (state.percent > 100) R.string.boost else R.string.native_volume),
                    Modifier.padding(start = 10.dp).background(Color(0xFF2A2730), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (state.percent > 100) Color(0xFFF5C518) else Color(0xFFB8B5C0),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Slider(value = state.percent.toFloat(), onValueChange = {
                StonePlaybackService.setVolumePercent(it.toInt())
            }, valueRange = 0f..300f, modifier = Modifier.fillMaxWidth().height(34.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White,
                    activeTrackColor = Color(0xFFF5C518), inactiveTrackColor = Color(0xFF302C36)))
        }
    }
}
