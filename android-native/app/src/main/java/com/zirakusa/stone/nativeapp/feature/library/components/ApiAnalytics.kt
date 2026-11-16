package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.automation.ApiTokens
import com.zirakusa.stone.nativeapp.automation.RequestStats
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Time windows: 60 one-minute bars, or 48 half-hour bars for the last day. */
private enum class StatsRange(val label: Int, val buckets: Int, val bucketMinutes: Int) {
    HOUR(R.string.api_range_hour, 60, 1), DAY(R.string.api_range_day, 48, 30)
}

/**
 * Requests per minute: totals, average and peak per minute, errors and a bar per minute (or per
 * half hour). [fixedTokenId] shows one token only (token editor); otherwise chips pick "Todos" or a token.
 */
@Composable internal fun ApiAnalyticsCard(modifier: Modifier = Modifier, fixedTokenId: String? = null) {
    val revision by RequestStats.revision.collectAsState()
    var range by rememberSaveable { mutableStateOf(StatsRange.HOUR) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    // The window keeps moving even when no request arrives.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val tokenId = fixedTokenId ?: picked
    val series = remember(revision, now, range, tokenId) {
        RequestStats.series(tokenId, range.buckets, range.bucketMinutes, System.currentTimeMillis())
    }
    val perMinute = remember(revision, now, range, tokenId) {
        if (range.bucketMinutes == 1) series else RequestStats.series(tokenId, range.buckets * range.bucketMinutes, 1)
    }
    val total = series.sumOf { it[RequestStats.OK] + it[RequestStats.DENIED] + it[RequestStats.FAILED] }
    val errors = series.sumOf { it[RequestStats.FAILED] }
    val denied = series.sumOf { it[RequestStats.DENIED] }
    val peak = perMinute.maxOfOrNull { it[RequestStats.OK] + it[RequestStats.DENIED] + it[RequestStats.FAILED] } ?: 0
    val minutes = range.buckets * range.bucketMinutes
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatsRange.entries.forEach { item ->
                FilterChip(range == item, { range = item }, { Text(stringResource(item.label), fontSize = 12.sp) })
            }
        }
        if (fixedTokenId == null && ApiTokens.tokens.size > 1) Row(Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(picked == null, { picked = null }, { Text(stringResource(R.string.api_an_all), fontSize = 12.sp) })
            ApiTokens.tokens.take(4).forEach { token ->
                FilterChip(picked == token.id, { picked = token.id }, { Text(token.name, fontSize = 12.sp, maxLines = 1) })
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(total.toString(), stringResource(R.string.api_an_requests))
            Tile("%.1f".format(total.toFloat() / minutes), stringResource(R.string.api_an_per_minute),
                stringResource(R.string.api_an_peak, peak))
            Tile(errors.toString(), stringResource(R.string.api_an_errors),
                "$denied " + stringResource(R.string.api_stats_denied).lowercase(), alert = errors > 0)
        }
        RequestBars(series, range, peak, total, Modifier.padding(top = 12.dp).fillMaxWidth().height(132.dp))
        if (fixedTokenId == null && picked == null) {
            val rejected = series.sumOf { it[RequestStats.REJECTED] }
            if (rejected > 0) Text(stringResource(R.string.api_an_rejected, rejected), Modifier.padding(top = 6.dp),
                color = ApiRed, fontSize = 12.sp)
        }
    }
}

/** Hero number tile: value in ink, label muted; red only when there are errors. */
@Composable private fun RowScope.Tile(value: String, label: String, detail: String? = null, alert: Boolean = false) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(StonePalette.Chip).padding(10.dp)) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp, color = if (alert) ApiRed else Color.Unspecified)
        Text(label, color = StonePalette.Secondary, fontSize = 11.sp, maxLines = 1)
        detail?.let { Text(it, color = StonePalette.Muted, fontSize = 10.sp, maxLines = 1) }
    }
}

/**
 * One series, one hue: thin bars with a 2 dp gap and rounded tops on a recessive baseline. A tap
 * shows that bar's time and counts; the description gives the totals to screen readers.
 */
@Composable private fun RequestBars(series: List<IntArray>, range: StatsRange, peak: Int, total: Int, modifier: Modifier) {
    var selected by remember(range) { mutableStateOf<Int?>(null) }
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val bar = StonePalette.Blue
    val grid = StonePalette.Muted.copy(alpha = .25f)
    val description = stringResource(R.string.api_an_chart_desc, total, peak)
    Column(modifier) {
        val pick = selected
        Text(if (pick != null) {
            val start = (System.currentTimeMillis() / 60_000 - (series.size - pick) * range.bucketMinutes + 1) * 60_000
            val counts = series[pick]
            stringResource(R.string.api_an_bar, clock.format(Date(start)), counts[0] + counts[1] + counts[2], counts[2])
        } else if (total == 0) stringResource(R.string.api_an_empty) else " ",
            color = StonePalette.Secondary, fontSize = 12.sp, maxLines = 1)
        val max = series.maxOfOrNull { it[0] + it[1] + it[2] }?.coerceAtLeast(1) ?: 1
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp).semantics { contentDescription = description }
            .pointerInput(series.size) {
                detectTapGestures { offset ->
                    val index = (offset.x / size.width * series.size).toInt().coerceIn(0, series.lastIndex)
                    selected = if (selected == index) null else index
                }
            }) {
            val slot = size.width / series.size
            val gap = 2.dp.toPx().coerceAtMost(slot / 2)
            val radius = CornerRadius(2.dp.toPx().coerceAtMost((slot - gap) / 2))
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            series.forEachIndexed { index, counts ->
                val value = counts[0] + counts[1] + counts[2]
                if (value == 0) return@forEachIndexed
                val height = (value.toFloat() / max * size.height).coerceAtLeast(2.dp.toPx())
                val color = if (selected == null || selected == index) bar else bar.copy(alpha = .35f)
                drawRoundRect(color, Offset(index * slot + gap / 2, size.height - height), Size(slot - gap, height), radius)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(stringResource(if (range == StatsRange.HOUR) R.string.api_range_hour else R.string.api_range_day),
                Modifier.weight(1f), color = StonePalette.Muted, fontSize = 10.sp)
            Text(clock.format(Date()), color = StonePalette.Muted, fontSize = 10.sp)
        }
    }
}
