/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Dialog
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.core.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.LocalWearSongMenuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearSongMenu() {
    val state = LocalWearSongMenuState.current
    val metadata = state.metadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    
    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val download = allDownloads[metadata.id]
    
    val currentSong by database.song(metadata.id).collectAsStateWithLifecycle(initialValue = null)
    val isLiked = currentSong?.song?.liked == true
    
    val isDownloaded = download?.state == Download.STATE_COMPLETED
    val isDownloading = download?.state == Download.STATE_DOWNLOADING || download?.state == Download.STATE_QUEUED

    var showPlaylistSelection by remember(state.isVisible) { mutableStateOf(false) }

    Dialog(
        showDialog = state.isVisible,
        onDismissRequest = { state.dismiss() }
    ) {
        if (showPlaylistSelection) {
            val playlists by produceState(initialValue = emptyList<com.metrolist.music.db.entities.PlaylistEntity>()) {
                value = database.playlistEntitiesByNameAsc()
            }
            val columnState = rememberResponsiveColumnState()
            
            ScalingLazyColumn(
                columnState = columnState,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    ListHeader {
                        Text(stringResource(R.string.choose_playlist))
                    }
                }
                items(playlists) { playlist ->
                    Chip(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                playlist.browseId?.let { browseId ->
                                    YouTube.addToPlaylist(browseId, metadata.id)
                                    scope.launch(Dispatchers.Main) {
                                        Toast.makeText(context, R.string.added_to_playlist, Toast.LENGTH_SHORT).show()
                                        state.dismiss()
                                    }
                                }
                            }
                        },
                        label = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(painterResource(R.drawable.playlist_play), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            val columnState = rememberResponsiveColumnState()
            ScalingLazyColumn(
                columnState = columnState,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = metadata.title,
                            style = MaterialTheme.typography.title3,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = metadata.artists.joinToString { it.name },
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.secondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Like
                item {
                    Chip(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val song = currentSong?.song ?: metadata.toSongEntity()
                                val updatedSong = song.toggleLike(syncToYouTube = false)
                                database.transaction {
                                    upsert(updatedSong)
                                }
                                syncUtils.likeSong(updatedSong)
                            }
                            state.dismiss()
                        },
                        label = { Text(if (isLiked) stringResource(R.string.action_remove_like) else stringResource(R.string.action_like)) },
                        icon = { 
                            Icon(
                                painter = painterResource(if (isLiked) R.drawable.ic_heart else R.drawable.ic_heart_outline),
                                contentDescription = null,
                                tint = if (isLiked) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.White
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Download
                item {
                    Chip(
                        onClick = {
                            if (isDownloaded || isDownloading) {
                                DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, metadata.id, false)
                            } else {
                                database.transaction {
                                    insert(metadata.toSongEntity())
                                }
                                val downloadRequest = DownloadRequest.Builder(metadata.id, metadata.id.toUri())
                                    .setCustomCacheKey(metadata.id)
                                    .setData(metadata.title.toByteArray())
                                    .build()
                                DownloadService.sendAddDownload(context, ExoDownloadService::class.java, downloadRequest, false)
                            }
                            state.dismiss()
                        },
                        label = {
                            Text(
                                text = when {
                                    isDownloaded -> stringResource(R.string.remove_download)
                                    isDownloading -> stringResource(R.string.downloading)
                                    else -> stringResource(R.string.action_download)
                                }
                            )
                        },
                        icon = {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
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

                // Start radio
                item {
                    Chip(
                        onClick = {
                            playerConnection?.playQueue(YouTubeQueue.radio(metadata))
                            state.dismiss()
                        },
                        label = { Text(stringResource(R.string.start_radio)) },
                        icon = { Icon(painterResource(R.drawable.radio), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Play next (Labeled as Add to Queue per user request)
                item {
                    Chip(
                        onClick = {
                            playerConnection?.playNext(metadata.toMediaItem())
                            Toast.makeText(context, R.string.play_next, Toast.LENGTH_SHORT).show()
                            state.dismiss()
                        },
                        label = { Text(stringResource(R.string.add_to_queue)) },
                        icon = { Icon(painterResource(R.drawable.queue_music), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Add to playlist
                item {
                    Chip(
                        onClick = {
                            showPlaylistSelection = true
                        },
                        label = { Text(stringResource(R.string.add_to_playlist)) },
                        icon = { Icon(painterResource(R.drawable.playlist_add), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
