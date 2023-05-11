package com.codersguidebook.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// We use AndroidViewModel so we can access the application context
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository
    val allSongs: LiveData<List<Song>>

    init {
        val musicDao = MusicDatabase.getDatabase(application).musicDao()
        repository = MusicRepository(musicDao)
        allSongs = repository.allSongs
    }

    fun deleteSong(song: Song) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteSong(song)
    }

    fun insertSong(song: Song) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertSong(song)
    }

    fun updateSong(song: Song) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateSong(song)
    }
}
