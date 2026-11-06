package com.zirakusa.stone.nativeapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Shows the active sleep timer using the sun by day and the moon by night. */
@Composable
fun SleepTimerIndicator(
    minutes: Int,
    modifier: Modifier = Modifier,
    endAtMillis: Long = 0L,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    if (minutes <= 0) return
    val fallbackEndAtMillis = remember(minutes) { System.currentTimeMillis() + minutes * 60_000L }
    val displayEndAtMillis = endAtMillis.takeIf { it > 0L } ?: fallbackEndAtMillis
    var daylight by remember { mutableStateOf(isDaylight()) }
    LaunchedEffect(Unit) {
        while (true) {
            daylight = isDaylight()
            delay(60_000L)
        }
    }
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (showLabel) 30.dp else 44.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = daylight,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "sleep-timer-daylight"
            ) { isDay ->
                Icon(
                    if (isDay) Icons.Default.WbSunny else Icons.Default.NightlightRound,
                    stringResource(R.string.sleep_timer_active),
                    tint = StonePalette.Yellow,
                    modifier = Modifier.size(if (showLabel) 20.dp else 24.dp)
                )
            }
        }
        if (showLabel) {
            Text(
                stringResource(R.string.sleep_timer_until, formatEndTime(displayEndAtMillis)),
                color = StonePalette.Yellow,
                maxLines = 1
            )
        }
    }
}

private fun isDaylight(): Boolean {
    val time = LocalTime.now()
    return time.hour in 6..17
}

private fun formatEndTime(endAtMillis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .format(Instant.ofEpochMilli(endAtMillis).atZone(ZoneId.systemDefault()))
}.getOrDefault("")
