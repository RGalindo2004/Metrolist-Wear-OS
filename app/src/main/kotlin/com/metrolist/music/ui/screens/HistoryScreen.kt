/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.core.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HistorySource
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.DateAgo
import com.metrolist.music.viewmodels.HistoryViewModel
import kotlinx.coroutines.flow.firstOrNull
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun dateAgoToString(dateAgo: DateAgo): String {
    return when (dateAgo) {
        DateAgo.Today -> stringResource(R.string.today)
        DateAgo.Yesterday -> stringResource(R.string.yesterday)
        DateAgo.ThisWeek -> stringResource(R.string.this_week)
        DateAgo.LastWeek -> stringResource(R.string.last_week)
        is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val historySource by viewModel.historySource.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val historyPage by viewModel.historyPage.collectAsStateWithLifecycle()
    
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, defaultValue = "")
    val isLoggedIn = remember(innerTubeCookie) { innerTubeCookie.isNotBlank() }

    val hideExplicit by rememberPreference(HideExplicitKey, defaultValue = false)

    val (query, onQueryChange) = rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver =
                listSaver(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) {
            mutableStateListOf<Long>()
        }

    val lazyListState = rememberLazyListState()

    BackHandler(inSelectMode || isSearching) {
        if (inSelectMode) {
            inSelectMode = false
            selection.clear()
        } else {
            isSearching = false
            onQueryChange("")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        val focusRequester = remember { FocusRequester() }
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text(stringResource(R.string.search)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            keyboardOptions = KeyboardOptions.Default,
                        )
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    } else {
                        Text(stringResource(R.string.history))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                onQueryChange("")
                            } else {
                                navController.navigateUp()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (inSelectMode) {
                        IconButton(
                            onClick = {
                                viewModel.deleteHistory(selection.toList())
                                inSelectMode = false
                                selection.clear()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                            )
                        }
                    } else {
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(
                                painter = painterResource(if (isSearching) R.drawable.close else R.drawable.search),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (historySource == HistorySource.LOCAL) {
                events.forEach { (dateAgo, dateEvents) ->
                    val filteredEvents = dateEvents.filter {
                        it.song.song.title.contains(query, ignoreCase = true) ||
                                it.song.orderedArtists.any { artist ->
                                    artist.name.contains(query, ignoreCase = true)
                                }
                    }
                    if (filteredEvents.isNotEmpty()) {
                        stickyHeader {
                            NavigationTitle(
                                title = dateAgoToString(dateAgo),
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background),
                            )
                        }

                        itemsIndexed(
                            items = filteredEvents,
                            key = { _, item -> item.event.id },
                        ) { _, item ->
                            val onCheckedChange: (Boolean) -> Unit = {
                                if (it) {
                                    selection.add(item.event.id)
                                } else {
                                    selection.remove(item.event.id)
                                }
                            }

                            SongListItem(
                                song = item.song,
                                isActive = item.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(item.event.id !in selection)
                                            } else if (item.song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.radio(item.song.toMediaMetadata()),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = item.song,
                                                        event = item.event,
                                                        onDismiss = menuState::dismiss,
                                                        onSelect = {
                                                            inSelectMode = true
                                                            onCheckedChange(true)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            } else if (historySource == HistorySource.REMOTE && isLoggedIn) {
                historyPage?.sections?.forEach { section ->
                    val filteredSongs = section.songs.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.artists.any { artist ->
                                    artist.name.contains(query, ignoreCase = true)
                                }
                    }
                    if (filteredSongs.isNotEmpty()) {
                        stickyHeader {
                            NavigationTitle(
                                title = section.title,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background),
                            )
                        }

                        itemsIndexed(
                            items = filteredSongs,
                            key = { _, it -> it.id },
                        ) { _, song ->
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.radio(song.toMediaMetadata()),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    onDismiss = menuState::dismiss,
                                                    onHistoryRemoved = {
                                                        viewModel.fetchRemoteHistory()
                                                    },
                                                    onSelect = {
                                                        // Remote history selection not implemented
                                                    }
                                                )
                                            }
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
