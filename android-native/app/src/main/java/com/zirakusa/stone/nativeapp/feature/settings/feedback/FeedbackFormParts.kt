package com.zirakusa.stone.nativeapp.feature.settings.feedback

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.feature.settings.api.color
import com.zirakusa.stone.nativeapp.feature.settings.api.icon
import com.zirakusa.stone.nativeapp.feature.settings.api.label

/** Visual pieces of the feedback form; state lives in FeedbackForm. */
@Composable internal fun FeedbackHeader(kind: FeedbackKind) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(StonePalette.Yellow.copy(alpha = .18f)),
            contentAlignment = Alignment.Center) {
            Icon(kind.icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(stringResource(kind.label), fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.feedback_header_hint), color = StonePalette.Muted, fontSize = 14.sp)
        }
    }
}

/** Three equal cards (report, suggest, opinion); the chosen one is outlined in the accent. */
@Composable internal fun FeedbackKindCards(selected: FeedbackKind, onSelect: (FeedbackKind) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedbackKind.entries.forEach { kind ->
            val active = kind == selected
            val shape = RoundedCornerShape(18.dp)
            Row(Modifier.weight(1f).heightIn(min = 60.dp).clip(shape)
                .background(if (active) StonePalette.Yellow.copy(alpha = .12f) else StonePalette.Chip)
                .border(if (active) 2.dp else 1.dp, if (active) StonePalette.Yellow else StonePalette.Border, shape)
                .clickable { onSelect(kind) }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(kind.icon, null, tint = if (active) StonePalette.Yellow else StonePalette.Secondary,
                    modifier = Modifier.size(24.dp))
                Text(stringResource(kind.label), Modifier.padding(start = 6.dp), fontSize = 13.sp, lineHeight = 16.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 2)
            }
        }
    }
}

@Composable internal fun FeedbackSectionLabel(text: Int, icon: ImageVector? = null) {
    Row(Modifier.padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = StonePalette.Yellow, modifier = Modifier.size(22.dp).padding(end = 6.dp))
        Text(stringResource(text), color = StonePalette.Yellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Dashed "+ Agregar captura" button. */
@Composable internal fun DashedAddButton(onClick: () -> Unit) {
    val accent = StonePalette.Yellow
    Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .drawBehind {
            drawRoundRect(accent, cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AddCircleOutline, null, tint = accent)
        Text(stringResource(R.string.feedback_add_attachment), color = accent, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp))
    }
}

/** Screenshot/attachment previews with a remove button. */
@Composable internal fun AttachmentThumbnails(items: List<Uri>, remove: (Uri) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { uri ->
            Box(Modifier.size(width = 84.dp, height = 112.dp)) {
                StoneArtwork(uri.toString(), 0L, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                IconButton(onClick = { remove(uri) }, modifier = Modifier.align(Alignment.TopEnd)
                    .padding(4.dp).size(28.dp).background(Color(0xCC000000), CircleShape)) {
                    Icon(Icons.Default.Close, stringResource(R.string.feedback_remove_attachment),
                        tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Opt-in for version/Android/screen; the ⓘ explains that nothing is sent automatically. */
@Composable internal fun TechnicalInfoRow(checked: Boolean, onChange: (Boolean) -> Unit, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(R.string.feedback_include_technical), Modifier.weight(1f),
            color = StonePalette.Secondary, fontSize = 14.sp)
        IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, stringResource(R.string.feedback_review_before_share),
            tint = StonePalette.Muted) }
    }
}
