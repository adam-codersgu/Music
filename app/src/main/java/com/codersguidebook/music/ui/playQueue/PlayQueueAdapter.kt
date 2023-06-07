package com.codersguidebook.music.ui.playQueue

import android.annotation.SuppressLint
import android.graphics.Color
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.R
import com.google.android.material.color.MaterialColors

class PlayQueueAdapter(private val activity: MainActivity, private val fragment: PlayQueueFragment): RecyclerView.Adapter<PlayQueueAdapter.PlayQueueViewHolder>() {
    var currentlyPlayingQueueId = -1L
    val playQueue = mutableListOf<QueueItem>()

    inner class PlayQueueViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        internal var mTitle = itemView.findViewById<View>(R.id.title) as TextView
        internal var mArtist = itemView.findViewById<View>(R.id.artist) as TextView
        internal var mHandle = itemView.findViewById<ImageView>(R.id.handle)
        internal var mMenu = itemView.findViewById<ImageButton>(R.id.menu)

        init {
            itemView.isClickable = true
            itemView.setOnClickListener {
                // TODO: Skip to a different position in the play queue
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayQueueViewHolder {
        return PlayQueueViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.queue_item, parent, false))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: PlayQueueViewHolder, position: Int) {
        val currentQueueItemDescription = playQueue[position].description

        holder.mTitle.text = currentQueueItemDescription.title
        holder.mArtist.text = currentQueueItemDescription.subtitle

        val textColour = if (playQueue[position].queueId == currentlyPlayingQueueId) {
            MaterialColors.getColor(
                activity, com.google.android.material.R.attr.colorAccent, Color.CYAN
            )
        } else MaterialColors.getColor(
            activity, com.google.android.material.R.attr.colorOnSurface, Color.LTGRAY
        )

        holder.mTitle.setTextColor(textColour)
        holder.mArtist.setTextColor(textColour)

        holder.mHandle.setOnTouchListener { _, event ->
            // TODO: Handle the drag action here
            return@setOnTouchListener true
        }

        holder.mMenu.setOnClickListener {
            // TODO: Open the options menu here
        }
    }

    override fun getItemCount() = playQueue.size

    fun processNewPlayQueue(newPlayQueue: List<QueueItem>) {
        for ((index, queueItem) in newPlayQueue.withIndex()) {
            when {
                index >= playQueue.size -> {
                    playQueue.add(queueItem)
                    notifyItemInserted(index)
                }
                queueItem.queueId != playQueue[index].queueId -> {
                    // Check if the queueItem is a new entry to the list
                    val queueItemIsNewEntry = playQueue.find { it.queueId == queueItem.queueId } == null
                    if (queueItemIsNewEntry) {
                        playQueue.add(index, queueItem)
                        notifyItemInserted(index)
                        continue
                    }

                    fun queueItemIdsDoNotMatchAtCurrentIndex(): Boolean {
                        return newPlayQueue.find { it.queueId == playQueue[index].queueId } == null
                    }

                    // Check if the queueItem has been removed from the list
                    if (queueItemIdsDoNotMatchAtCurrentIndex()) {
                        var numberOfItemsRemoved = 0
                        do {
                            playQueue.removeAt(index)
                            ++numberOfItemsRemoved
                        } while (index < playQueue.size && queueItemIdsDoNotMatchAtCurrentIndex())

                        when {
                            numberOfItemsRemoved == 1 -> notifyItemRemoved(index)
                            numberOfItemsRemoved > 1 -> notifyItemRangeRemoved(index,
                                numberOfItemsRemoved)
                        }
                    }
                }
                queueItem.description.title != playQueue[index].description.title ||
                        queueItem.description.subtitle != playQueue[index].description.subtitle -> {
                    playQueue[index] = queueItem
                    notifyItemChanged(index)
                }
            }
        }

        if (playQueue.size > newPlayQueue.size) {
            val numberItemsToRemove = playQueue.size - newPlayQueue.size
            repeat(numberItemsToRemove) { playQueue.removeLast() }
            notifyItemRangeRemoved(newPlayQueue.size, numberItemsToRemove)
        }
    }

    fun changeCurrentlyPlayingQueueItemId(newQueueId: Long) {
        val oldCurrentlyPlayingIndex = playQueue.indexOfFirst {
            it.queueId == currentlyPlayingQueueId
        }

        currentlyPlayingQueueId = newQueueId
        if (oldCurrentlyPlayingIndex != -1) notifyItemChanged(oldCurrentlyPlayingIndex)

        val newCurrentlyPlayingIndex = playQueue.indexOfFirst {
            it.queueId == currentlyPlayingQueueId
        }
        if (newCurrentlyPlayingIndex != -1) {
            notifyItemChanged(newCurrentlyPlayingIndex)
        }
    }
}
