package com.codersguidebook.music

import androidx.lifecycle.LiveData

class MusicRepository(private val musicDao: MusicDao) {

    val allSongs: LiveData<List<Song>> = musicDao.getSongsOrderByTitle()

    suspend fun insertSong(song: Song) {
        musicDao.insert(song)
    }

    suspend fun deleteSong(song: Song) {
        musicDao.delete(song)
    }

    suspend fun updateSong(song: Song){
        musicDao.update(song)
    }

    suspend fun getSongById(songId: Long): Song? = musicDao.getSongById(songId)
}
