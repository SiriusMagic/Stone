package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.media3.common.Player
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.ui.ImmersiveWindowEffect
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.data.library.LyricsIndex
import androidx.compose.foundation.border
import com.zirakusa.stone.nativeapp.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val QueueBrown = Color(0xFF5A3436)
private val QueueDark = Color(0xFF4A292B)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun QueueSheet(state: PlaybackState, playback: PlaybackViewModel, tint: Color = QueueBrown,
    onClose: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var clearDialog by remember { mutableStateOf(false) }
    val swipeState = remember { mutableStateMapOf<String, Float>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Light artwork gives a light sheet: switch text and icons to dark so they stay readable.
    val light = tint.luminance() > .5f
    val ink = if (light) Color(0xFF1E1A1C) else Color.White
    val subInk = ink.copy(alpha = .72f)
    val repeatOne = state.repeat == Player.REPEAT_MODE_ONE
    val current = state.queue.getOrNull(state.index)
    ModalBottomSheet(onDismissRequest = onClose, containerColor = tint,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF7A5759)) }) {
        ImmersiveWindowEffect()
        CompositionLocalProvider(LocalContentColor provides ink) {
        Column(Modifier.fillMaxHeight(0.78f).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (repeatOne) R.string.play_mode_one else R.string.queue), Modifier.weight(1f),
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = { if (state.index >= 0) scope.launch { listState.animateScrollToItem(state.index) } }) {
                    Icon(Icons.Default.MyLocation, stringResource(R.string.go_to_current_song))
                }
                IconButton(onClick = { clearDialog = true }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.clear_queue))
                }
            }
            // Repeat one: the looping song stays pinned on top and the rest of the queue is dimmed.
            if (repeatOne && current != null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    QueueArtwork(current.uri, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(current.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (LyricsIndex.has(current.id)) LyricsBadge(subInk)
                            Text(subtitle(current), maxLines = 1, overflow = TextOverflow.Ellipsis, color = subInk, fontSize = 13.sp)
                        }
                    }
                }
                HorizontalDivider(color = ink.copy(alpha = .15f))
            }
            LazyColumn(Modifier.weight(1f).graphicsLayer { alpha = if (repeatOne) .45f else 1f }, state = listState) {
                itemsIndexed(state.queue, key = { _, item -> item.id }) { index, item ->
                    val isDragging = draggingId == item.id
                    Box(Modifier.fillMaxWidth().height(66.dp)
                        .animateItem(placementSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffset
                                scaleX = 1.025f
                                scaleY = 1.025f
                                shadowElevation = 8.dp.toPx()
                            }
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(item.id, state.queue.size) {
                            var swipeOffset = 0f
                            detectHorizontalDragGestures(onHorizontalDrag = { change, amount ->
                                if (amount < 0f || swipeOffset < 0f) {
                                    change.consume()
                                    swipeOffset = (swipeOffset + amount).coerceIn(-size.width.toFloat(), 0f)
                                    swipeState[item.id] = swipeOffset
                                }
                            }, onDragEnd = {
                                val offset = swipeState[item.id] ?: 0f
                                if (offset <= -size.width * .5f) {
                                    playback.removeAt(index)
                                }
                                swipeState.remove(item.id)
                            }, onDragCancel = {
                                swipeState.remove(item.id)
                            })
                        }
                        .background(Color(0xFFE94B43))) {
                        Icon(Icons.Default.RemoveCircleOutline, stringResource(R.string.delete),
                            tint = Color.White, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 26.dp))
                    val swipeOffset = swipeState[item.id] ?: 0f
                    Row(Modifier.fillMaxSize().offset { IntOffset(swipeOffset.roundToInt(), 0) }
                        .background(if (index == state.index) Color.White.copy(alpha = .08f).compositeOver(tint) else tint,
                            RoundedCornerShape(14.dp))
                        .clickable { if (swipeOffset != 0f) swipeState.remove(item.id) else playback.playAt(index) }
                        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        QueueArtwork(item.uri, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (LyricsIndex.has(item.id)) LyricsBadge(subInk)
                                Text(subtitle(item), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = subInk, fontSize = 13.sp)
                            }
                        }
                        Icon(Icons.Default.DragHandle, stringResource(R.string.hold_to_reorder),
                            Modifier.size(38.dp).pointerInput(item.id, state.queue.size) {
                                var movingIndex = index
                                val step = 66.dp.toPx()
                                detectDragGestures(onDragStart = {
                                    draggingId = item.id
                                    dragOffset = 0f
                                }, onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                }, onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                }) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    while (dragOffset >= step && movingIndex < state.queue.lastIndex) {
                                        playback.move(movingIndex, movingIndex + 1)
                                        movingIndex++
                                        dragOffset -= step
                                    }
                                    while (dragOffset <= -step && movingIndex > 0) {
                                        playback.move(movingIndex, movingIndex - 1)
                                        movingIndex--
                                        dragOffset += step
                                    }
                                }
                            })
                    }
                    }
                }
            }
            // Intentionally outside LazyColumn: these controls never scroll away. One mode at a time.
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QueueModeButton(repeatOne, tint, Modifier.weight(1f), {
                    playback.setShuffle(false); playback.setRepeat(Player.REPEAT_MODE_ONE)
                }) { Icon(Icons.Default.RepeatOne, stringResource(R.string.play_mode_one)) }
                QueueModeButton(!repeatOne && !state.shuffle && state.repeat == Player.REPEAT_MODE_ALL, tint, Modifier.weight(1f), {
                    playback.setShuffle(false); playback.setRepeat(Player.REPEAT_MODE_ALL)
                }) { Icon(Icons.Default.Repeat, stringResource(R.string.play_mode_all)) }
                QueueModeButton(state.shuffle && !repeatOne, tint, Modifier.weight(1f), {
                    playback.setRepeat(Player.REPEAT_MODE_ALL); playback.setShuffle(true)
                }) { Icon(Icons.Default.Shuffle, stringResource(R.string.play_mode_shuffle)) }
            }
            if (clearDialog) AlertDialog(
                onDismissRequest = { clearDialog = false },
                title = { ImmersiveWindowEffect(); Text(stringResource(R.string.clear_queue_title)) },
                text = { Text(stringResource(R.string.clear_queue_message)) },
                confirmButton = {
                    TextButton(onClick = { playback.clearQueue(); clearDialog = false }) {
                        Text(stringResource(R.string.clear_queue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clearDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                })
        }
        }
    }
}

