package com.codersguidebook.music

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Song::class], version = 1, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var database: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            database ?: kotlin.run {
                database = Room.databaseBuilder(context, MusicDatabase::class.java, "music_database")
                    .build()
            }

            return database!!
        }
    }
}
