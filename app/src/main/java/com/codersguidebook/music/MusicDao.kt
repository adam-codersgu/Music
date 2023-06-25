package com.codersguidebook.music

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MusicDao {

    @Delete
    suspend fun delete(song: Song)

    @Query("SELECT * from music_library ORDER BY song_title")
    fun getSongsOrderByTitle(): LiveData<List<Song>>

    @Query("SELECT * FROM music_library WHERE song_title LIKE :search OR song_artist LIKE :search OR song_album LIKE :search")
    suspend fun getSongsLikeSearch(search: String): List<Song>

    @Query("SELECT * FROM music_library WHERE songId = :songId")
    suspend fun getSongById(songId: Long): Song?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: Song)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(song: Song)
}
