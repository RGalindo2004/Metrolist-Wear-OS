/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.remote.interactions.RemoteActivityHelper
import coil3.compose.AsyncImage
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.LocalBatterySaverMode
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.WearApp
import com.metrolist.music.constants.*
import com.metrolist.music.core.R
import com.metrolist.music.db.entities.EventWithSong
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.Album
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeAlbumRadio
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.utils.GoogleDeviceAuth
import com.metrolist.music.utils.OTAUpdater
import com.metrolist.music.utils.LoginHelper
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.resize
import com.metrolist.music.viewmodels.OnlineSearchViewModel
import com.metrolist.music.viewmodels.WearHomeViewModel
import com.metrolist.music.wear.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.concurrent.thread
import timber.log.Timber
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearMenuScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val accountName by remember(context) {
        context.dataStore.data.map { it[AccountNameKey] ?: it[AccountEmailKey] }
    }.collectAsStateWithLifecycle(initialValue = null)

    val accountPhoto by remember(context) {
        context.dataStore.data.map { it[AccountPhotoKey] }
    }.collectAsStateWithLifecycle(initialValue = null)

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
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
        item {
            Chip(
                onClick = onNavigateToLogin,
                label = { Text(accountName ?: stringResource(R.string.login)) },
                icon = {
                    if (accountPhoto != null) {
                        AsyncImage(
                            model = accountPhoto,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ChipDefaults.IconSize)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(painterResource(if (accountName != null) R.drawable.account else R.drawable.login), contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToSettings,
                label = { Text(stringResource(R.string.settings)) },
                icon = { Icon(painterResource(R.drawable.settings), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearHomeSectionScreen(
    sectionType: String,
    onItemClick: () -> Unit
) {
    val viewModel: WearHomeViewModel = hiltViewModel()
    val columnState = rememberResponsiveColumnState()
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current

    val title = when (sectionType) {
        "quick_picks" -> stringResource(R.string.quick_picks)
        "keep_listening" -> stringResource(R.string.keep_listening)
        "for_you" -> stringResource(R.string.for_you)
        "listen_again" -> stringResource(R.string.listen_again)
        else -> ""
    }

    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val forYou by viewModel.forYou.collectAsStateWithLifecycle()
    val listenAgain by viewModel.listenAgain.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item { ListHeader { Text(title) } }

        when (sectionType) {
            "quick_picks" -> {
                items(quickPicks ?: emptyList()) { song ->
                    WearSongChip(
                        title = song.song.title,
                        artists = song.artists.joinToString { it.name },
                        thumbnailUrl = song.song.thumbnailUrl,
                        onClick = {
                            playerConnection?.playQueue(ListQueue(items = listOf(song.toMediaItem())))
                            onItemClick()
                        },
                        onMenuClick = {
                            menuState.show(song.toMediaMetadata())
                        }
                    )
                }
            }
            "keep_listening" -> {
                items(keepListening ?: emptyList()) { item ->
                    when (item) {
                        is Song -> WearSongChip(
                            title = item.song.title,
                            artists = item.artists.joinToString { it.name },
                            thumbnailUrl = item.song.thumbnailUrl,
                            onClick = {
                                playerConnection?.playQueue(ListQueue(items = listOf(item.toMediaItem())))
                                onItemClick()
                            },
                            onMenuClick = {
                                menuState.show(item.toMediaMetadata())
                            }
                        )
                        is Album -> AlbumChip(item, onItemClick)
                        else -> {}
                    }
                }
            }
            "for_you" -> {
                items(forYou?.items ?: emptyList()) { item ->
                    YTItemChip(item, onItemClick)
                }
            }
            "listen_again" -> {
                items(listenAgain?.items ?: emptyList()) { item ->
                    YTItemChip(item, onItemClick)
                }
            }
        }
        
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun AlbumChip(album: Album, onClick: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current
    Chip(
        onClick = {
            album.album.playlistId?.let {
                playerConnection?.playQueue(YouTubeAlbumRadio(it))
            }
            onClick()
        },
        label = { Text(album.album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(album.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = {
            AsyncImage(
                model = album.album.thumbnailUrl?.resize(100, 100),
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.IconSize).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun YTItemChip(item: YTItem, onClick: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    
    Chip(
        onClick = {
            when (item) {
                is SongItem -> playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id)))
                is AlbumItem -> playerConnection?.playQueue(YouTubeAlbumRadio(item.playlistId))
                is com.metrolist.innertube.models.PlaylistItem -> item.playEndpoint?.let { playerConnection?.playQueue(YouTubeQueue(it)) }
                else -> {}
            }
            onClick()
        },
        label = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = {
            Box(
                modifier = Modifier
                    .size(ChipDefaults.IconSize)
                    .clip(CircleShape)
            ) {
                AsyncImage(
                    model = item.thumbnail?.resize(100, 100),
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
                                if (item is SongItem) {
                                    menuState.show(item.toMediaMetadata())
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Menu",
                        modifier = Modifier.size(16.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearSearchScreen(
    onSearch: (String) -> Unit,
    onItemClick: () -> Unit = {}
) {
    val actualViewModel: OnlineSearchViewModel = hiltViewModel()

    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }
    
    val currentFilter by actualViewModel.filter.collectAsState()
    val searchLabel = stringResource(R.string.search)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            if (result.resultCode == Activity.RESULT_OK) {
                val resultsBundle = RemoteInput.getResultsFromIntent(result.data)
                val q = resultsBundle?.getCharSequence("search_query")?.toString()
                if (!q.isNullOrBlank()) {
                    onSearch(q)
                }
            }
        } catch (e: Exception) {
            Timber.tag("WearSearchScreen").e(e, "Error handling search result")
        }
    }

    LaunchedEffect(actualViewModel.query) {
        try {
            if (actualViewModel.query.isNotEmpty() && actualViewModel.filter.value == null) {
                actualViewModel.filter.value = YouTube.SearchFilter.FILTER_SONG
            }
        } catch (e: Exception) {
            Timber.tag("WearSearchScreen").e(e, "Error updating search filter")
        }
    }

    LaunchedEffect(Unit) {
        if (actualViewModel.query.isEmpty()) {
            try {
                val remoteInput = RemoteInput.Builder("search_query")
                    .setLabel(searchLabel)
                    .build()
                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                launcher.launch(intent)
            } catch (e: Exception) {
                Timber.tag("WearSearchScreen").e(e, "Failed to launch RemoteInput auto")
            }
        }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
    ) {
        item {
            ListHeader {
                Text(
                    text = actualViewModel.query.ifEmpty { stringResource(R.string.search) },
                    textAlign = TextAlign.Center
                )
            }
        }
        
        item {
            Chip(
                onClick = {
                    try {
                        val remoteInput = RemoteInput.Builder("search_query")
                            .setLabel(searchLabel)
                            .build()
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                        launcher.launch(intent)
                    } catch (e: Exception) {
                        Timber.tag("WearSearchScreen").e(e, "Failed to launch RemoteInput")
                    }
                },
                label = { Text(stringResource(R.string.search)) },
                icon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }

        if (YouTube.visitorData == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.listen_together_connecting),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (actualViewModel.query.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                ) {
                    val filters = listOf(
                        Triple(null, R.string.filter_all, R.drawable.grid_view),
                        Triple(YouTube.SearchFilter.FILTER_SONG, R.string.songs, R.drawable.music_note),
                        Triple(YouTube.SearchFilter.FILTER_ALBUM, R.string.albums, R.drawable.album),
                        Triple(YouTube.SearchFilter.FILTER_ARTIST, R.string.artists, R.drawable.artist),
                        Triple(YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST, R.string.playlists, R.drawable.library_music)
                    )
                    filters.forEach { (filter, labelRes, iconRes) ->
                        CompactChip(
                            onClick = {
                                try {
                                    actualViewModel.filter.value = filter
                                } catch (e: Exception) {
                                    Timber.tag("WearSearchScreen").e(e, "Failed to set filter")
                                }
                            },
                            label = {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = stringResource(labelRes),
                                    modifier = Modifier.size(ChipDefaults.SmallIconSize)
                                )
                            },
                            colors = if (currentFilter == filter)
                                ChipDefaults.primaryChipColors()
                            else
                                ChipDefaults.secondaryChipColors()
                        )
                    }
                }
            }

            val filterValue = currentFilter?.value ?: YouTube.SearchFilter.FILTER_SONG.value
            val itemsPage = actualViewModel.viewStateMap[filterValue]
            val results = itemsPage?.items.orEmpty()
            val isLoading = itemsPage == null

            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            } else {
                items(results) { item ->
                    if (item is SongItem) {
                        WearSongChip(
                            title = item.title,
                            artists = item.artists.joinToString { it.name },
                            thumbnailUrl = item.thumbnail,
                            onClick = {
                                val endpoint = WatchEndpoint(videoId = item.id)
                                playerConnection?.playQueue(YouTubeQueue(endpoint))
                                onItemClick()
                            },
                            onMenuClick = {
                                menuState.show(item.toMediaMetadata())
                            }
                        )
                    } else {
                        TitleCard(
                            onClick = {
                                try {
                                    when (item) {
                                        is AlbumItem -> {
                                            onSearch("online/album/${item.id}")
                                        }
                                        is ArtistItem -> {
                                            onSearch("online/artist/${item.id}")
                                        }
                                        is com.metrolist.innertube.models.PlaylistItem -> {
                                            onSearch("online/playlist/${item.id}")
                                        }
                                        is com.metrolist.innertube.models.PodcastItem -> {
                                            onSearch("online/playlist/${item.id}")
                                        }
                                        is com.metrolist.innertube.models.EpisodeItem -> {
                                            val endpoint = WatchEndpoint(videoId = item.id)
                                            playerConnection?.playQueue(YouTubeQueue(endpoint))
                                            onItemClick()
                                        }
                                        else -> {}
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("WearSearchScreen").e(e, "Failed to handle item click")
                                }
                            },
                            title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            backgroundPainter = CardDefaults.cardBackgroundPainter(
                                startBackgroundColor = MaterialTheme.colors.surface,
                                endBackgroundColor = MaterialTheme.colors.surface
                            ),
                            contentColor = MaterialTheme.colors.onSurface,
                            titleColor = MaterialTheme.colors.onSurface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = item.thumbnail,
                                    contentDescription = null,
                                    placeholder = painterResource(R.drawable.music_note),
                                    error = painterResource(R.drawable.music_note),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                val subtitle = when (item) {
                                    is AlbumItem -> item.artists?.joinToString { it.name }.orEmpty()
                                    is ArtistItem -> stringResource(R.string.artists)
                                    else -> ""
                                }
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.caption2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colors.secondary
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (itemsPage.continuation != null) {
                    item {
                        Chip(
                            onClick = { 
                                try {
                                    actualViewModel.loadMore() 
                                } catch (e: Exception) {
                                    Timber.tag("WearSearchScreen").e(e, "Failed to load more")
                                }
                            },
                            label = { Text(stringResource(R.string.show_more)) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
                
                item {
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}


private enum class LoginMode { None, Token }

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLoginScreen(onDismiss: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    var loginMode by remember { mutableStateOf(LoginMode.None) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    
    var deviceCodeResponse by remember { mutableStateOf<GoogleDeviceAuth.DeviceCodeResponse?>(null) }

    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val wifiIp = interfaces.filter { it.name.contains("wlan") || it.name.contains("eth") }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .map { it.hostAddress }
                .firstOrNull()
            if (wifiIp != null) return wifiIp
            interfaces.flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .map { it.hostAddress }
                .firstOrNull()
        } catch (e: Exception) { null }
    }

    val errorNoWifiIp = stringResource(R.string.error_no_wifi_ip)
    
    // Local Server Flow
    DisposableEffect(loginMode) {
        if (loginMode != LoginMode.Token) return@DisposableEffect onDispose {}
        var ip = getLocalIpAddress()
        var serverSocket: ServerSocket? = null
        val thread = thread {
            try {
                serverSocket = try { ServerSocket(8080) } catch (e: Exception) { ServerSocket(0) }
                val port = serverSocket?.localPort ?: 8080
                if (ip == null) ip = getLocalIpAddress()
                if (ip != null) {
                    serverUrl = "http://$ip:$port"
                } else { statusMessage = errorNoWifiIp }
                while (!Thread.currentThread().isInterrupted) {
                    val client = try { serverSocket?.accept() } catch (e: Exception) { null } ?: break
                    val reader = client.getInputStream().bufferedReader()
                    val firstLine = reader.readLine() ?: continue
                    if (firstLine.startsWith("GET")) {
                        val path = firstLine.substringAfter(" ").substringBefore(" ")
                        val query = if (path.contains("?")) path.substringAfter("?") else ""
                        val params = query.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
                        
                        if (params.containsKey("cookie")) {
                            val cookie = java.net.URLDecoder.decode(params["cookie"], "UTF-8")
                            coroutineScope.launch {
                                isLoading = true
                                statusMessage = "Iniciando sesión..."
                                
                                runCatching {
                                    YouTube.cookie = cookie
                                    val accountInfo = YouTube.accountInfo().getOrThrow()
                                    
                                    context.safeDataStoreEdit { settings ->
                                        settings[InnerTubeCookieKey] = cookie
                                        settings[AccountNameKey] = accountInfo.name
                                        settings[AccountEmailKey] = accountInfo.email.orEmpty()
                                        settings[AccountPhotoKey] = accountInfo.thumbnailUrl.orEmpty()
                                    }
                                    
                                    statusMessage = "¡Éxito! Reiniciando..."
                                    delay(2000)
                                    if (context is Activity) {
                                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        context.startActivity(intent)
                                        Runtime.getRuntime().exit(0)
                                    }
                                }.onFailure { e ->
                                    isLoading = false
                                    statusMessage = "Error: ${e.message}"
                                }
                            }
                        }
                        
                        val response = """
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body { font-family: sans-serif; padding: 20px; background: #121212; color: white; text-align: center; }
                                    textarea { width: 100%; height: 120px; margin: 10px 0; background: #222; color: white; border: 1px solid #444; border-radius: 8px; padding: 10px; font-size: 14px; }
                                    button { background: #BB86FC; color: black; border: none; padding: 12px 24px; border-radius: 20px; font-weight: bold; cursor: pointer; font-size: 16px; width: 100%; }
                                    h1 { color: #BB86FC; margin-bottom: 20px; }
                                    p { color: #aaa; margin-bottom: 20px; }
                                </style>
                            </head>
                            <body>
                                <h1>Metrolist Login</h1>
                                <p>Copia y pega tu cookie de InnerTube aquí:</p>
                                <form action="/" method="GET">
                                    <textarea name="cookie" placeholder="Ej: VISITOR_INFO1_LIVE=...; SID=..."></textarea>
                                    <br>
                                    <button type="submit">INICIAR SESIÓN EN EL RELOJ</button>
                                </form>
                            </body>
                            </html>
                        """.trimIndent()
                        client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n$response".toByteArray())
                        client.close()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Server error")
            } finally {
                serverSocket?.close()
            }
        }
        onDispose {
            thread.interrupt()
            serverSocket?.close()
        }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(stringResource(R.string.login)) } }
        
        if (isLoading) {
            item { Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (statusMessage != null) {
            item { 
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            item {
                Chip(
                    onClick = { statusMessage = null; loginMode = LoginMode.None },
                    label = { Text(stringResource(R.string.back_button_desc)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            when (loginMode) {
                LoginMode.None -> {
                    item {
                        Chip(
                            onClick = { loginMode = LoginMode.Token },
                            label = { Text("Browser Login") },
                            icon = { Icon(painterResource(R.drawable.sync), null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Chip(
                            onClick = onDismiss,
                            label = { Text(stringResource(R.string.dismiss)) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                LoginMode.Token -> {
                    item {
                        Text(
                            text = if (serverUrl != null) "Abre este enlace en tu celular:" else "Iniciando servidor...",
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        )
                    }
                    if (serverUrl != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${serverUrl}",
                                    contentDescription = "QR Login",
                                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                        item {
                            val remoteActivityHelper = remember { RemoteActivityHelper(context) }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            remoteActivityHelper.startRemoteActivity(
                                                Intent(Intent.ACTION_VIEW).apply {
                                                    data = serverUrl!!.toUri()
                                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                                }
                                            ).await()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al abrir en el celular", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) {
                                Text("Abrir en Celular", fontSize = 12.sp)
                            }
                        }
                        item {
                            Text(
                                text = serverUrl!!,
                                style = MaterialTheme.typography.caption3,
                                color = MaterialTheme.colors.secondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibraryScreen(
    onNavigateToSongs: () -> Unit,
    onNavigateToAlbums: () -> Unit,
    onNavigateToArtists: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLiked: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val columnState = rememberResponsiveColumnState()
    val syncUtils = LocalSyncUtils.current
    
    val cookie by remember {
        context.dataStore.data.map { it[InnerTubeCookieKey] }
    }.collectAsStateWithLifecycle(initialValue = null)

    val syncState by syncUtils.syncState.collectAsStateWithLifecycle()
    val syncCompletedStr = stringResource(R.string.sync_completed)
    val syncErrorStr = stringResource(R.string.sync_error)

    LaunchedEffect(syncState.overallStatus) {
        when (syncState.overallStatus) {
            com.metrolist.music.utils.SyncStatus.Completed -> {
                Toast.makeText(context, syncCompletedStr, Toast.LENGTH_SHORT).show()
            }
            is com.metrolist.music.utils.SyncStatus.Error -> {
                Toast.makeText(context, syncErrorStr, Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ListHeader {
                Text(stringResource(R.string.filter_library))
            }
        }
        item {
            val startingSyncStr = stringResource(R.string.starting_sync)
            Chip(
                onClick = { 
                    if (cookie == null) {
                        onNavigateToLogin()
                    } else {
                        Toast.makeText(context, startingSyncStr, Toast.LENGTH_SHORT).show()
                        syncUtils.performFullSync()
                    }
                },
                label = { Text(stringResource(R.string.sync_library)) },
                secondaryLabel = { Text(stringResource(R.string.sync_library_desc)) },
                icon = { Icon(painterResource(R.drawable.sync), contentDescription = null) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToSongs,
                label = { Text(stringResource(R.string.songs)) },
                icon = { Icon(painterResource(R.drawable.music_note), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToAlbums,
                label = { Text(stringResource(R.string.albums)) },
                icon = { Icon(painterResource(R.drawable.album), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToArtists,
                label = { Text(stringResource(R.string.artists)) },
                icon = { Icon(painterResource(R.drawable.artist), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToPlaylists,
                label = { Text(stringResource(R.string.playlists)) },
                icon = { Icon(painterResource(R.drawable.library_music), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        item { ListHeader { Text(stringResource(R.string.filter_all)) } }
        
        item {
            Chip(
                onClick = onNavigateToLiked,
                label = { Text(stringResource(R.string.filter_liked)) },
                icon = { Icon(painterResource(R.drawable.ic_heart), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToDownloads,
                label = { Text(stringResource(R.string.filter_downloaded)) },
                icon = { Icon(painterResource(R.drawable.offline), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToHistory,
                label = { Text(stringResource(R.string.history)) },
                icon = { Icon(painterResource(R.drawable.history), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibrarySongsScreen(
    filterLiked: Boolean = false,
    filterDownloaded: Boolean = false,
    onItemClick: () -> Unit = {}
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val songs by when {
        filterLiked -> database.likedSongs(SongSortType.CREATE_DATE, true)
        filterDownloaded -> database.downloadedSongs(SongSortType.CREATE_DATE, true)
        else -> database.songs(SongSortType.CREATE_DATE, true)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item {
            ListHeader {
                Text(
                    text = when {
                        filterLiked -> stringResource(R.string.filter_liked)
                        filterDownloaded -> stringResource(R.string.filter_downloaded)
                        else -> stringResource(R.string.songs)
                    }
                )
            }
        }
        
        items(songs) { song ->
            WearSongChip(
                title = song.song.title,
                artists = song.artists.joinToString { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                onClick = {
                    playerConnection?.playQueue(ListQueue(items = listOf(song.toMediaItem())))
                    onItemClick()
                },
                onMenuClick = {
                    menuState.show(song.toMediaMetadata())
                }
            )
        }
        item {
            Spacer(Modifier.height(40.dp))
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearHistoryScreen(onItemClick: () -> Unit = {}) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val history by database.events().collectAsStateWithLifecycle(initialValue = emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(stringResource(R.string.history)) } }
        items(history) { event ->
            WearSongChip(
                title = event.song.song.title,
                artists = event.song.artists.joinToString { it.name },
                thumbnailUrl = event.song.song.thumbnailUrl,
                onClick = {
                    playerConnection?.playQueue(ListQueue(items = listOf(event.song.toMediaItem())))
                    onItemClick()
                },
                onMenuClick = {
                    menuState.show(event.song.toMediaMetadata())
                }
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibraryAlbumsScreen(onAlbumClick: (String) -> Unit) {
    val database = LocalDatabase.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val albums by database.albums(AlbumSortType.CREATE_DATE, true).collectAsStateWithLifecycle(initialValue = emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(stringResource(R.string.albums)) } }
        items(albums) { album ->
            Chip(
                onClick = { onAlbumClick(album.id) },
                label = { Text(album.album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text(album.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = {
                    AsyncImage(
                        model = album.album.thumbnailUrl?.resize(100, 100),
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.IconSize).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearAlbumSongsScreen(albumId: String, onItemClick: () -> Unit) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val album by database.album(albumId).collectAsStateWithLifecycle(initialValue = null)
    val songs by database.albumSongs(albumId).collectAsStateWithLifecycle(initialValue = emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(album?.album?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        items(songs) { song ->
            WearSongChip(
                title = song.song.title,
                artists = song.artists.joinToString { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                onClick = {
                    playerConnection?.playQueue(ListQueue(items = songs.map { it.toMediaItem() }, startIndex = songs.indexOf(song)))
                    onItemClick()
                },
                onMenuClick = {
                    menuState.show(song.toMediaMetadata())
                }
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibraryArtistsScreen(onArtistClick: (String) -> Unit) {
    val database = LocalDatabase.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val artists by database.artists(ArtistSortType.CREATE_DATE, true).collectAsStateWithLifecycle(initialValue = emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(stringResource(R.string.artists)) } }
        items(artists) { artist ->
            Chip(
                onClick = { onArtistClick(artist.id) },
                label = { Text(artist.artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = {
                    AsyncImage(
                        model = artist.artist.thumbnailUrl?.resize(100, 100),
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearArtistSongsScreen(artistId: String, onItemClick: () -> Unit) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val artist by database.artist(artistId).collectAsStateWithLifecycle(initialValue = null)
    val songs by database.artistSongs(artistId, ArtistSongSortType.CREATE_DATE, true).collectAsStateWithLifecycle(initialValue = emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(artist?.artist?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        items(songs) { song ->
            WearSongChip(
                title = song.song.title,
                artists = song.artists.joinToString { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                onClick = {
                    playerConnection?.playQueue(ListQueue(items = songs.map { it.toMediaItem() }, startIndex = songs.indexOf(song)))
                    onItemClick()
                },
                onMenuClick = {
                    menuState.show(song.toMediaMetadata())
                }
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibraryPlaylistsScreen(onPlaylistClick: (String) -> Unit) {
    val database = LocalDatabase.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val playlists by database.playlists(PlaylistSortType.CREATE_DATE, true).collectAsStateWithLifecycle(initialValue = emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(stringResource(R.string.playlists)) } }
        items(playlists) { playlist ->
            Chip(
                onClick = { onPlaylistClick(playlist.id) },
                label = { Text(playlist.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text(pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount)) },
                icon = {
                    AsyncImage(
                        model = playlist.playlist.thumbnailUrl?.resize(100, 100),
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.IconSize).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearPlaylistSongsScreen(playlistId: String, onItemClick: () -> Unit) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val playlist by database.playlist(playlistId).collectAsStateWithLifecycle(initialValue = null)
    val playlistSongs by database.playlistSongs(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    val songs = remember(playlistSongs) { playlistSongs.map { it.song } }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(playlist?.playlist?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        items(playlistSongs) { playlistSong ->
            val song = playlistSong.song
            WearSongChip(
                title = song.song.title,
                artists = song.artists.joinToString { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                onClick = {
                    playerConnection?.playQueue(ListQueue(items = songs.map { it.toMediaItem() }, startIndex = songs.indexOf(song)))
                    onItemClick()
                },
                onMenuClick = {
                    menuState.show(song.toMediaMetadata())
                }
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearOnlinePlaylistScreen(playlistId: String, onItemClick: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    var playlistPage by remember { mutableStateOf<com.metrolist.innertube.pages.PlaylistPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        YouTube.playlist(playlistId).onSuccess {
            playlistPage = it
            isLoading = false
        }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(playlistPage?.playlist?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        
        if (isLoading) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            items(playlistPage?.songs ?: emptyList()) { songItem ->
                WearSongChip(
                    title = songItem.title,
                    artists = songItem.artists.joinToString { it.name },
                    thumbnailUrl = songItem.thumbnail,
                    onClick = {
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = songItem.id, playlistId = playlistPage!!.playlist.id)))
                        onItemClick()
                    },
                    onMenuClick = {
                        menuState.show(songItem.toMediaMetadata())
                    }
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearOnlineAlbumScreen(albumId: String, onItemClick: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    var albumPage by remember { mutableStateOf<com.metrolist.innertube.pages.AlbumPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(albumId) {
        YouTube.album(albumId).onSuccess {
            albumPage = it
            isLoading = false
        }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(albumPage?.album?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        
        if (isLoading) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            items(albumPage?.songs ?: emptyList()) { songItem ->
                WearSongChip(
                    title = songItem.title,
                    artists = songItem.artists.joinToString { it.name },
                    thumbnailUrl = songItem.thumbnail,
                    onClick = {
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = songItem.id, playlistId = albumPage!!.album.playlistId)))
                        onItemClick()
                    },
                    onMenuClick = {
                        menuState.show(songItem.toMediaMetadata())
                    }
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearOnlineArtistScreen(artistId: String, onItemClick: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalWearSongMenuState.current
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    var artistPage by remember { mutableStateOf<com.metrolist.innertube.pages.ArtistPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistId) {
        YouTube.artist(artistId).onSuccess {
            artistPage = it
            isLoading = false
        }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(artistPage?.artist?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        
        if (isLoading) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            artistPage?.sections?.forEach { section ->
                item { ListHeader { Text(section.title ?: "", style = MaterialTheme.typography.caption2) } }
                items(section.items) { item ->
                    when (item) {
                        is SongItem -> {
                            WearSongChip(
                                title = item.title,
                                artists = item.artists.joinToString { it.name },
                                thumbnailUrl = item.thumbnail,
                                onClick = {
                                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id)))
                                    onItemClick()
                                },
                                onMenuClick = {
                                    menuState.show(item.toMediaMetadata())
                                }
                            )
                        }
                        is AlbumItem -> {
                            Chip(
                                onClick = { /* Navigate to album */ },
                                label = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = {
                                    AsyncImage(
                                        model = item.thumbnail.resize(100, 100),
                                        contentDescription = null,
                                        modifier = Modifier.size(ChipDefaults.IconSize).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun WearSongChip(
    title: String,
    artists: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(artists, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = {
            Box(
                modifier = Modifier
                    .size(ChipDefaults.IconSize)
                    .clip(CircleShape)
            ) {
                AsyncImage(
                    model = thumbnailUrl?.resize(100, 100),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = onMenuClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Menu",
                        modifier = Modifier.size(16.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalHorologistApi::class, ExperimentalWearMaterialApi::class)
@Composable
fun WearSettingsScreen(
    onNavigateToLogin: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToContentLanguage: () -> Unit = {},
    onNavigateToContentCountry: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    var hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    var hideVideoSongs by rememberPreference(key = HideVideoSongsKey, defaultValue = false)
    var crossfadeEnabled by rememberPreference(key = CrossfadeEnabledKey, defaultValue = false)
    var audioNormalization by rememberPreference(key = AudioNormalizationKey, defaultValue = true)
    var skipSilence by rememberPreference(key = SkipSilenceKey, defaultValue = false)
    var stopMusicOnTaskClear by rememberPreference(key = StopMusicOnTaskClearKey, defaultValue = true)
    var offBodyAppClose by rememberPreference(key = OffBodyAppCloseKey, defaultValue = false)
    var batterySaverMode by rememberPreference(key = BatterySaverModeKey, defaultValue = false)
    val appLanguage by rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val contentLanguage by rememberPreference(key = ContentLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val contentCountry by rememberPreference(key = ContentCountryKey, defaultValue = SYSTEM_DEFAULT)

    val accountName by remember(context) {
        context.dataStore.data.map { it[AccountNameKey] ?: it[AccountEmailKey] }
    }.collectAsStateWithLifecycle(initialValue = null)

    val accountPhoto by remember(context) {
        context.dataStore.data.map { it[AccountPhotoKey] }
    }.collectAsStateWithLifecycle(initialValue = null)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            offBodyAppClose = false
        }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(stringResource(R.string.settings)) } }

        item { ListHeader { Text(stringResource(R.string.account), style = MaterialTheme.typography.caption2) } }
        item {
            Chip(
                onClick = {
                    if (accountName != null) {
                        coroutineScope.launch { WearApp.forgetAccount(context) }
                    } else {
                        onNavigateToLogin()
                    }
                },
                label = { Text(accountName ?: stringResource(R.string.not_logged_in)) },
                secondaryLabel = { Text(if (accountName != null) stringResource(R.string.action_logout) else stringResource(R.string.action_login)) },
                icon = {
                    if (accountPhoto != null) {
                        AsyncImage(
                            model = accountPhoto,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ChipDefaults.IconSize)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(painterResource(R.drawable.account), contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.app_language), style = MaterialTheme.typography.caption2) } }
        item {
            Chip(
                onClick = onNavigateToLanguage,
                label = { Text(stringResource(R.string.app_language)) },
                secondaryLabel = { Text(LanguageCodeToName[appLanguage] ?: stringResource(R.string.system_default)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToContentLanguage,
                label = { Text(stringResource(R.string.content_language)) },
                secondaryLabel = { Text(LanguageCodeToName[contentLanguage] ?: stringResource(R.string.system_default)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToContentCountry,
                label = { Text(stringResource(R.string.content_country)) },
                secondaryLabel = { Text(CountryCodeToName[contentCountry] ?: stringResource(R.string.system_default)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.playback), style = MaterialTheme.typography.caption2) } }
        item {
            ToggleChip(
                checked = audioNormalization,
                onCheckedChange = { audioNormalization = it },
                label = { Text(stringResource(R.string.audio_normalization)) },
                toggleControl = { Checkbox(checked = audioNormalization) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleChip(
                checked = skipSilence,
                onCheckedChange = { skipSilence = it },
                label = { Text(stringResource(R.string.skip_silence)) },
                toggleControl = { Checkbox(checked = skipSilence) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleChip(
                checked = crossfadeEnabled,
                onCheckedChange = { crossfadeEnabled = it },
                label = { Text("Crossfade") },
                toggleControl = { Checkbox(checked = crossfadeEnabled) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.content), style = MaterialTheme.typography.caption2) } }
        item {
            ToggleChip(
                checked = hideExplicit,
                onCheckedChange = { hideExplicit = it },
                label = { Text(stringResource(R.string.hide_explicit)) },
                toggleControl = { Checkbox(checked = hideExplicit) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleChip(
                checked = hideVideoSongs,
                onCheckedChange = { hideVideoSongs = it },
                label = { Text("Hide video songs") },
                toggleControl = { Checkbox(checked = hideVideoSongs) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.privacy), style = MaterialTheme.typography.caption2) } }
        item {
            ToggleChip(
                checked = offBodyAppClose,
                onCheckedChange = {
                    if (it) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)
                        } else {
                            offBodyAppClose = true
                        }
                    } else {
                        offBodyAppClose = false
                    }
                },
                label = { Text("Off-body app close") },
                toggleControl = { Checkbox(checked = offBodyAppClose) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.misc), style = MaterialTheme.typography.caption2) } }
        item {
            ToggleChip(
                checked = batterySaverMode,
                onCheckedChange = { batterySaverMode = it },
                label = { Text("Battery saver mode") },
                toggleControl = { Checkbox(checked = batterySaverMode) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleChip(
                checked = stopMusicOnTaskClear,
                onCheckedChange = { stopMusicOnTaskClear = it },
                label = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                toggleControl = { Checkbox(checked = stopMusicOnTaskClear) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.about), style = MaterialTheme.typography.caption2) } }
        item {
            Chip(
                onClick = {
                    coroutineScope.launch {
                        OTAUpdater.checkAndUpdate(context)
                    }
                },
                label = { Text(stringResource(R.string.app_version)) },
                secondaryLabel = { Text(BuildConfig.VERSION_NAME) },
                icon = { Icon(painterResource(R.drawable.update), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLanguageScreen(
    title: String,
    preferenceKey: androidx.datastore.preferences.core.Preferences.Key<String>,
    options: Map<String, String>,
    onSelected: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    val currentSelection by rememberPreference(key = preferenceKey, defaultValue = SYSTEM_DEFAULT)

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(title) } }
        item {
            SelectableChip(
                selected = currentSelection == SYSTEM_DEFAULT,
                onClick = { 
                    coroutineScope.launch { 
                        context.dataStore.edit { it[preferenceKey] = SYSTEM_DEFAULT }
                        onSelected()
                    }
                },
                label = { Text(stringResource(R.string.system_default)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(options.entries.toList()) { (code, name) ->
            SelectableChip(
                selected = currentSelection == code,
                onClick = {
                    coroutineScope.launch { 
                        context.dataStore.edit { it[preferenceKey] = code }
                        onSelected()
                    }
                },
                label = { Text(name) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearVolumeScreen() {
    val context = LocalContext.current
    val audioManager = context.getSystemService<AudioManager>()!!
    val columnState = rememberResponsiveColumnState()
    
    var volume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item { ListHeader { Text(stringResource(R.string.volume)) } }
        item {
            InlineSlider(
                value = volume,
                onValueChange = { 
                    volume = it
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume.toInt(), 0)
                },
                valueRange = 0f..maxVolume.toFloat(),
                steps = maxVolume - 1,
                decreaseIcon = { Icon(painterResource(R.drawable.volume_down), null) },
                increaseIcon = { Icon(painterResource(R.drawable.volume_up), null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val outputDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            
            Chip(
                onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                label = { Text(stringResource(R.string.audio_output)) },
                secondaryLabel = { Text(outputDevice?.productName?.toString() ?: stringResource(R.string.watch)) },
                icon = { Icon(painterResource(R.drawable.bluetooth), null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
