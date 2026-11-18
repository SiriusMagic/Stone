package com.zirakusa.stone.nativeapp.feature.settings.feedback

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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
import com.zirakusa.stone.nativeapp.ui.system.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette
import com.zirakusa.stone.nativeapp.feature.settings.api.color
import com.zirakusa.stone.nativeapp.feature.settings.api.icon
import com.zirakusa.stone.nativeapp.feature.settings.api.label

internal enum class FeedbackKind(val label: Int, val icon: ImageVector, val send: Int) {
    BUG(R.string.feedback_report_bug, Icons.Outlined.BugReport, R.string.feedback_send_report),
    FEATURE(R.string.feedback_suggest_feature, Icons.Outlined.Lightbulb, R.string.feedback_send_suggestion),
    OPINION(R.string.feedback_send_opinion, Icons.Outlined.Chat, R.string.feedback_send_opinion),
}

@Composable
internal fun FeedbackDetail(shakeToReport: Boolean, onShakeToReportChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.feedback_shake_title), color = StonePalette.Yellow,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.feedback_shake_hint), color = StonePalette.Muted, fontSize = 14.sp)
            }
            Switch(shakeToReport, onShakeToReportChange)
        }
        HorizontalDivider(color = StonePalette.Border)
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
    Column(formModifier.padding(top = 16.dp, bottom = 24.dp)) {
        FeedbackHeader(kind)
        FeedbackKindCards(kind) { kind = it }
        if (kind == FeedbackKind.BUG) {
            FeedbackSectionLabel(R.string.feedback_category)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bugCategories.forEach { option ->
                    FilterChip(selected = category == option, onClick = { category = option },
                        label = { Text(stringResource(option)) })
                }
            }
        }
        FeedbackSectionLabel(R.string.feedback_details)
        OutlinedTextField(value = details, onValueChange = { details = it.take(MAX_DETAILS) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), minLines = 5,
            shape = RoundedCornerShape(18.dp),
            placeholder = { Text(stringResource(R.string.feedback_details_hint)) })
        Text("${details.length} / $MAX_DETAILS", Modifier.fillMaxWidth().padding(top = 6.dp),
            color = StonePalette.Muted, fontSize = 13.sp, textAlign = TextAlign.End)
        FeedbackSectionLabel(R.string.feedback_attachments, Icons.Outlined.Image)
        if (attachments.isNotEmpty()) AttachmentThumbnails(attachments) { attachments = attachments - it }
        if (attachments.size < 4) DashedAddButton { picker.launch("image/*") }
        TechnicalInfoRow(includeTechnical, { includeTechnical = it }) {
            Toast.makeText(context, R.string.feedback_review_before_share, Toast.LENGTH_LONG).show()
        }
        Button(onClick = {
            val sent = shareFeedback(context, kind, category, details, attachments,
                includeTechnical, screenLabel)
            if (!sent) Toast.makeText(context, R.string.feedback_no_share_app,
                Toast.LENGTH_LONG).show() else onShared()
        }, enabled = details.isNotBlank(), shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(top = 4.dp)) {
            Icon(Icons.AutoMirrored.Outlined.Send, null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(kind.send), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

private const val MAX_DETAILS = 2000

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
