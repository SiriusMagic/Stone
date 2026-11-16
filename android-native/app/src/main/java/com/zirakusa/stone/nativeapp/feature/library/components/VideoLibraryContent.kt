package com.zirakusa.stone.nativeapp.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.stone.core.Song
import com.zirakusa.stone.nativeapp.R
import com.zirakusa.stone.nativeapp.feature.library.LibraryState
import com.zirakusa.stone.nativeapp.ui.components.*
import com.zirakusa.stone.nativeapp.ui.theme.StonePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VideoLibraryContent(videos: List<Song>, state: LibraryState, currentId: String, modifier: Modifier,
    onPlay: (Song, List<Song>) -> Unit, onFolder: () -> Unit,
    compact: Boolean, videoLibraryLayout: VideoLibraryLayout,
    onVideoLibraryLayoutChange: (VideoLibraryLayout) -> Unit,
    onVideoOptions: (Song) -> Unit, onDelete: (Song) -> Unit, onTagsSaved: () -> Unit) {
    val selection = remember { VideoSelectionState() }
    var folderBrowser by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val visible = selectedFolder?.let { folder -> videos.filter { it.folder == folder } } ?: videos
    val effectiveLayout = if (compact) VideoLibraryLayout.GRID else videoLibraryLayout
    VideoSelectionDialogs(selection, visible, onDelete, onTagsSaved)
    Column(modifier.fillMaxWidth()) {
        if (selection.active) Box(Modifier.padding(horizontal = 12.dp)) {
            SelectionHeader(selection.ids.size, visible.size, onExit = selection::exit, onToggleAll = {
                selection.ids = if (selection.ids.size == visible.size) emptySet() else visible.map { it.id }.toSet()
            })
        } else Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selectedFolder != null) IconButton(onClick = { selectedFolder = null; folderBrowser = true }) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
            }
            Text(if (selectedFolder != null) selectedFolder.orEmpty()
                else pluralStringResource(R.plurals.video_count,
                    videos.size, videos.size), Modifier.weight(1f),
                color = StonePalette.SongSubtitle, fontSize = 15.sp)
            IconButton(onClick = onFolder, enabled = !state.scanning) {
                Icon(Icons.Default.CreateNewFolder, stringResource(R.string.choose_video_folder))
            }
            IconButton(onClick = {
                folderBrowser = !folderBrowser
                if (!folderBrowser) selectedFolder = null
            }) { Icon(if (folderBrowser) Icons.Default.VideoLibrary else Icons.Default.Folder,
                stringResource(R.string.video_folders)) }
            if (!compact && selectedFolder == null) LayoutSelector(videoLibraryLayout, onVideoLibraryLayoutChange,
                Modifier.testTag("video-layout-toggle"))
        }
        when {
            folderBrowser -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp)) {
                items(videos.groupBy { it.folder }.toList(), key = { it.first }) { (folder, items) ->
                    Row(Modifier.fillMaxWidth().clickable { selectedFolder = folder; folderBrowser = false }
                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = StonePalette.Yellow, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(folder, fontWeight = FontWeight.SemiBold)
                            Text(pluralStringResource(R.plurals.video_count, items.size, items.size), color = StonePalette.SongSubtitle,
                                fontSize = 13.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = StonePalette.Muted)
                    }
                }
            }
            visible.isNotEmpty() && effectiveLayout == VideoLibraryLayout.GRID ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("video-grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                        top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visible, key = { it.id }) { video ->
                        val edit: (() -> Unit)? = if (selection.editable(video)) { { selection.editing = video } } else null
                        VideoGridCard(video, selected = video.id == currentId,
                            play = { selection.click(video) { onPlay(video, visible) } },
                            onOptions = { onVideoOptions(video) }, checked = selection.checked(video),
                            onLongClick = { if (!selection.active) selection.start(video) },
                            onEdit = edit)
                    }
                }
            visible.isNotEmpty() ->
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("video-list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    items(visible, key = { it.id }) { video ->
                        VideoListRow(video, selected = video.id == currentId,
                            play = { selection.click(video) { onPlay(video, visible) } },
                            onOptions = { onVideoOptions(video) }, checked = selection.checked(video),
                            onLongClick = { if (!selection.active) selection.start(video) },
                            onEdit = if (selection.editable(video)) { { selection.editing = video } } else null,
                            dense = effectiveLayout == VideoLibraryLayout.COMPACT)
                    }
                }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                item {
                    if (!state.scanning) LibraryEmptyState(videos = true)
                }
            }
        }
        VideoSelectionBar(selection, visible)
    }
}
