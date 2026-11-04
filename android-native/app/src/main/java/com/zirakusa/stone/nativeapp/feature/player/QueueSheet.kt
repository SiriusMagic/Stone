package com.zirakusa.stone.nativeapp.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.media3.common.Player
import com.zirakusa.stone.nativeapp.playback.PlaybackState
import com.zirakusa.stone.nativeapp.playback.PlaybackViewModel
import com.zirakusa.stone.nativeapp.ui.components.StoneArtwork
import com.zirakusa.stone.nativeapp.data.library.ArtworkLoader
import com.zirakusa.stone.nativeapp.R
import kotlinx.coroutines.launch

private val QueueBrown = Color(0xFF5A3436)
private val QueueDark = Color(0xFF4A292B)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun QueueSheet(state: PlaybackState, playback: PlaybackViewModel, onClose: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = QueueBrown,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF7A5759)) }) {
        Column(Modifier.fillMaxHeight(0.78f).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.queue), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = { if (state.index >= 0) scope.launch { listState.animateScrollToItem(state.index) } }) {
                    Icon(Icons.Default.MyLocation, stringResource(R.string.go_to_current_song))
                }
                IconButton(onClick = { editing = !editing }) {
                    Icon(if (editing) Icons.Default.Check else Icons.Default.Brush,
                        stringResource(if (editing) R.string.done else R.string.edit_queue))
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.dismiss)) }
            }
            LazyColumn(Modifier.weight(1f), state = listState) {
                itemsIndexed(state.queue, key = { _, item -> item.id }) { index, item ->
                    val isDragging = draggingId == item.id
                    Row(Modifier.fillMaxWidth().height(66.dp)
                        .animateItem(placementSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffset
                                scaleX = 1.025f
                                scaleY = 1.025f
                                shadowElevation = 8.dp.toPx()
                            }
                        }
                        .background(if (index == state.index) Color(0x1FFFFFFF) else Color.Transparent,
                            RoundedCornerShape(14.dp))
                        .clickable { playback.playAt(index) }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        QueueArtwork(item.uri, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold)
                            Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = Color(0xFFD0BFC0), fontSize = 13.sp)
                        }
                        if (editing) {
                            IconButton(onClick = { playback.removeAt(index) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete))
                            }
                            Icon(Icons.Default.DragHandle, stringResource(R.string.hold_to_reorder),
                            Modifier.size(38.dp).pointerInput(item.id, state.queue.size, editing) {
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
            // Intentionally outside LazyColumn: these controls never scroll away.
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QueueModeButton(state.repeat == Player.REPEAT_MODE_ONE, Modifier.weight(1f), {
                    playback.setRepeat(if (state.repeat == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE)
                }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Repeat, stringResource(R.string.repeat_one))
                        if (state.repeat == Player.REPEAT_MODE_ONE) Text("1", fontSize = 10.sp,
                            fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
                    }
                }
                QueueModeButton(state.repeat == Player.REPEAT_MODE_ALL, Modifier.weight(1f), {
                    playback.setRepeat(if (state.repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL)
                }) { Icon(Icons.Default.Repeat, stringResource(R.string.repeat_all)) }
                QueueModeButton(state.shuffle, Modifier.weight(1f), { playback.setShuffle(!state.shuffle) }) {
                    Icon(Icons.Default.Shuffle, stringResource(R.string.shuffle))
                }
            }
        }
    }
}

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

@Composable private fun QueueModeButton(active: Boolean, modifier: Modifier, onClick: () -> Unit,
    content: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(24.dp),
        color = if (active) Color(0xFFE8E6EE) else QueueDark,
        contentColor = if (active) QueueDark else Color(0xFFE8E6EE)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
