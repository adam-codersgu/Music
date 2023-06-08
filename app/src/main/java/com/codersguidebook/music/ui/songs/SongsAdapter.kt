package com.codersguidebook.music.ui.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.R
import com.codersguidebook.music.Song
import com.codersguidebook.recyclerviewfastscroller.RecyclerViewScrollbar

class SongsAdapter(private val activity: MainActivity):
    RecyclerView.Adapter<SongsAdapter.SongsViewHolder>(), RecyclerViewScrollbar.ValueLabelListener {
    val songs = mutableListOf<Song>()

    override fun getValueLabelText(position: Int): String {
        return if (songs[position].title.isNotEmpty()) {
            songs[position].title[0].uppercase()
        } else ""
    }

    inner class SongsViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        internal var mArtwork = itemView.findViewById<View>(R.id.artwork) as ImageView
        internal var mTitle = itemView.findViewById<View>(R.id.title) as TextView
        internal var mArtist = itemView.findViewById<View>(R.id.artist) as TextView
        internal var mMenu = itemView.findViewById<ImageButton>(R.id.menu)

        init {
            itemView.isClickable = true
            itemView.setOnClickListener {
                activity.playNewPlayQueue(songs, layoutPosition)
            }

            itemView.setOnLongClickListener{
                activity.showSongPopup(it, songs[layoutPosition])
                return@setOnLongClickListener true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongsViewHolder {
        return SongsViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.song_preview, parent, false))
    }

    override fun onBindViewHolder(holder: SongsViewHolder, position: Int) {
        val current = songs[position]

        activity.loadArtwork(current.albumId, holder.mArtwork)

        holder.mTitle.text = current.title
        holder.mArtist.text = current.artist
        holder.mMenu.setOnClickListener {
            activity.showSongPopup(it, current)
        }
    }

    override fun getItemCount() = songs.size

    fun processNewSongs(newSongs: List<Song>) {
        for ((index, song) in newSongs.withIndex()) {
            when {
                songs.isEmpty() -> {
                    songs.addAll(newSongs)
                    notifyItemRangeInserted(0, newSongs.size)
                }
                index >= songs.size -> {
                    songs.add(song)
                    notifyItemInserted(index)
                }
                song.songId != songs[index].songId -> {
                    // Check if the song is a new entry to the list
                    val songIsNewEntry = songs.find { it.songId == song.songId } == null
                    if (songIsNewEntry) {
                        songs.add(index, song)
                        notifyItemInserted(index)
                        continue
                    }

                    // Check if the song has been removed from the list
                    fun songIdsDoNotMatchAtCurrentIndex(): Boolean {
                        return newSongs.find { it.songId == songs[index].songId } == null
                    }

                    if (songIdsDoNotMatchAtCurrentIndex()) {
                        var numberOfItemsRemoved = 0
                        do {
                            songs.removeAt(index)
                            ++numberOfItemsRemoved
                        } while (index < songs.size && songIdsDoNotMatchAtCurrentIndex())

                        when {
                            numberOfItemsRemoved == 1 -> notifyItemRemoved(index)
                            numberOfItemsRemoved > 1 -> notifyItemRangeRemoved(index,
                                numberOfItemsRemoved)
                        }

                        // Check if removing the song(s) has fixed the list
                        if (song.songId == songs[index].songId) continue
                    }
                }
                song != songs[index] -> {
                    songs[index] = song
                    notifyItemChanged(index)
                }
            }
        }

        if (songs.size > newSongs.size) {
            val numberItemsToRemove = songs.size - newSongs.size
            repeat(numberItemsToRemove) { songs.removeLast() }
            notifyItemRangeRemoved(newSongs.size, numberItemsToRemove)
        }
    }
}
