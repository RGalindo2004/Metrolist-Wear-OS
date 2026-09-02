package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class WearHomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase
) : ViewModel() {
    val isLoading = MutableStateFlow(false)
    
    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<com.metrolist.music.db.entities.LocalItem>?>(null)
    val forYou = MutableStateFlow<HomePage.Section?>(null)
    val listenAgain = MutableStateFlow<HomePage.Section?>(null)

    init {
        load()
    }

    fun load() {
        if (isLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            try {
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val cookie = context.dataStore.data.map { it[InnerTubeCookieKey] }.first()
                
                if (cookie != null) {
                    YouTube.cookie = cookie
                }

                // 1. Quick Picks (from DB)
                database.quickPicks().first().let { songs ->
                    quickPicks.value = songs.filter { !hideVideoSongs || !it.song.isVideo }.shuffled().take(20)
                }

                // 2. Keep Listening (from DB History)
                val fromTimeStamp = LocalDateTime.now().minusWeeks(2)
                val songs = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 10, offset = 0).first()
                val albums = database.mostPlayedAlbums(fromTimeStamp, limit = 5, offset = 0).first()
                keepListening.value = (songs + albums).shuffled()

                // 3. YouTube Home Sections (For You / Listen Again)
                YouTube.home().onSuccess { page ->
                    val sections = page.sections
                    
                    listenAgain.value = sections.find { 
                        it.title.contains("Listen again", ignoreCase = true) || 
                        it.title.contains("Vuelve a escuchar", ignoreCase = true) 
                    }
                    
                    forYou.value = sections.find { 
                        it.title.contains("For you", ignoreCase = true) || 
                        it.title.contains("Para ti", ignoreCase = true) ||
                        it.title.contains("Recommended", ignoreCase = true) ||
                        it.title.contains("Mixes", ignoreCase = true)
                    } ?: sections.firstOrNull { it != listenAgain.value }

                }.onFailure { reportException(it) }

            } catch (e: Exception) {
                Timber.e(e, "Failed to load Wear home data")
            } finally {
                isLoading.value = false
            }
        }
    }
}
