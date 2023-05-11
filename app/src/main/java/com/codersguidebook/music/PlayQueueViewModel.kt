package com.codersguidebook.music

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PlayQueueViewModel : ViewModel() {
    var playQueue = MutableLiveData<List<QueueItem>>()
    var currentQueueItemId = MutableLiveData<Int>()
    var currentlyPlayingSongMetadata = MutableLiveData<MediaMetadataCompat?>()
    var playbackDuration = MutableLiveData<Int>()
    var playbackPosition = MutableLiveData<Int>()
    var playbackState = MutableLiveData(STATE_NONE)
}
