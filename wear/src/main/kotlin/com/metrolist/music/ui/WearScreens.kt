/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import kotlin.concurrent.thread
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.tasks.await
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.metrolist.music.constants.AudioNormalizationKey
import com.metrolist.music.constants.AutoDownloadOnLikeKey
import com.metrolist.music.constants.CrossfadeDurationKey
import com.metrolist.music.constants.CrossfadeEnabledKey
import com.metrolist.music.constants.EnableSongCacheKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HistoryDuration
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.StopMusicOnTaskClearKey
import com.metrolist.music.constants.AppLanguageKey
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalBatterySaverMode
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.WearApp
import com.metrolist.music.constants.*
import com.metrolist.music.constants.AuthSyncConstants.AUTH_REQUEST_PATH
import com.metrolist.music.constants.AuthSyncConstants.OPEN_LOGIN_PATH
import com.metrolist.music.core.R
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.EventWithSong
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.playback.queues.YouTubeAlbumRadio
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.utils.GoogleDeviceAuth
import com.metrolist.music.utils.LoginHelper
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearMenuScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

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
                label = { Text(stringResource(R.string.login)) },
                icon = { Icon(painterResource(R.drawable.login), contentDescription = null) },
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
fun WearSearchScreen(
    onSearch: (String) -> Unit,
    onItemClick: () -> Unit = {}
) {
    val actualViewModel: OnlineSearchViewModel = hiltViewModel()

    val playerConnection = LocalPlayerConnection.current
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
                    TitleCard(
                        onClick = { 
                            try {
                                when (item) {
                                    is SongItem -> {
                                        val endpoint = WatchEndpoint(videoId = item.id)
                                        playerConnection?.playQueue(YouTubeQueue(endpoint)) 
                                        onItemClick()
                                    }
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
                                is SongItem -> item.artists.joinToString { it.name }
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

private enum class LoginMode { None, Google, Token }

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
    var accountInfo by remember { mutableStateOf<com.metrolist.innertube.models.AccountInfo?>(null) }
    
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
    val loginLabel = stringResource(R.string.login).uppercase()
    val wearSyncTitle = stringResource(R.string.wear_sync_title)
    val syncLibraryDesc = stringResource(R.string.sync_library_desc)
    val processingToken = stringResource(R.string.processing_token)
    val errorUnknown = stringResource(R.string.error_unknown)
    val loginFailed = stringResource(R.string.login_failed)
    val loginOnPhone = stringResource(R.string.login_on_phone)
    val openingLoginOnPhone = stringResource(R.string.opening_login_on_phone)

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
                        val queryParams = if (path.contains("?")) path.substringAfter("?") else ""
                        if (path.startsWith("/sync") || queryParams.contains("sync_block=")) {
                            val syncBlock = queryParams.split("&").find { it.startsWith("sync_block=") }?.substringAfter("sync_block=")
                            if (!syncBlock.isNullOrBlank()) {
                                val block = URLDecoder.decode(syncBlock, "UTF-8")
                                val out = client.getOutputStream().bufferedWriter()
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html><body><h2>Sync Success! Check watch.</h2></body></html>")
                                out.flush(); client.close()
                                coroutineScope.launch {
                                    isLoading = true; statusMessage = "Processing..."
                                    fun extractValue(key: String): String {
                                        val regex = """\*+\s*${key}\s*\*+\s*=\s*([^*]+)""".toRegex(RegexOption.IGNORE_CASE)
                                        return regex.find(block)?.groupValues?.get(1)?.trim() ?: ""
                                    }
                                    val cookie = extractValue("INNERTUBE COOKIE").ifBlank { block }
                                    val visitorData = extractValue("VISITOR DATA").ifBlank { YouTube.visitorData().getOrNull().orEmpty() }
                                    LoginHelper.finalizeLogin(context, cookie = cookie, visitorData = visitorData, dataSyncId = "", authUser = "0", autoRestart = false).onSuccess { accountInfo = it }.onFailure { isLoading = false; statusMessage = it.message }
                                }
                                continue
                            }
                        }
                        val out = client.getOutputStream().bufferedWriter()
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n")
                        out.write("""
                            <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>Sync</title>
                            <style>body{font-family:sans-serif;background:#121212;color:white;padding:20px;text-align:center;}.card{background:#1e1e1e;padding:20px;border-radius:12px;border:1px solid #333;}
                            textarea{width:100%;height:150px;background:#222;color:#fff;border:1px solid #444;border-radius:8px;padding:10px;margin-bottom:15px;}
                            .btn{display:block;width:100%;padding:15px;background:#BB86FC;color:#000;border:none;border-radius:30px;font-weight:bold;text-decoration:none;}</style></head>
                            <body><div class="card"><h3>Metrolist Token Sync</h3><p>Paste the token block below:</p>
                            <form method="POST"><textarea name="sync_block" placeholder="**INNERTUBE COOKIE** =..."></textarea><button type="submit" class="btn">SYCN NOW</button></form></div></body></html>
                        """.trimIndent())
                        out.flush(); client.close()
                    } else if (firstLine.startsWith("POST")) {
                        var contentLength = 0
                        var line = reader.readLine()
                        while (line != null && line.isNotEmpty()) {
                            if (line.startsWith("Content-Length:")) contentLength = line.substringAfter(": ").toInt()
                            line = reader.readLine()
                        }
                        val body = CharArray(contentLength); reader.read(body)
                        val rawData = String(body)
                        val syncBlockParam = rawData.split("&").find { it.startsWith("sync_block=") }
                        val blockValueEncoded = syncBlockParam?.substringAfter("sync_block=") ?: ""
                        val block = URLDecoder.decode(blockValueEncoded, "UTF-8")
                        val out = client.getOutputStream().bufferedWriter()
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html><body><h2>Success!</h2></body></html>")
                        out.flush(); client.close()
                        coroutineScope.launch {
                            isLoading = true; statusMessage = "Processing..."
                            fun extractValue(key: String): String {
                                val regex = """\*+\s*${key}\s*\*+\s*=\s*([^*]+)""".toRegex(RegexOption.IGNORE_CASE)
                                return regex.find(block)?.groupValues?.get(1)?.trim() ?: ""
                            }
                            val cookie = extractValue("INNERTUBE COOKIE").ifBlank { block }
                            val visitorData = extractValue("VISITOR DATA").ifBlank { YouTube.visitorData().getOrNull().orEmpty() }
                            LoginHelper.finalizeLogin(context, cookie = cookie, visitorData = visitorData, dataSyncId = "", authUser = "0", autoRestart = false).onSuccess { accountInfo = it }.onFailure { isLoading = false; statusMessage = it.message }
                        }
                    }
                }
            } catch (e: Exception) { Timber.e(e) }
        }
        onDispose { thread.interrupt(); serverSocket?.close() }
    }

    // Google Flow
    LaunchedEffect(loginMode) {
        if (loginMode != LoginMode.Google) return@LaunchedEffect
        isLoading = true; statusMessage = "Generando código..."
        GoogleDeviceAuth.requestDeviceCode().onSuccess { response ->
            deviceCodeResponse = response; isLoading = false; statusMessage = null
            coroutineScope.launch {
                while (accountInfo == null) {
                    delay(response.interval.toLong().seconds)
                    GoogleDeviceAuth.pollToken(response.deviceCode).onSuccess { tokenRes ->
                        if (tokenRes.accessToken != null) {
                            isLoading = true; statusMessage = "Sincronizando..."
                            LoginHelper.finalizeLogin(context, bearerToken = tokenRes.accessToken, refreshToken = tokenRes.refreshToken, visitorData = YouTube.visitorData().getOrNull().orEmpty(), dataSyncId = "", authUser = "0", autoRestart = false)
                                .onSuccess { accountInfo = it }.onFailure { statusMessage = it.message; isLoading = false }
                        } else if (tokenRes.error != "authorization_pending") { statusMessage = "Sesión expirada."; break }
                    }
                }
            }
        }.onFailure { statusMessage = "Error: ${it.message}"; isLoading = false }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()) {
        item { ListHeader { Text(stringResource(R.string.login)) } }

        if (loginMode == LoginMode.None) {
            item { Chip(onClick = { loginMode = LoginMode.Google }, label = { Text("Code Login") }, secondaryLabel = { Text("google.com/device") }, icon = { Icon(painterResource(R.drawable.speed), null) }, modifier = Modifier.fillMaxWidth()) }
            item { Chip(onClick = { loginMode = LoginMode.Token }, label = { Text("Token Sync") }, secondaryLabel = { Text("Manual Paste") }, icon = { Icon(painterResource(R.drawable.token), null) }, modifier = Modifier.fillMaxWidth()) }
        } else {
            item {
                Chip(
                    onClick = {
                        val url = if (loginMode == LoginMode.Google) "https://google.com/device" else serverUrl ?: "https://music.youtube.com"
                        coroutineScope.launch {
                            try {
                                val helper = RemoteActivityHelper(context, ContextCompat.getMainExecutor(context))
                                helper.startRemoteActivity(Intent(Intent.ACTION_VIEW).setData(url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE), null).await()
                                Toast.makeText(context, "Abre el navegador en tu móvil", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) { Timber.e(e) }
                        }
                    },
                    label = { Text("Login on phone") },
                    secondaryLabel = { Text(if (loginMode == LoginMode.Google) "google.com/device" else "Open Local URL") },
                    icon = { Icon(painterResource(R.drawable.login), null) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }
            if (loginMode == LoginMode.Google && deviceCodeResponse != null) {
                item { Text("Ingresa este código:", style = MaterialTheme.typography.caption2, modifier = Modifier.padding(top = 8.dp)) }
                item { Text(deviceCodeResponse!!.userCode, style = MaterialTheme.typography.display2.copy(color = MaterialTheme.colors.primary, letterSpacing = 2.sp)) }
            }
            if (loginMode == LoginMode.Token && serverUrl != null) {
                item { AsyncImage(model = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$serverUrl", contentDescription = "QR", modifier = Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White)) }
                item { Text(serverUrl!!, style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.primary) }
            }
            item { CompactChip(onClick = { loginMode = LoginMode.None; deviceCodeResponse = null; serverUrl = null; statusMessage = null }, label = { Text("Volver") }, modifier = Modifier.padding(top = 8.dp)) }
        }

        if (statusMessage != null) { item { Text(statusMessage!!, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp)) } }
        if (isLoading && accountInfo == null) { item { CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(8.dp)) } }
    }

    if (accountInfo != null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                AsyncImage(model = accountInfo!!.thumbnailUrl, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colors.surface))
                Spacer(Modifier.height(12.dp))
                Text("Bienvenido,\n${accountInfo!!.name}", textAlign = TextAlign.Center, style = MaterialTheme.typography.title3, color = MaterialTheme.colors.primary)
            }
        }
        LaunchedEffect(Unit) {
            delay(3000.milliseconds)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(launchIntent)
            }
            if (context is Activity) context.finish()
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
                onClick = onNavigateToLiked,
                label = { Text(stringResource(R.string.liked)) },
                icon = { Icon(painterResource(R.drawable.ic_heart), contentDescription = null, tint = MaterialTheme.colors.error) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToDownloads,
                label = { Text(stringResource(R.string.offline)) },
                icon = { Icon(painterResource(R.drawable.offline), contentDescription = null, tint = MaterialTheme.colors.primary) },
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
                icon = { Icon(painterResource(R.drawable.playlist_play), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
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
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val batterySaver = LocalBatterySaverMode.current
    val columnState = rememberResponsiveColumnState()
    
    val songs by remember(filterLiked, filterDownloaded) {
        when {
            filterLiked -> database.likedSongsByCreateDateAsc().map { it.reversed() }
            filterDownloaded -> database.allSongs().map { it.filter { s -> s.song.isDownloaded }.sortedByDescending { s -> s.song.dateDownload } }
            else -> database.songsByCreateDateAsc().map { it.reversed() }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val totalSongs = songs.size
    val downloadedCount = remember(songs, allDownloads) {
        songs.count { it.song.isDownloaded || allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ListHeader {
                Text(
                    text = when {
                        filterLiked -> stringResource(R.string.liked)
                        filterDownloaded -> stringResource(R.string.offline)
                        else -> stringResource(R.string.songs)
                    }
                )
            }
        }
        
        if (totalSongs > 0 && !filterDownloaded) {
            item {
                BulkDownloadButton(
                    songs = songs.map { BulkDownloadItem(it.id, it.song.title, it.song.isDownloaded) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (songs.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption2
                )
            }
        } else {
            items(songs) { song ->
                Chip(
                    onClick = {
                        val endpoint = WatchEndpoint(videoId = song.id)
                        playerConnection?.playQueue(YouTubeQueue(endpoint))
                        onItemClick()
                    },
                    label = { Text(song.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { 
                        Text(
                            text = song.artists.joinToString { it.name },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        if (!batterySaver) {
                            AsyncImage(
                                model = song.song.thumbnailUrl,
                                contentDescription = null,
                                placeholder = painterResource(R.drawable.music_note),
                                error = painterResource(R.drawable.music_note),
                                modifier = Modifier
                                    .size(ChipDefaults.IconSize)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                modifier = Modifier.size(ChipDefaults.IconSize)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibraryAlbumsScreen(onAlbumClick: (String) -> Unit) {
    val database = LocalDatabase.current
    val columnState = rememberResponsiveColumnState()
    val albums by remember { 
        database.albumsByCreateDateAsc().map { it.reversed() } 
    }.collectAsStateWithLifecycle(emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item { ListHeader { Text(stringResource(R.string.albums)) } }
        items(albums) { album ->
            Chip(
                onClick = { onAlbumClick(album.id) },
                label = { Text(album.album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text(album.artists.joinToString { it.name }, maxLines = 1) },
                icon = {
                    AsyncImage(
                        model = album.album.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibraryArtistsScreen(onArtistClick: (String) -> Unit) {
    val database = LocalDatabase.current
    val columnState = rememberResponsiveColumnState()
    val artists by remember { 
        database.artistsByPlayTimeAsc().map { it.reversed() } 
    }.collectAsStateWithLifecycle(emptyList())

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item { ListHeader { Text(stringResource(R.string.artists)) } }
        items(artists) { artist ->
            Chip(
                onClick = { onArtistClick(artist.id) },
                label = { Text(artist.artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = {
                    AsyncImage(
                        model = artist.artist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearLibraryPlaylistsScreen(onPlaylistClick: (String) -> Unit) {
    val database = LocalDatabase.current
    val columnState = rememberResponsiveColumnState()
    
    val playlists by produceState(initialValue = emptyList<com.metrolist.music.db.entities.PlaylistEntity>()) {
        value = database.playlistEntitiesByNameAsc()
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item { ListHeader { Text(stringResource(R.string.playlists)) } }
        items(playlists) { playlist ->
            Chip(
                onClick = { onPlaylistClick(playlist.id) },
                label = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(painterResource(R.drawable.playlist_play), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearVolumeScreen() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService<AudioManager>()!! }
    val columnState = rememberResponsiveColumnState()
    
    var currentVolume by remember { 
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) 
    }
    
    val watchLabel = stringResource(R.string.watch)
    val outputDeviceName = remember(watchLabel) {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothDevice = devices.find { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO 
        }
        bluetoothDevice?.productName?.toString() ?: watchLabel
    }

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ListHeader {
                Text(stringResource(R.string.volume), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    },
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Icon(painterResource(R.drawable.volume_down), contentDescription = stringResource(R.string.previous))
                }

                Text(
                    text = "$currentVolume",
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    },
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
                ) {
                    Icon(painterResource(R.drawable.volume_up), contentDescription = stringResource(R.string.next))
                }
            }
        }
        item {
            val isMuted = currentVolume == 0
            Chip(
                onClick = {
                    if (isMuted) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    }
                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                },
                label = { Text(if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)) },
                icon = { Icon(painterResource(if (isMuted) R.drawable.volume_up else R.drawable.volume_off), contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
        
        item {
            Chip(
                onClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= 31) {
                            val intent = Intent("android.media.action.LAUNCH_AUDIO_OUTPUT_SWITCHER")
                            intent.putExtra("android.media.extra.PACKAGE_NAME", context.packageName)
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        }
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            Timber.tag("WearVolumeScreen").e(e2, "Failed to launch output switcher")
                        }
                    }
                },
                label = { Text(stringResource(R.string.audio_output)) },
                secondaryLabel = { Text(outputDeviceName) },
                icon = { Icon(painterResource(R.drawable.bluetooth), contentDescription = null, tint = MaterialTheme.colors.primary) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 4.dp)
            )
        }
        
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
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
    var crossfadeDuration by rememberPreference(key = CrossfadeDurationKey, defaultValue = 5f)
    var audioNormalization by rememberPreference(key = AudioNormalizationKey, defaultValue = true)
    var skipSilence by rememberPreference(key = SkipSilenceKey, defaultValue = false)
    var sleepTimerDuration by rememberPreference(key = SleepTimerDefaultKey, defaultValue = 30f)
    var enableSongCache by rememberPreference(key = EnableSongCacheKey, defaultValue = true)
    var autoDownloadOnLike by rememberPreference(key = AutoDownloadOnLikeKey, defaultValue = false)
    var historyDuration by rememberPreference(key = HistoryDuration, defaultValue = 30f)
    var stopMusicOnTaskClear by rememberPreference(key = StopMusicOnTaskClearKey, defaultValue = true)
    var offBodyAppClose by rememberPreference(key = OffBodyAppCloseKey, defaultValue = false)
    var batterySaverMode by rememberPreference(key = BatterySaverModeKey, defaultValue = false)
    val appLanguage by rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val contentLanguage by rememberPreference(key = ContentLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val contentCountry by rememberPreference(key = ContentCountryKey, defaultValue = SYSTEM_DEFAULT)

    val accountName by remember {
        context.dataStore.data.map { it[AccountNameKey] ?: it[AccountEmailKey] }
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
                icon = { Icon(painterResource(R.drawable.account), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.app_language), style = MaterialTheme.typography.caption2) } }
        item {
            Chip(
                onClick = onNavigateToLanguage,
                label = { Text(stringResource(R.string.app_language)) },
                secondaryLabel = { Text(if (appLanguage == SYSTEM_DEFAULT) stringResource(R.string.system_default) else LanguageCodeToName[appLanguage] ?: appLanguage) },
                icon = { Icon(painterResource(R.drawable.language), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToContentLanguage,
                label = { Text(stringResource(R.string.content_language)) },
                secondaryLabel = { Text(if (contentLanguage == SYSTEM_DEFAULT) stringResource(R.string.system_default) else LanguageCodeToName[contentLanguage] ?: contentLanguage) },
                icon = { Icon(painterResource(R.drawable.language), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onNavigateToContentCountry,
                label = { Text(stringResource(R.string.content_country)) },
                secondaryLabel = { Text(if (contentCountry == SYSTEM_DEFAULT) stringResource(R.string.system_default) else CountryCodeToName[contentCountry] ?: contentCountry) },
                icon = { Icon(painterResource(R.drawable.language), contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.player), style = MaterialTheme.typography.caption2) } }
        item {
            ToggleChip(
                checked = crossfadeEnabled,
                onCheckedChange = { crossfadeEnabled = it },
                label = { Text(stringResource(R.string.crossfade)) },
                secondaryLabel = { Text(if (crossfadeEnabled) stringResource(R.string.active_format, crossfadeDuration.toInt()) else stringResource(R.string.crossfade_desc)) },
                toggleControl = { Checkbox(checked = crossfadeEnabled) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (crossfadeEnabled) {
            item {
                Chip(
                    onClick = {
                        val next = crossfadeDuration + 1f
                        crossfadeDuration = if (next > 15f) 1f else next
                    },
                    label = { Text(stringResource(R.string.adjust_duration)) },
                    secondaryLabel = { Text(pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt())) },
                    icon = { Icon(painterResource(R.drawable.more_time), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }
        }

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
                checked = stopMusicOnTaskClear,
                onCheckedChange = { stopMusicOnTaskClear = it },
                label = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                toggleControl = { Checkbox(checked = stopMusicOnTaskClear) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleChip(
                checked = offBodyAppClose,
                onCheckedChange = {
                    if (it) {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BODY_SENSORS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)
                        } else {
                            offBodyAppClose = true
                        }
                    } else {
                        offBodyAppClose = false
                    }
                },
                label = { Text(stringResource(R.string.off_body_app_close)) },
                secondaryLabel = { Text(stringResource(R.string.off_body_app_close_desc)) },
                toggleControl = { Checkbox(checked = offBodyAppClose) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleChip(
                checked = batterySaverMode,
                onCheckedChange = { batterySaverMode = it },
                label = { Text(stringResource(R.string.battery_saver_mode)) },
                secondaryLabel = { Text(stringResource(R.string.battery_saver_mode_desc)) },
                toggleControl = { Checkbox(checked = batterySaverMode) },
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

        item { ListHeader { Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.caption2) } }
        item {
            Chip(
                onClick = {
                    val next = sleepTimerDuration + 5f
                    sleepTimerDuration = if (next > 120f) 5f else next
                },
                label = { Text(stringResource(R.string.sleep_timer)) },
                secondaryLabel = { Text(pluralStringResource(R.plurals.minute, sleepTimerDuration.toInt(), sleepTimerDuration.toInt())) },
                icon = { Icon(painterResource(R.drawable.timer), contentDescription = null) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }

        item { ListHeader { Text(stringResource(R.string.history), style = MaterialTheme.typography.caption2) } }
        item {
            Chip(
                onClick = {
                    val next = historyDuration + 10f
                    historyDuration = if (next > 120f) 10f else next
                },
                label = { Text(stringResource(R.string.history_duration)) },
                secondaryLabel = { Text(pluralStringResource(R.plurals.seconds, historyDuration.toInt(), historyDuration.toInt())) },
                icon = { Icon(painterResource(R.drawable.history), contentDescription = null) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
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
                label = { Text(stringResource(R.string.hide_video_songs)) },
                toggleControl = { Checkbox(checked = hideVideoSongs) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.storage), style = MaterialTheme.typography.caption2) } }
        item {
            ToggleChip(
                checked = enableSongCache,
                onCheckedChange = { enableSongCache = it },
                label = { Text(stringResource(R.string.enable_song_cache)) },
                secondaryLabel = { Text(stringResource(R.string.enable_song_cache_desc)) },
                toggleControl = { Checkbox(checked = enableSongCache) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ToggleChip(
                checked = autoDownloadOnLike,
                onCheckedChange = { autoDownloadOnLike = it },
                label = { Text(stringResource(R.string.auto_download_on_like)) },
                secondaryLabel = { Text(stringResource(R.string.auto_download_on_like_desc)) },
                toggleControl = { Checkbox(checked = autoDownloadOnLike) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { ListHeader { Text(stringResource(R.string.settings_section_system), style = MaterialTheme.typography.caption2) } }
        item {
            Chip(
                onClick = {
                    coroutineScope.launch {
                        com.metrolist.music.utils.OTAUpdater.checkAndUpdate(context)
                    }
                },
                label = { Text(stringResource(R.string.update_app_ota)) },
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
    onSelected: () -> Unit = {}
) {
    val columnState = rememberResponsiveColumnState()
    val focusRequester = remember { FocusRequester() }

    var selectedValue by rememberPreference(key = preferenceKey, defaultValue = SYSTEM_DEFAULT)

    ScalingLazyColumn(
        columnState = columnState,
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    ) {
        item { ListHeader { Text(title) } }
        item {
            ToggleChip(
                checked = selectedValue == SYSTEM_DEFAULT,
                onCheckedChange = { if (it) { selectedValue = SYSTEM_DEFAULT; onSelected() } },
                label = { Text(stringResource(R.string.system_default)) },
                toggleControl = { RadioButton(selected = selectedValue == SYSTEM_DEFAULT) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        val sortedOptions = options.entries.sortedBy { it.value }
        items(sortedOptions) { (code, name) ->
            ToggleChip(
                checked = selectedValue == code,
                onCheckedChange = { if (it) { selectedValue = code; onSelected() } },
                label = { Text(name) },
                toggleControl = { RadioButton(selected = selectedValue == code) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

data class BulkDownloadItem(val id: String, val title: String, val isDownloaded: Boolean = false)

@Composable
fun BulkDownloadButton(
    songs: List<BulkDownloadItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadUtil = LocalDownloadUtil.current
    
    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val isManagerPaused by downloadUtil.isPaused.collectAsStateWithLifecycle()
    
    val totalSongs = songs.size
    val downloadedCount = remember(songs, allDownloads) {
        songs.count { it.isDownloaded || allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }
    
    val isAnyInQueue = remember(songs, allDownloads) {
        songs.any { 
            val s = allDownloads[it.id]?.state
            s == Download.STATE_DOWNLOADING || s == Download.STATE_QUEUED || s == Download.STATE_RESTARTING || s == Download.STATE_STOPPED
        }
    }
    
    val isDownloading = remember(songs, allDownloads) {
        songs.any { 
            val s = allDownloads[it.id]?.state
            s == Download.STATE_DOWNLOADING || s == Download.STATE_QUEUED || s == Download.STATE_RESTARTING
        }
    }

    if (totalSongs > 0) {
        Chip(
            onClick = {
                when {
                    isDownloading && !isManagerPaused -> {
                        DownloadService.sendPauseDownloads(context, ExoDownloadService::class.java, true)
                    }
                    isManagerPaused && isAnyInQueue -> {
                        DownloadService.sendResumeDownloads(context, ExoDownloadService::class.java, true)
                    }
                    downloadedCount < totalSongs -> {
                        Toast.makeText(context, R.string.downloading, Toast.LENGTH_SHORT).show()
                        if (isManagerPaused) {
                            DownloadService.sendResumeDownloads(context, ExoDownloadService::class.java, true)
                        }
                        coroutineScope.launch(Dispatchers.IO) {
                            Timber.d("BulkDownloadButton: Starting bulk download for ${songs.size} songs")
                            var firstNeeded = true
                            songs.forEach { song ->
                                val downloadState = allDownloads[song.id]?.state
                                if (!song.isDownloaded && downloadState != Download.STATE_COMPLETED && downloadState != Download.STATE_DOWNLOADING && downloadState != Download.STATE_QUEUED) {
                                    Timber.d("BulkDownloadButton: Adding download for ${song.id}")
                                    val downloadRequest = DownloadRequest.Builder(song.id, song.id.toUri())
                                        .setCustomCacheKey(song.id)
                                        .setData(song.title.toByteArray())
                                        .build()
                                    
                                    DownloadService.sendAddDownload(
                                        context,
                                        ExoDownloadService::class.java,
                                        downloadRequest,
                                        firstNeeded
                                    )
                                    firstNeeded = false
                                    // Delay to avoid overwhelming the system with intents
                                    delay(100.milliseconds)
                                }
                            }
                        }
                    }
                }
            },
            label = { 
                Text(
                    when {
                        isManagerPaused && isAnyInQueue -> stringResource(R.string.downloading_paused, downloadedCount, totalSongs)
                        isDownloading -> stringResource(R.string.downloading_progress, downloadedCount, totalSongs)
                        downloadedCount == totalSongs -> stringResource(R.string.offline)
                        else -> stringResource(R.string.action_download)
                    }
                )
            },
            icon = { 
                Icon(
                    painter = painterResource(
                        when {
                            downloadedCount == totalSongs -> R.drawable.done
                            isManagerPaused && isAnyInQueue -> R.drawable.pause
                            else -> R.drawable.download
                        }
                    ), 
                    contentDescription = null,
                    tint = if (downloadedCount == totalSongs) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
                ) 
            },
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearPlaylistSongsScreen(playlistId: String, onItemClick: () -> Unit = {}) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val columnState = rememberResponsiveColumnState()
    val playlistSongs by remember(playlistId) { database.playlistSongs(playlistId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlist by remember(playlistId) { database.playlist(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }

    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val totalSongs = playlistSongs.size
    val downloadedCount = remember(playlistSongs, allDownloads) {
        playlistSongs.count { it.song.song.isDownloaded || allDownloads[it.song.song.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(text = playlist?.title ?: stringResource(R.string.playlists)) } }
        
        if (totalSongs > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { playerConnection?.player?.shuffleModeEnabled = !shuffleModeEnabled },
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
                            playlist?.playlist?.radioEndpointParams?.let { params ->
                                playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(playlistId = playlistId, params = params)))
                            } ?: run {
                                if (!playlistId.startsWith("LP")) {
                                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(playlistId = "RD$playlistId")))
                                }
                            }
                            onItemClick()
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
            
            item {
                BulkDownloadButton(
                    songs = playlistSongs.map { BulkDownloadItem(it.song.song.id, it.song.song.title, it.song.song.isDownloaded) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        playlistSongs.forEach { playlistSong ->
                            val songId = playlistSong.song.song.id
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                songId,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (playlistSongs.isEmpty()) {
            item { Text(text = stringResource(R.string.no_results_found), modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) }
        } else {
            items(playlistSongs) { playlistSong ->
                val song = playlistSong.song
                Chip(
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = playlist?.title,
                                items = playlistSongs.map { it.song.toMediaItem() },
                                startIndex = playlistSongs.indexOf(playlistSong)
                            )
                        )
                        onItemClick()
                    },
                    label = { Text(song.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(text = song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { AsyncImage(model = song.song.thumbnailUrl, contentDescription = null, placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note), modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearAlbumSongsScreen(albumId: String, onItemClick: () -> Unit = {}) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val columnState = rememberResponsiveColumnState()
    val albumSongs by remember(albumId) { database.albumSongs(albumId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val album by remember(albumId) { database.albumWithSongs(albumId) }.collectAsStateWithLifecycle(initialValue = null)
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }

    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val totalSongs = albumSongs.size
    val downloadedCount = remember(albumSongs, allDownloads) {
        albumSongs.count { it.song.isDownloaded || allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(text = album?.album?.title ?: stringResource(R.string.albums)) } }
        
        if (totalSongs > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { playerConnection?.player?.shuffleModeEnabled = !shuffleModeEnabled },
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
                            album?.let {
                                playerConnection?.playQueue(LocalAlbumRadio(it))
                                onItemClick()
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
            
            item {
                BulkDownloadButton(
                    songs = albumSongs.map { BulkDownloadItem(it.id, it.song.title, it.song.isDownloaded) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        albumSongs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (albumSongs.isEmpty()) {
            item { Text(text = stringResource(R.string.no_results_found), modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) }
        } else {
            items(albumSongs) { song ->
                Chip(
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = album?.album?.title,
                                items = albumSongs.map { it.toMediaItem() },
                                startIndex = albumSongs.indexOf(song)
                            )
                        )
                        onItemClick()
                    },
                    label = { Text(song.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(text = song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { AsyncImage(model = song.song.thumbnailUrl, contentDescription = null, placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note), modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearArtistSongsScreen(artistId: String, onItemClick: () -> Unit = {}) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val columnState = rememberResponsiveColumnState()
    val artistSongs by remember(artistId) { database.artistSongsByCreateDateAsc(artistId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val artist by remember(artistId) { database.artist(artistId) }.collectAsStateWithLifecycle(initialValue = null)
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }

    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val totalSongs = artistSongs.size
    val downloadedCount = remember(artistSongs, allDownloads) {
        artistSongs.count { it.song.isDownloaded || allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(text = artist?.artist?.name ?: stringResource(R.string.artists)) } }
        
        if (totalSongs > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { playerConnection?.player?.shuffleModeEnabled = !shuffleModeEnabled },
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
                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(playlistId = "RD$artistId")))
                            onItemClick()
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
            
            item {
                BulkDownloadButton(
                    songs = artistSongs.map { BulkDownloadItem(it.id, it.song.title, it.song.isDownloaded) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        artistSongs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (artistSongs.isEmpty()) {
            item { Text(text = stringResource(R.string.no_results_found), modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) }
        } else {
            items(artistSongs) { song ->
                Chip(
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = artist?.artist?.name,
                                items = artistSongs.map { it.toMediaItem() },
                                startIndex = artistSongs.indexOf(song)
                            )
                        )
                        onItemClick()
                    },
                    label = { Text(song.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(text = song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { AsyncImage(model = song.song.thumbnailUrl, contentDescription = null, placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note), modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearHistoryScreen(onItemClick: () -> Unit = {}) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val columnState = rememberResponsiveColumnState()
    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()

    var historySource by remember { mutableStateOf(if (YouTube.cookie != null) HistorySource.REMOTE else HistorySource.LOCAL) }

    val historyPage by produceState<com.metrolist.innertube.pages.HistoryPage?>(initialValue = null) {
        value = YouTube.musicHistory().getOrNull()
    }

    val localEvents by remember(database) {
        database.events()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val songs = remember(historySource, historyPage, localEvents) {
        if (historySource == HistorySource.REMOTE) {
            historyPage?.sections?.flatMap { it.songs }.orEmpty()
        } else {
            localEvents.map { eventWithSong ->
                val song = eventWithSong.song
                SongItem(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.map { Artist(id = it.id, name = it.name) },
                    album = song.album?.let { Album(id = it.id, name = it.title) },
                    duration = song.song.duration,
                    thumbnail = song.thumbnailUrl ?: "",
                    explicit = false,
                    endpoint = WatchEndpoint(videoId = song.id)
                )
            }
        }
    }

    val totalSongs = songs.size
    val downloadedCount = remember(songs, allDownloads) {
        songs.count { allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(text = stringResource(R.string.history)) } }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                CompactChip(
                    onClick = { historySource = HistorySource.LOCAL },
                    label = { Text(stringResource(R.string.local_history)) },
                    colors = if (historySource == HistorySource.LOCAL) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
                CompactChip(
                    onClick = { historySource = HistorySource.REMOTE },
                    label = { Text(stringResource(R.string.remote_history)) },
                    colors = if (historySource == HistorySource.REMOTE) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (totalSongs > 0) {
            item {
                BulkDownloadButton(
                    songs = songs.map { BulkDownloadItem(it.id, it.title, false) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (historyPage == null) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (songs.isEmpty()) {
            item { Text(text = stringResource(R.string.no_results_found), modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) }
        } else {
            items(songs) { song ->
                Chip(
                    onClick = {
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = song.id)))
                        onItemClick()
                    },
                    label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(text = song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = {
                        if (!LocalBatterySaverMode.current) {
                            AsyncImage(
                                model = song.thumbnail,
                                contentDescription = null,
                                placeholder = painterResource(R.drawable.music_note),
                                error = painterResource(R.drawable.music_note),
                                modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                modifier = Modifier.size(ChipDefaults.IconSize)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearOnlinePlaylistScreen(playlistId: String, onItemClick: () -> Unit = {}) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val columnState = rememberResponsiveColumnState()
    val playlistPage by produceState<com.metrolist.innertube.pages.PlaylistPage?>(initialValue = null) { value = YouTube.playlist(playlistId).getOrNull() }
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }

    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val songs = playlistPage?.songs.orEmpty()
    val totalSongs = songs.size
    val downloadedCount = remember(songs, allDownloads) {
        songs.count { allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(text = playlistPage?.playlist?.title ?: stringResource(R.string.playlists)) } }
        
        if (totalSongs > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { playerConnection?.player?.shuffleModeEnabled = !shuffleModeEnabled },
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
                            playlistPage?.playlist?.radioEndpoint?.let { radioEndpoint ->
                                playerConnection?.playQueue(YouTubeQueue(radioEndpoint))
                                onItemClick()
                            } ?: run {
                                playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(playlistId = "RD$playlistId")))
                                onItemClick()
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
            
            item {
                BulkDownloadButton(
                    songs = songs.map { BulkDownloadItem(it.id, it.title, false) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (playlistPage == null) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (songs.isEmpty()) {
            item { Text(text = stringResource(R.string.no_results_found), modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) }
        } else {
            items(songs) { song ->
                Chip(
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = playlistPage?.playlist?.title,
                                items = songs.map { it.toMediaItem() },
                                startIndex = songs.indexOf(song)
                            )
                        )
                        onItemClick()
                    },
                    label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(text = song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { AsyncImage(model = song.thumbnail, contentDescription = null, placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note), modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearOnlineAlbumScreen(albumId: String, onItemClick: () -> Unit = {}) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val columnState = rememberResponsiveColumnState()
    val albumPage by produceState<com.metrolist.innertube.pages.AlbumPage?>(initialValue = null) { value = YouTube.album(albumId).getOrNull() }
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }

    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val songs = albumPage?.songs.orEmpty()
    val totalSongs = songs.size
    val downloadedCount = remember(songs, allDownloads) {
        songs.count { allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(text = albumPage?.album?.title ?: stringResource(R.string.albums)) } }
        
        if (totalSongs > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { playerConnection?.player?.shuffleModeEnabled = !shuffleModeEnabled },
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
                            playerConnection?.playQueue(YouTubeAlbumRadio(albumId))
                            onItemClick()
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
            
            item {
                BulkDownloadButton(
                    songs = songs.map { BulkDownloadItem(it.id, it.title, false) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (albumPage == null) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (songs.isEmpty()) { item { Text(text = stringResource(R.string.no_results_found), modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) } } else {
            items(songs) { song ->
                Chip(
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = albumPage?.album?.title,
                                items = songs.map { it.toMediaItem() },
                                startIndex = songs.indexOf(song)
                            )
                        )
                        onItemClick()
                    },
                    label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(text = song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { AsyncImage(model = song.thumbnail, contentDescription = null, placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note), modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearOnlineArtistScreen(artistId: String, onItemClick: () -> Unit = {}) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val columnState = rememberResponsiveColumnState()
    val artistPage by produceState<com.metrolist.innertube.pages.ArtistPage?>(initialValue = null) { value = YouTube.artist(artistId).getOrNull() }
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsStateWithLifecycle(initialValue = false) ?: remember { mutableStateOf(false) }

    val allDownloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val songs = artistPage?.sections?.flatMap { it.items.filterIsInstance<SongItem>() }.orEmpty()
    val totalSongs = songs.size
    val downloadedCount = remember(songs, allDownloads) {
        songs.count { allDownloads[it.id]?.state == Download.STATE_COMPLETED }
    }

    ScalingLazyColumn(columnState = columnState, modifier = Modifier.fillMaxSize()) {
        item { ListHeader { Text(text = artistPage?.artist?.title ?: stringResource(R.string.artists)) } }
        
        if (totalSongs > 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { playerConnection?.player?.shuffleModeEnabled = !shuffleModeEnabled },
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
                            artistPage?.artist?.radioEndpoint?.let { radioEndpoint ->
                                playerConnection?.playQueue(YouTubeQueue(radioEndpoint))
                                onItemClick()
                            } ?: run {
                                playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(playlistId = "RD$artistId")))
                                onItemClick()
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
            
            item {
                BulkDownloadButton(
                    songs = songs.map { BulkDownloadItem(it.id, it.title, false) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (downloadedCount > 0) {
            item {
                Chip(
                    onClick = {
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.clear_all_downloads)) },
                    icon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (artistPage == null) {
            item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (songs.isEmpty()) {
            item { Text(text = stringResource(R.string.no_results_found), modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) }
        } else {
            items(songs) { song ->
                Chip(
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = artistPage?.artist?.title,
                                items = songs.map { it.toMediaItem() },
                                startIndex = songs.indexOf(song)
                            )
                        )
                        onItemClick()
                    },
                    label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(text = song.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { AsyncImage(model = song.thumbnail, contentDescription = null, placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note), modifier = Modifier.size(ChipDefaults.IconSize).clip(CircleShape)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}
