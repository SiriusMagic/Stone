package com.zirakusa.stone.nativeapp.feature.settings.api

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.actions.ActionRisk
import com.zirakusa.stone.nativeapp.automation.actions.RecordStatus
import com.zirakusa.stone.nativeapp.automation.actions.StoneAutomation
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ActivityFilter(val label: Int) {
    ALL(R.string.api_filter_all), QUERIES(R.string.api_filter_queries), CHANGES(R.string.api_filter_changes),
    DENIED(R.string.api_filter_denied)
}

/** Actividad tab: counters since Stone opened, filters and the request log. */
@Composable internal fun ApiActivityTab() {
    val history by StoneAutomation.history.collectAsState()
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }
    ApiProgress()
    // Requests per minute for every agent together, or one token at a time.
    ApiSection(R.string.api_analytics, Icons.Default.MonitorHeart) { ApiAnalyticsCard() }
    ApiSection(R.string.api_tab_activity, Icons.Default.MonitorHeart, R.string.api_stats_since) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(history.count { it.status == RecordStatus.OK }, R.string.api_stats_ok, ApiGreen)
            Stat(history.count { it.status == RecordStatus.DENIED }, R.string.api_stats_denied, StonePalette.Yellow)
            Stat(history.count { it.status == RecordStatus.FAILED }, R.string.api_stats_failed, ApiRed)
            Stat(history.size, R.string.api_stats_total, StonePalette.Blue)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivityFilter.entries.forEach { item ->
                FilterChip(filter == item, { filter = item }, { Text(stringResource(item.label), fontSize = 12.sp) })
            }
        }
        ApiActivityList(filter = { record ->
            when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.QUERIES -> record.risk == ActionRisk.READ
                ActivityFilter.CHANGES -> record.risk != ActionRisk.READ
                ActivityFilter.DENIED -> record.status != RecordStatus.OK
            }
        })
    }
}

@Composable private fun RowScope.Stat(value: Int, label: Int, color: Color) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .1f)).padding(10.dp)) {
        Text(value.toString(), color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(stringResource(label), color = StonePalette.Secondary, fontSize = 11.sp, maxLines = 1)
    }
}

/** Request log, newest first; shows a pending approval on top. */
@Composable internal fun ApiActivityList(limit: Int = 200,
    filter: (com.zirakusa.stone.nativeapp.automation.actions.ActionRecord) -> Boolean = { true }) {
    val history by StoneAutomation.history.collectAsState()
    val pending by StoneAutomation.confirmation.collectAsState()
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    pending?.let { ApiPill(stringResource(R.string.api_waiting) + ": " + it.action, StonePalette.Yellow, Modifier.padding(top = 8.dp)) }
    val shown = history.filter(filter).take(limit)
    if (shown.isEmpty()) Text(stringResource(R.string.api_no_activity), Modifier.padding(top = 10.dp), color = StonePalette.Muted)
    shown.forEach { record ->
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Text(clock.format(Date(record.timeMs)), color = StonePalette.Muted, fontSize = 12.sp, modifier = Modifier.width(62.dp))
            Column(Modifier.weight(1f)) {
                Text(record.action.replace('.', '_'), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(record.detail, color = if (record.ok) StonePalette.Secondary else record.status.color(), fontSize = 12.sp, maxLines = 2)
                if (record.token.isNotBlank()) Text(record.token, color = StonePalette.Muted, fontSize = 11.sp)
            }
            Box(Modifier.size(10.dp).padding(top = 2.dp).clip(RoundedCornerShape(5.dp)).background(record.status.color()))
        }
    }
}
