/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.HistoryPage
import com.metrolist.music.constants.HistorySource
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.EventWithSong
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
class WearHistoryViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase
) : ViewModel() {
    private val _historySource = MutableStateFlow(HistorySource.LOCAL)
    val historySource: StateFlow<HistorySource> = _historySource.asStateFlow()

    private val _localHistory = MutableStateFlow<List<EventWithSong>>(emptyList())
    val localHistory: StateFlow<List<EventWithSong>> = _localHistory.asStateFlow()

    private val _remoteHistory = MutableStateFlow<HistoryPage?>(null)
    val remoteHistory: StateFlow<HistoryPage?> = _remoteHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadLocalHistory()
        fetchRemoteHistory()
    }

    fun setSource(source: HistorySource) {
        _historySource.value = source
    }

    private fun loadLocalHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.events().collect {
                _localHistory.value = it
            }
        }
    }

    fun fetchRemoteHistory() {
        if (_isLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val cookie = context.dataStore.data.map { it[InnerTubeCookieKey] }.first()
            if (cookie != null) {
                YouTube.cookie = cookie
            }
            YouTube.musicHistory().onSuccess {
                _remoteHistory.value = it
            }.onFailure {
                reportException(it)
            }
            _isLoading.value = false
        }
    }
}
