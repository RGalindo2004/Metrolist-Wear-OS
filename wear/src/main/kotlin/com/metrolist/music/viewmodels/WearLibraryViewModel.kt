/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.music.constants.HistorySource
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WearLibraryViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _items = MutableStateFlow<List<YTItem>>(emptyList())
    val items: StateFlow<List<YTItem>> = _items.asStateFlow()

    private val _librarySource = MutableStateFlow(HistorySource.LOCAL)
    val librarySource: StateFlow<HistorySource> = _librarySource.asStateFlow()

    fun setSource(source: HistorySource) {
        _librarySource.value = source
    }

    fun loadLibrary(browseId: String) {
        if (_isLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val cookie = context.dataStore.data.map { it[InnerTubeCookieKey] }.first()
            if (cookie != null) {
                YouTube.cookie = cookie
            }
            YouTube.library(browseId).onSuccess {
                _items.value = it.items
            }.onFailure {
                reportException(it)
            }
            _isLoading.value = false
        }
    }

    fun loadLikedSongs() {
        if (_isLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val cookie = context.dataStore.data.map { it[InnerTubeCookieKey] }.first()
            if (cookie != null) {
                YouTube.cookie = cookie
            }
            // Liked songs browse ID
            YouTube.playlist("VLRDPN_liked_videos").onSuccess {
                _items.value = it.songs
            }.onFailure {
                reportException(it)
            }
            _isLoading.value = false
        }
    }
}
