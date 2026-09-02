/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.core.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_SONG
import com.metrolist.music.constants.PodcastFilter
import com.metrolist.music.constants.PodcastFilterKey
import com.metrolist.music.constants.SongSortTypeKey
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.db.entities.PodcastEntity
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.viewmodels.LibraryPodcastsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryPodcastsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryPodcastsViewModel = hiltViewModel(),
) {
    val downloadedEpisodesStr = stringResource(R.string.downloaded_episodes)
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    var podcastFilter by rememberEnumPreference(PodcastFilterKey, PodcastFilter.EPISODES)

    val subscribedChannels by viewModel.subscribedChannels.collectAsStateWithLifecycle()
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsStateWithLifecycle()
    val savedEpisodes by viewModel.savedEpisodes.collectAsStateWithLifecycle()

    val rdpnPlaylist by viewModel.rdpnPlaylist.collectAsStateWithLifecycle()
    val sePlaylist by viewModel.sePlaylist.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = podcastFilter == PodcastFilter.EPISODES,
                onClick = { podcastFilter = PodcastFilter.EPISODES },
                label = { Text(stringResource(R.string.filter_episodes)) },
                leadingIcon =
                    if (podcastFilter == PodcastFilter.EPISODES) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
            FilterChip(
                selected = podcastFilter == PodcastFilter.CHANNELS,
                onClick = { podcastFilter = PodcastFilter.CHANNELS },
                label = { Text(stringResource(R.string.filter_channels)) },
                leadingIcon =
                    if (podcastFilter == PodcastFilter.CHANNELS) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
        ) {
            when (podcastFilter) {
                PodcastFilter.EPISODES -> {
                    // New Episodes - card/folder (requires login)
                    if (rdpnPlaylist != null) {
                        item(key = "new_episodes", contentType = CONTENT_TYPE_HEADER) {
                            AutoPlaylistCard(
                                title = stringResource(R.string.new_episodes),
                                thumbnailUrl = rdpnPlaylist?.thumbnail,
                                episodeCount = rdpnPlaylist?.songCountText,
                                onClick = { navController.navigate("online_playlist/RDPN") },
                            )
                        }
                    }

                    // Episodes for Later - card/folder (works both logged in and out)
                    item(key = "episodes_for_later", contentType = CONTENT_TYPE_HEADER) {
                        AutoPlaylistCard(
                            title = stringResource(R.string.episodes_for_later),
                            thumbnailUrl = sePlaylist?.thumbnail ?: savedEpisodes.firstOrNull()?.song?.thumbnailUrl,
                            episodeCount =
                                sePlaylist?.songCountText ?: if (savedEpisodes.isNotEmpty()) {
                                    pluralStringResource(R.plurals.n_episode, savedEpisodes.size, savedEpisodes.size)
                                } else {
                                    null
                                },
                            onClick = { navController.navigate("online_playlist/SE") },
                        )
                    }

                    // Saved podcast shows (episode playlists) from YT Music library
                    itemsIndexed(
                        items = subscribedChannels,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { _, podcast ->
                        PodcastEpisodePlaylistItem(
                            podcast = podcast,
                            onClick = { navController.navigate("online_podcast/${podcast.id}") },
                            onMenuClick = {
                                menuState.show {
                                    PodcastEpisodePlaylistMenu(
                                        podcast = podcast,
                                        database = database,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                        )
                    }

                    // Downloaded episodes header
                    if (downloadedEpisodes.isNotEmpty()) {
                        item(key = "downloaded_episodes_title", contentType = CONTENT_TYPE_HEADER) {
                            Text(
                                text = stringResource(R.string.downloaded_episodes),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    itemsIndexed(
                        items = downloadedEpisodes,
                        key = { _, episode -> "downloaded_${episode.id}" },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, episode ->
                        val channelName = episode.song.albumName ?: ""
                        val subtitle =
                            joinByBullet(
                                channelName,
                                makeTimeString(episode.song.duration.toLong() * 1000L),
                            )
                        SongListItem(
                            song = episode,
                            showInLibraryIcon = false,
                            isActive = episode.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showLikedIcon = false,
                            showDownloadIcon = true,
                            subtitleOverride = subtitle.ifEmpty { null },
                            onMenuClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = episode,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (episode.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = downloadedEpisodesStr,
                                                        items = downloadedEpisodes.map { it.toMediaItem() },
                                                        startIndex = index,
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = episode,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    ).animateItem(),
                        )
                    }

                    if (downloadedEpisodes.isEmpty() && subscribedChannels.isEmpty() && savedEpisodes.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_downloaded_episodes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }

                PodcastFilter.CHANNELS -> {
                    itemsIndexed(
                        items = subscribedChannels,
                        key = { _, item -> "channel_${item.id}" },
                    ) { _, channel ->
                        PodcastArtistChannelItem(
                            thumbnailUrl = channel.thumbnailUrl,
                            channelName = channel.title,
                            modifier =
                                Modifier.clickable {
                                    navController.navigate("artist/${channel.id}?isPodcastChannel=true")
                                },
                        )
                    }

                    if (subscribedChannels.isEmpty()) {
                        item(key = "empty_channels") {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_subscribed_channels),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun AutoPlaylistCard(
    title: String,
    thumbnailUrl: String?,
    episodeCount: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episodeCount != null) {
                Text(
                    text = episodeCount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PodcastEpisodePlaylistItem(
    podcast: PodcastEntity,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMenuClick()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (podcast.thumbnailUrl != null) {
                AsyncImage(
                    model = podcast.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val author = podcast.author
            if (!author.isNullOrBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Menu shown when tapping the three-dot icon on an episode playlist */
@Composable
private fun PodcastEpisodePlaylistMenu(
    podcast: PodcastEntity,
    database: MusicDatabase,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val isPinned by database.speedDialDao.isPinned(podcast.id).collectAsStateWithLifecycle(initialValue = false)

    val playlistId = podcast.id.removePrefix("MPSP")
    val shareUrl = "https://music.youtube.com/playlist?list=$playlistId"

    Spacer(Modifier.height(12.dp))
    Material3MenuGroup(
        items =
            listOf(
                Material3MenuItemData(
                    title = { Text(text = stringResource(R.string.remove_from_library)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            // Update local database
                            database.query {
                                update(podcast.copy(bookmarkedAt = null))
                            }
                            // Sync with YouTube (unsave podcast only, don't unsubscribe channel)
                            syncUtils.savePodcast(podcast.id, false)
                        }
                        onDismiss()
                    },
                ),
                Material3MenuItemData(
                    title = { Text(text = stringResource(R.string.share)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        val intent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                            }
                        context.startActivity(Intent.createChooser(intent, null))
                        onDismiss()
                    },
                ),
                Material3MenuItemData(
                    title = {
                        Text(
                            text =
                                stringResource(
                                    if (isPinned) {
                                        R.string.unpin_from_speed_dial
                                    } else {
                                        R.string.pin_to_speed_dial
                                    },
                                ),
                        )
                    },
                    icon = {
                        Icon(
                            painter =
                                painterResource(
                                    if (isPinned) R.drawable.remove else R.drawable.ic_push_pin,
                                ),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            if (isPinned) {
                                database.speedDialDao.delete(podcast.id)
                            } else {
                                database.speedDialDao.insert(
                                    SpeedDialItem(
                                        id = podcast.id,
                                        title = podcast.title,
                                        subtitle = podcast.author,
                                        thumbnailUrl = podcast.thumbnailUrl,
                                        type = "PLAYLIST",
                                    ),
                                )
                            }
                        }
                        onDismiss()
                    },
                ),
            ),
    )
    Spacer(Modifier.height(12.dp))
}

/** Artist/channel page item shown in the Channels tab */
@Composable
private fun PodcastArtistChannelItem(
    thumbnailUrl: String?,
    channelName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape),
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = channelName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