/** Outlined "LETRAS" label in front of the subtitle of songs that have lyrics. */
@Composable private fun LyricsBadge(color: Color) {
    Text(stringResource(R.string.lyrics_badge), Modifier.padding(end = 8.dp).border(1.dp, color, RoundedCornerShape(14.dp))
        .padding(horizontal = 7.dp, vertical = 1.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
}

private fun subtitle(item: com.zirakusa.stone.nativeapp.playback.QueueEntry) =
    listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" - ")

@Composable private fun QueueArtwork(uri: String?, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val isVideo = uri?.substringBefore('?')?.substringAfterLast('.')?.lowercase() in setOf("mp4", "mkv", "mov", "webm")
    var frame by remember(uri) { mutableStateOf<ArtworkLoader.VideoFrame?>(null) }
    LaunchedEffect(uri, isVideo) {
        frame = if (isVideo && uri != null) ArtworkLoader.loadVideoFrame(context, uri, 0L) else null
    }
    if (frame?.bitmap != null) Image(frame!!.bitmap!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else StoneArtwork(uri, 0L, modifier)
}

@Composable private fun QueueModeButton(active: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit,
    content: @Composable () -> Unit) {
    // Selected: a pale (or, on light sheets, dark) pill; the others blend with the sheet colour.
    val light = tint.luminance() > .5f
    val selected = if (light) Color(0xFF1E1A1C) else Color.White.copy(alpha = .85f).compositeOver(tint)
    val idle = (if (light) Color.Black.copy(alpha = .06f) else Color.Black.copy(alpha = .14f)).compositeOver(tint)
    val idleInk = if (light) Color(0xFF1E1A1C) else Color.White
    Surface(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(24.dp),
        color = if (active) selected else idle,
        contentColor = if (active) (if (light) Color.White else Color(0xFF1E1A1C)) else idleInk) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
