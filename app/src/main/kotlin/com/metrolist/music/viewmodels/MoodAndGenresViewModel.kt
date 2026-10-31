/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    val categoryArtwork = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val pendingArtwork = mutableSetOf<String>()
    private val artworkRequests = Semaphore(2)

    fun loadCategoryArtwork(title: String) {
        if (title in categoryArtwork.value || title in pendingArtwork || pendingArtwork.size + categoryArtwork.value.size >= 80) return
        pendingArtwork.add(title)
        viewModelScope.launch {
            try {
                val thumbnail = artworkRequests.withPermit {
                    YouTube.search("${title.take(100)} music playlist", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST)
                        .getOrNull()?.items?.filterIsInstance<com.metrolist.innertube.models.PlaylistItem>()?.firstOrNull()?.thumbnail
                }
                categoryArtwork.value = categoryArtwork.value + (title to thumbnail)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                categoryArtwork.value = categoryArtwork.value + (title to null)
            } finally {
                pendingArtwork.remove(title)
            }
        }
    }

    val browsePlaylists = MutableStateFlow<List<com.metrolist.innertube.models.PlaylistItem>>(emptyList())
    fun loadBrowsePlaylists() { viewModelScope.launch {
        YouTube.search("Deutschrap Hits", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).onSuccess { result ->
            browsePlaylists.value = result.items.filterIsInstance<com.metrolist.innertube.models.PlaylistItem>().distinctBy { it.id }.take(12)
        }
    } }
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    init {
        loadBrowsePlaylists()
        viewModelScope.launch {
            YouTube
                .moodAndGenres()
                .onSuccess {
                    moodAndGenres.value = it
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
