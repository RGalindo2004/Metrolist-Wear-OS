package com.metrolist.music.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Dialog
import coil3.compose.AsyncImage
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.media.ui.components.PlayPauseButton
import com.google.android.horologist.media.ui.components.controls.SeekToNextButton
import com.google.android.horologist.media.ui.components.controls.SeekToPreviousButton
import com.metrolist.music.LocalBatterySaverMode
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.core.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.viewmodels.WearHomeViewModel
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.ui.LocalWearSongMenuState
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.resize
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.AccountNameKey
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

/**
 * Wear OS Music Player for Metrolist.
 * Optimized for circular screens, crown support, and Horologist controls.
 */
@OptIn(ExperimentalWearFoundationApi::class, ExperimentalHorologistApi::class)
@Composable
fun WearMusicPlayer(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToVolume: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToHomeSection: (String) -> Unit
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val downloadUtil = LocalDownloadUtil.current
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    
    // Download state for the options page
    val songId = mediaMetadata?.id
    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val download = remember(songId, allDownloads) { songId?.let { allDownloads[it] } }
    
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> QueueScreen(playerConnection)
            1 -> NowPlayingScreen(
                metadata = mediaMetadata,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                onNavigateToVolume = onNavigateToVolume
            )
            2 -> WearOptionsPage(
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToLiked = onNavigateToLiked,
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToQueue = onNavigateToQueue,
                onNavigateToHomeSection = onNavigateToHomeSection,
                metadata = mediaMetadata,
                currentSong = currentSong,
                download = download
            )
        }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun NowPlayingScreen(
    metadata: MediaMetadata?,
    isPlaying: Boolean,
    playerConnection: com.metrolist.music.playback.PlayerConnection,
    onNavigateToVolume: () -> Unit
) {
    val batterySaver = LocalBatterySaverMode.current
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val isLiked = currentSong?.song?.liked == true
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val player = playerConnection.player
    
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying, metadata) {
        while (isActive) {
            if (isPlaying) {
                currentPosition = player.currentPosition
                duration = if (player.duration > 0) player.duration else 0L
            }
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent {
                if (it.verticalScrollPixels > 0) {
                    playerConnection.seekToNext()
                } else if (it.verticalScrollPixels < 0) {
                    playerConnection.seekToPrevious()
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // Background Artwork with Blur/Fade
        if (!batterySaver && metadata != null) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.2f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )
        }

        // Circular Progress Indicator (Subtle background)
        if (duration > 0) {
            CircularProgressIndicator(
                progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 2.dp,
                indicatorColor = MaterialTheme.colors.primary.copy(alpha = 0.7f),
                trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
            )
        }

        // Middle Section: Controls (Geometric Center)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            modifier = Modifier.wrapContentSize()
        ) {
            SeekToPreviousButton(
                onClick = { playerConnection.seekToPrevious() },
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
            )

            PlayPauseButton(
                onPlayClick = { playerConnection.play() },
                onPauseClick = { playerConnection.pause() },
                playing = isPlaying,
                modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
            )

            SeekToNextButton(
                onClick = { playerConnection.seekToNext() },
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize)
            )
        }

        // Top Section: Metadata
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 26.dp)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = metadata?.title ?: stringResource(R.string.untitled),
                style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Text(
                text = metadata?.artists?.joinToString { it.name } ?: "",
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Bottom Section: Utility Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like Button
            Button(
                onClick = { playerConnection.toggleLike() },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Icon(
                    painter = painterResource(if (isLiked) R.drawable.ic_heart else R.drawable.ic_heart_outline),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isLiked) Color.Red else Color.White
                )
            }

            // Volume Button
            Button(
                onClick = onNavigateToVolume,
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Icon(
                    painter = painterResource(R.drawable.volume_up),
                    contentDescription = stringResource(R.string.volume),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Sleep Button
            Button(
                onClick = { showSleepTimerDialog = true },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Icon(
                    painter = painterResource(R.drawable.timer),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showSleepTimerDialog) {
        Dialog(
            showDialog = showSleepTimerDialog,
            onDismissRequest = { showSleepTimerDialog = false }
        ) {
            val columnState = rememberResponsiveColumnState()
            ScalingLazyColumn(columnState = columnState) {
                item { ListHeader { Text(stringResource(R.string.sleep_timer)) } }
                items(listOf(5, 10, 15, 30, 45, 60)) { mins ->
                    Chip(
                        onClick = {
                            playerConnection.service.sleepTimer?.start(mins)
                            showSleepTimerDialog = false
                        },
                        label = { Text("$mins min") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Chip(
                        onClick = {
                            playerConnection.service.sleepTimer?.clear()
                            showSleepTimerDialog = false
                        },
                        label = { Text(stringResource(R.string.reset)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearOptionsPage(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToHomeSection: (String) -> Unit,
    metadata: MediaMetadata?,
    currentSong: com.metrolist.music.db.entities.Song?,
    download: Download?
) {
    val context = LocalContext.current
    val columnState = rememberResponsiveColumnState()
    val viewModel: WearHomeViewModel = hiltViewModel()
    
    val accountName by remember(context) {
        context.dataStore.data.map { it[AccountNameKey] }
    }.collectAsStateWithLifecycle(initialValue = null)

    val isDownloaded = currentSong?.song?.isDownloaded == true || download?.state == Download.STATE_COMPLETED
    val isDownloading = download?.state == Download.STATE_DOWNLOADING || download?.state == Download.STATE_QUEUED
    val downloadPercent = download?.percentDownloaded ?: 0f

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ListHeader {
                Text(stringResource(R.string.options))
            }
        }

        item {
            Chip(
                onClick = onNavigateToSearch,
                label = { Text(stringResource(R.string.search)) },
                icon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Contextual Download Option
        if (metadata != null) {
            item {
                Chip(
                    onClick = {
                        val songId = metadata.id
                        if (isDownloaded || isDownloading) {
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                songId,
                                false
                            )
                        } else {
                            val downloadRequest = DownloadRequest.Builder(songId, songId.toUri())
                                .setCustomCacheKey(songId)
                                .setData(metadata.title.toByteArray())
                                .build()
                            DownloadService.sendAddDownload(
                                context,
                                ExoDownloadService::class.java,
                                downloadRequest,
                                true
                            )
                        }
                    },
                    label = { 
                        Text(
                            text = when {
                                isDownloaded -> stringResource(R.string.remove_download)
                                isDownloading -> stringResource(R.string.downloading_progress, (downloadPercent).toInt(), 100)
                                else -> stringResource(R.string.action_download)
                            }
                        )
                    },
                    icon = {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                progress = downloadPercent / 100f,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(if (isDownloaded) R.drawable.offline else R.drawable.download),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Chip(
                onClick = onNavigateToLibrary,
                label = { Text(stringResource(R.string.filter_library)) },
                icon = { Icon(painterResource(R.drawable.library_music), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // New Home Sections
        item { ListHeader { Text(stringResource(R.string.home)) } }
        
        item {
            Chip(
                onClick = { onNavigateToHomeSection("quick_picks") },
                label = { Text(stringResource(R.string.quick_picks)) },
                icon = { Icon(painterResource(R.drawable.grid_view), null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Chip(
                onClick = { onNavigateToHomeSection("keep_listening") },
                label = { Text(stringResource(R.string.keep_listening)) },
                icon = { Icon(painterResource(R.drawable.history), null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        if (accountName != null) {
            item {
                Chip(
                    onClick = { onNavigateToHomeSection("for_you") },
                    label = { Text(stringResource(R.string.for_you)) },
                    icon = { Icon(painterResource(R.drawable.ic_heart), null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                Chip(
                    onClick = { onNavigateToHomeSection("listen_again") },
                    label = { Text(stringResource(R.string.listen_again)) },
                    icon = { Icon(painterResource(R.drawable.sync), null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Chip(
                onClick = onNavigateToSettings,
                label = { Text(stringResource(R.string.settings)) },
                icon = { Icon(painterResource(R.drawable.settings), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalWearFoundationApi::class, ExperimentalHorologistApi::class, ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(playerConnection: com.metrolist.music.playback.PlayerConnection) {
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle(emptyList())
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
    ) {
        item {
            ListHeader {
                Text(stringResource(R.string.playback_queue))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                    colors = if (shuffleModeEnabled)
                        ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary)
                    else
                        ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Icon(
                        painter = painterResource(if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle)
                    )
                }
                Button(
                    onClick = {
                        playerConnection.mediaMetadata.value?.let { metadata ->
                            playerConnection.playQueue(com.metrolist.music.playback.queues.YouTubeQueue.radio(metadata))
                        }
                    },
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.radio),
                        contentDescription = stringResource(R.string.start_radio)
                    )
                }
            }
        }
        
        itemsIndexed(queueWindows) { index, window ->
            val metadata = window.mediaItem.metadata
            val isCurrent = index == currentWindowIndex
            
            Chip(
                onClick = { playerConnection.player.seekTo(index, 0) },
                label = { 
                    Text(
                        text = metadata?.title ?: stringResource(R.string.untitled),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    ) 
                },
                secondaryLabel = {
                    val nowPlayingPrefix = if (isCurrent) "${stringResource(R.string.now_playing)} • " else ""
                    Text(
                        text = nowPlayingPrefix + 
                               (metadata?.artists?.joinToString { it.name } ?: stringResource(R.string.widget_recognizer_unknown_artist)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent) MaterialTheme.colors.primary else MaterialTheme.colors.secondary
                    )
                },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(ChipDefaults.IconSize)
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            model = metadata?.thumbnailUrl?.resize(100, 100),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.5f
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = {
                                        metadata?.let { menuState.show(it) }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = "Menu",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = if (isCurrent) 
                    ChipDefaults.gradientBackgroundChipColors()
                else
                    ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item {
            Spacer(Modifier.height(40.dp))
        }
    }

    LaunchedEffect(currentWindowIndex) {
        if (currentWindowIndex >= 0) {
            // Scroll to the current playing item
            columnState.state.scrollToItem(currentWindowIndex + 1)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
