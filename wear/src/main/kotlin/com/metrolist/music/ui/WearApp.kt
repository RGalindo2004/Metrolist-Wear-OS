/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.metrolist.music.LocalBatterySaverMode
import com.metrolist.music.core.R
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.ContentCountryKey
import com.metrolist.music.constants.ContentLanguageKey
import com.metrolist.music.constants.CountryCodeToName
import com.metrolist.music.constants.LanguageCodeToName
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.WearSongMenu
import com.metrolist.music.ui.player.WearMusicPlayer
import com.metrolist.music.ui.WearWelcomeScreen
import com.metrolist.music.utils.SearchRoutes
import timber.log.Timber

class WearSongMenuState {
    var isVisible by mutableStateOf(false)
    var metadata by mutableStateOf<MediaMetadata?>(null)

    fun show(metadata: MediaMetadata) {
        this.metadata = metadata
        isVisible = true
    }

    fun dismiss() {
        isVisible = false
    }
}

val LocalWearSongMenuState = compositionLocalOf { WearSongMenuState() }

@Composable
fun WearApp() {
    val navController = rememberSwipeDismissableNavController()
    val batterySaver = LocalBatterySaverMode.current
    val songMenuState = remember { WearSongMenuState() }
    var showWelcome by remember { mutableStateOf(true) }

    CompositionLocalProvider(LocalWearSongMenuState provides songMenuState) {
        MaterialTheme {
            if (showWelcome) {
                WearWelcomeScreen(onTimeout = { showWelcome = false })
            } else {
                Scaffold(
                    timeText = { TimeText() },
                    vignette = { 
                        if (!batterySaver) {
                            Vignette(vignettePosition = VignettePosition.TopAndBottom)
                        }
                    }
                ) {
                    Box {
                        SwipeDismissableNavHost(
                            navController = navController,
                            startDestination = "player"
                        ) {
                        composable("player") {
                            WearMusicPlayer(
                                onNavigateToSearch = { navController.navigate("search") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToLibrary = { navController.navigate("library") },
                                onNavigateToLiked = { navController.navigate("library/liked") },
                                onNavigateToDownloads = { navController.navigate("library/downloads") },
                                onNavigateToCache = { navController.navigate("library/cache") },
                                onNavigateToHistory = { navController.navigate("library/history") },
                                onNavigateToVolume = { navController.navigate("volume") },
                                onNavigateToQueue = {},
                                onNavigateToHomeSection = { sectionType -> navController.navigate("home_section/$sectionType") }
                            )
                        }
                        composable("menu") {
                            WearMenuScreen(
                                onNavigateToSearch = { navController.navigate("search") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToLogin = { navController.navigate("login") }
                            )
                        }
                        composable("search") {
                            WearSearchScreen(
                                onSearch = { q ->
                                    if (q.startsWith("online/")) {
                                        navController.navigate(q)
                                    } else {
                                        navController.navigate(SearchRoutes.resultRoute(q))
                                    }
                                },
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(SearchRoutes.ROUTE) {
                            WearSearchScreen(
                                onSearch = { q ->
                                    if (q.startsWith("online/")) {
                                        navController.navigate(q)
                                    } else {
                                        navController.navigate(SearchRoutes.resultRoute(q))
                                    }
                                },
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "online/playlist/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getString("playlistId")!!
                            WearOnlinePlaylistScreen(
                                playlistId = playlistId,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "online/album/{albumId}",
                            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val albumId = backStackEntry.arguments?.getString("albumId")!!
                            WearOnlineAlbumScreen(
                                albumId = albumId,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "online/artist/{artistId}",
                            arguments = listOf(navArgument("artistId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val artistId = backStackEntry.arguments?.getString("artistId")!!
                            WearOnlineArtistScreen(
                                artistId = artistId,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("login") {
                            WearLoginScreen()
                        }
                        composable("settings") {
                            WearSettingsScreen(
                                onNavigateToLogin = { navController.navigate("login") },
                                onNavigateToLanguage = { navController.navigate("settings/language") },
                                onNavigateToContentLanguage = { navController.navigate("settings/content_language") },
                                onNavigateToContentCountry = { navController.navigate("settings/content_country") }
                            )
                        }
                        composable("settings/language") {
                            WearLanguageScreen(
                                title = stringResource(R.string.app_language),
                                preferenceKey = AppLanguageKey,
                                options = LanguageCodeToName,
                                onSelected = { navController.navigateUp() }
                            )
                        }
                        composable("settings/content_language") {
                            WearLanguageScreen(
                                title = stringResource(R.string.content_language),
                                preferenceKey = ContentLanguageKey,
                                options = LanguageCodeToName,
                                onSelected = { navController.navigateUp() }
                            )
                        }
                        composable("settings/content_country") {
                            WearLanguageScreen(
                                title = stringResource(R.string.content_country),
                                preferenceKey = ContentCountryKey,
                                options = CountryCodeToName,
                                onSelected = { navController.navigateUp() }
                            )
                        }
                        composable("volume") {
                            WearVolumeScreen()
                        }
                        composable("library") {
                            WearLibraryScreen(
                                onNavigateToSongs = { navController.navigate("library/songs") },
                                onNavigateToAlbums = { navController.navigate("library/albums") },
                                onNavigateToArtists = { navController.navigate("library/artists") },
                                onNavigateToPlaylists = { navController.navigate("library/playlists") },
                                onNavigateToLiked = { navController.navigate("library/liked") },
                                onNavigateToDownloads = { navController.navigate("library/downloads") },
                                onNavigateToCache = { navController.navigate("library/cache") },
                                onNavigateToHistory = { navController.navigate("library/history") },
                                onNavigateToLogin = { navController.navigate("login") }
                            )
                        }
                        composable("library/songs") {
                            WearLibrarySongsScreen(
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("library/liked") {
                            WearLibrarySongsScreen(
                                filterLiked = true,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("library/downloads") {
                            WearLibrarySongsScreen(
                                filterDownloaded = true,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("library/cache") {
                            WearLibrarySongsScreen(
                                filterCached = true,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("library/history") {
                            WearHistoryScreen(
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("library/albums") {
                            WearLibraryAlbumsScreen(
                                onAlbumClick = { albumId ->
                                    navController.navigate("library/albums/$albumId")
                                }
                            )
                        }
                        composable(
                            route = "library/albums/{albumId}",
                            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val albumId = backStackEntry.arguments?.getString("albumId")!!
                            WearAlbumSongsScreen(
                                albumId = albumId,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("library/artists") {
                            WearLibraryArtistsScreen(
                                onArtistClick = { artistId ->
                                    navController.navigate("library/artists/$artistId")
                                }
                            )
                        }
                        composable(
                            route = "library/artists/{artistId}",
                            arguments = listOf(navArgument("artistId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val artistId = backStackEntry.arguments?.getString("artistId")!!
                            WearArtistSongsScreen(
                                artistId = artistId,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("library/playlists") {
                            WearLibraryPlaylistsScreen(
                                onPlaylistClick = { playlistId ->
                                    navController.navigate("library/playlists/$playlistId")
                                }
                            )
                        }
                        composable(
                            route = "library/playlists/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getString("playlistId")!!
                            WearPlaylistSongsScreen(
                                playlistId = playlistId,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home_section/{sectionType}") { backStackEntry ->
                            val sectionType = backStackEntry.arguments?.getString("sectionType")!!
                            WearHomeSectionScreen(
                                sectionType = sectionType,
                                onItemClick = {
                                    navController.navigate("player") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                    WearSongMenu()
                }
            }
        }
    }
}
}
