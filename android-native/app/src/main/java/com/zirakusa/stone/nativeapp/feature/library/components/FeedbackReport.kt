package com.zirakusa.stone.nativeapp.feature.library.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.nativeapp.BuildConfig
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.components.PenguinReportMascot
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

internal enum class FeedbackKind(val label: Int) {
    BUG(R.string.feedback_report_bug),
    FEATURE(R.string.feedback_suggest_feature),
    OPINION(R.string.feedback_send_opinion),
}

@Composable
internal fun FeedbackDetail(shakeToReport: Boolean, onShakeToReportChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.feedback_intro), color = StonePalette.Muted,
            modifier = Modifier.padding(bottom = 14.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.feedback_shake_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.feedback_shake_hint), color = StonePalette.Muted, fontSize = 13.sp)
            }
            Switch(shakeToReport, onShakeToReportChange)
        }
        FeedbackForm(initialKind = FeedbackKind.OPINION, screenLabel = "Ajustes > Opiniones y sugerencias",
            scrollable = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedbackReportSheet(initialKind: FeedbackKind = FeedbackKind.BUG,
    screenLabel: String, initialAttachments: List<Uri> = emptyList(), close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = StonePalette.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ImmersiveWindowEffect()
        FeedbackForm(initialKind, screenLabel, onShared = close, initialAttachments = initialAttachments,
            modifier = Modifier.fillMaxHeight(.92f).padding(horizontal = 24.dp))
    }
}

@Composable
private fun FeedbackForm(initialKind: FeedbackKind, screenLabel: String,
    modifier: Modifier = Modifier, onShared: () -> Unit = {}, scrollable: Boolean = true,
    initialAttachments: List<Uri> = emptyList()) {
    val context = LocalContext.current
    var kind by remember(initialKind) { mutableStateOf(initialKind) }
    var details by remember { mutableStateOf("") }
    var category by remember { mutableIntStateOf(R.string.feedback_other) }
    var includeTechnical by remember { mutableStateOf(false) }
    var attachments by remember(initialAttachments) { mutableStateOf(initialAttachments) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
        attachments = (attachments + selected).distinct().take(4)
    }
    val bugCategories = listOf(R.string.feedback_lyrics, R.string.feedback_ads,
        R.string.feedback_missing_file, R.string.feedback_unplayable,
        R.string.feedback_stops, R.string.feedback_slow, R.string.feedback_other)
    val formModifier = if (scrollable) {
        modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
    } else {
        modifier
    }
    Column(formModifier
        .padding(top = 16.dp, bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PenguinReportMascot(Modifier.size(58.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(stringResource(R.string.feedback_title), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.feedback_review_before_share), color = StonePalette.Muted,
                    fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackKind.entries.forEach { option ->
                FilterChip(selected = kind == option, onClick = { kind = option },
                    label = { Text(stringResource(option.label)) })
            }
        }
        if (kind == FeedbackKind.BUG) {
            Text(stringResource(R.string.feedback_category), fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bugCategories.forEach { option ->
                    FilterChip(selected = category == option, onClick = { category = option },
                        label = { Text(stringResource(option)) })
                }
            }
        }
        Text(stringResource(R.string.feedback_details), fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (attachments.isNotEmpty()) AttachmentThumbnails(attachments) { attachments = attachments - it }
        OutlinedTextField(value = details, onValueChange = { details = it.take(MAX_DETAILS) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 5,
            supportingText = { Text("${details.length} / $MAX_DETAILS") },
            placeholder = { Text(stringResource(R.string.feedback_details_hint)) })
        Text(stringResource(R.string.feedback_attachments), fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        OutlinedButton(onClick = { picker.launch("image/*") }) {
            Icon(Icons.Default.AttachFile, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.feedback_add_attachment))
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = includeTechnical, onCheckedChange = { includeTechnical = it })
            Text(stringResource(R.string.feedback_include_technical), color = StonePalette.Muted,
                fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Button(onClick = {
            val sent = shareFeedback(context, kind, category, details, attachments,
                includeTechnical, screenLabel)
            if (!sent) Toast.makeText(context, R.string.feedback_no_share_app,
                Toast.LENGTH_LONG).show() else onShared()
        }, enabled = details.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(stringResource(R.string.feedback_share))
        }
    }
}

private const val MAX_DETAILS = 2000

/** Screenshot/attachment previews with a remove button, shown above the description. */
@Composable private fun AttachmentThumbnails(items: List<Uri>, remove: (Uri) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState())
        .padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { uri ->
            Box(Modifier.size(width = 84.dp, height = 112.dp)) {
                com.zirakusa.stone.nativeapp.ui.components.StoneArtwork(uri.toString(), 0L,
                    Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)))
                IconButton(onClick = { remove(uri) }, modifier = Modifier.align(Alignment.TopEnd)
                    .padding(4.dp).size(28.dp).background(androidx.compose.ui.graphics.Color(0xCC000000), CircleShape)) {
                    Icon(Icons.Default.Close, stringResource(R.string.feedback_remove_attachment),
                        tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun shareFeedback(context: Context, kind: FeedbackKind, category: Int, details: String,
    attachments: List<Uri>, includeTechnical: Boolean, screenLabel: String): Boolean {
    val body = buildString {
        append(context.getString(R.string.feedback_type_line, context.getString(kind.label)))
        if (kind == FeedbackKind.BUG) append('\n').append(
            context.getString(R.string.feedback_category_line, context.getString(category)))
        append("\n\n").append(details.trim())
        if (includeTechnical) {
            append("\n\n").append(context.getString(R.string.feedback_technical_header))
            append('\n').append(context.getString(R.string.feedback_app_version, BuildConfig.VERSION_NAME))
            append('\n').append(context.getString(R.string.feedback_android_version,
                Build.VERSION.RELEASE ?: "?", Build.VERSION.SDK_INT))
            append('\n').append(context.getString(R.string.feedback_current_screen, screenLabel))
        }
    }
    val intent = Intent(if (attachments.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
        .setType(if (attachments.isEmpty()) "text/plain" else "*/*")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
        .putExtra(Intent.EXTRA_TEXT, body)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (attachments.size == 1) intent.putExtra(Intent.EXTRA_STREAM, attachments.single())
    if (attachments.size > 1) intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
    if (attachments.isNotEmpty()) {
        val clip = ClipData.newRawUri("Stone", attachments.first())
        attachments.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
    }
    return runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.feedback_share_title)))
    }.isSuccess
}
