package com.codersguidebook.music

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaPlayer
import android.media.MediaPlayer.MEDIA_ERROR_IO
import android.media.MediaPlayer.MEDIA_ERROR_MALFORMED
import android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.support.v4.media.session.PlaybackStateCompat.*
import android.text.TextUtils
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class MediaPlaybackService : MediaBrowserServiceCompat(), MediaPlayer.OnErrorListener {

    private val channelId = "music"
    private var currentlyPlayingQueueItemId = -1L
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private val playQueue: MutableList<QueueItem> = mutableListOf()
    private lateinit var audioFocusRequest: AudioFocusRequest
    private lateinit var mediaSessionCompat: MediaSessionCompat

    private val afChangeListener = OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AUDIOFOCUS_LOSS, AUDIOFOCUS_LOSS_TRANSIENT -> {
                mediaSessionCompat.controller.transportControls.pause()
            }
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer?.setVolume(0.3f, 0.3f)
            AUDIOFOCUS_GAIN -> mediaPlayer?.setVolume(1.0f, 1.0f)
        }
    }

    private var playbackPositionRunnable = object : Runnable {
        override fun run() {
            try {
                if (mediaPlayer?.isPlaying == true) setMediaPlaybackState(STATE_PLAYING)
            } finally {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val mediaSessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Pre-SDK 33
                @Suppress("DEPRECATION")
                mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            } else {
                // SDK 33 and up
                mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            }

            keyEvent?.let { event ->
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (mediaPlayer?.isPlaying == true) onPause()
                        else onPlay()
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> onPlay()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> onPause()
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> onSkipToPrevious()
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> onSkipToNext()
                }
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onPrepare() {
            super.onPrepare()

            if (playQueue.isEmpty()) {
                onError(mediaPlayer, MEDIA_ERROR_UNKNOWN, 0)
                return
            }

            // If no queue item ID has been set, then start from the beginning of the play queue
            if (currentlyPlayingQueueItemId == -1L) currentlyPlayingQueueItemId = playQueue[0].queueId

            mediaPlayer?.apply {
                stop()
                release()
            }

            try {
                val currentQueueItem = getCurrentQueueItem()
                val currentQueueItemUri = currentQueueItem?.description?.mediaId?.let {
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        it.toLong())
                }
                if (currentQueueItemUri == null) {
                    onError(mediaPlayer, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_MALFORMED)
                    return
                }
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(application, currentQueueItemUri)
                    setOnErrorListener(this@MediaPlaybackService)
                    prepare()
                }
                // Refresh the notification and metadata so the user can see the song has changed
                setCurrentMetadata()
                refreshNotification()
                setMediaPlaybackState(STATE_NONE)
            } catch (_: IOException) {
                onError(mediaPlayer, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_IO)
            } catch (_: IllegalStateException) {
                onError(mediaPlayer, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_IO)
            } catch (_: IllegalArgumentException) {
                onError(mediaPlayer, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_MALFORMED)
            }
        }

        override fun onPlay() {
            super.onPlay()

            try {
                if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                    val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                    audioFocusRequest = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN).run {
                        setAudioAttributes(AudioAttributes.Builder().run {
                            setOnAudioFocusChangeListener(afChangeListener)
                            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            build()
                        })
                        build()
                    }

                    val audioFocusRequestOutcome = audioManager.requestAudioFocus(audioFocusRequest)
                    if (audioFocusRequestOutcome == AUDIOFOCUS_REQUEST_GRANTED) {
                        startService(Intent(applicationContext, MediaBrowserService::class.java))
                        mediaSessionCompat.isActive = true
                        try {
                            mediaPlayer?.apply {
                                start()

                                setOnCompletionListener {
                                    val repeatMode = mediaSessionCompat.controller.repeatMode
                                    when {
                                        repeatMode == REPEAT_MODE_ONE -> {}
                                        repeatMode == REPEAT_MODE_ALL ||
                                                playQueue.isNotEmpty() &&
                                                playQueue[playQueue.size - 1].queueId
                                                != currentlyPlayingQueueItemId -> {
                                            onSkipToNext()
                                            return@setOnCompletionListener
                                        }
                                        else -> {
                                            onStop()
                                            return@setOnCompletionListener
                                        }
                                    }

                                    onPrepare()
                                    onPlay()
                                }
                            }
                            refreshNotification()
                            setMediaPlaybackState(STATE_PLAYING, getBundleWithSongDuration())
                        } catch (_: NullPointerException) {
                            onError(mediaPlayer, MEDIA_ERROR_UNKNOWN, 0)
                        }
                    }
                }
            } catch (_: IllegalStateException) {
                onError(mediaPlayer, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_IO)
            }
        }

        override fun onPause() {
            super.onPause()
            mediaPlayer?.pause()
            setMediaPlaybackState(STATE_PAUSED, getBundleWithSongDuration())
            refreshNotification()
        }

        override fun onSkipToQueueItem(id: Long) {
            super.onSkipToQueueItem(id)

            if (playQueue.find { it.queueId == id} != null) {
                val playbackState = mediaSessionCompat.controller.playbackState.state
                currentlyPlayingQueueItemId = id
                onPrepare()
                if (playbackState == STATE_PLAYING || playbackState == STATE_SKIPPING_TO_NEXT) {
                    onPlay()
                }
            }
        }

        override fun onSkipToNext() {
            super.onSkipToNext()

            val repeatMode = mediaSessionCompat.controller.repeatMode
            currentlyPlayingQueueItemId = when {
                playQueue.isNotEmpty() &&
                        playQueue[playQueue.size - 1].queueId != currentlyPlayingQueueItemId -> {
                    val indexOfCurrentQueueItem = playQueue.indexOfFirst {
                        it.queueId == currentlyPlayingQueueItemId
                    }
                    playQueue[indexOfCurrentQueueItem + 1].queueId
                }
                // We are at the end of the queue. Check whether we should start over from the beginning
                repeatMode == REPEAT_MODE_ALL -> playQueue[0].queueId
                else -> return
            }

            onSkipToQueueItem(currentlyPlayingQueueItemId)
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()

            if (playQueue.isNotEmpty()) {
                if (mediaPlayer != null && mediaPlayer!!.currentPosition > 5000 ||
                    currentlyPlayingQueueItemId == playQueue[0].queueId) onSeekTo(0L)
                else {
                    val indexOfCurrentQueueItem = playQueue.indexOfFirst {
                        it.queueId == currentlyPlayingQueueItemId
                    }
                    currentlyPlayingQueueItemId = playQueue[indexOfCurrentQueueItem - 1].queueId
                    onSkipToQueueItem(currentlyPlayingQueueItemId)
                }
            }
        }

        override fun onStop() {
            super.onStop()

            playQueue.clear()
            mediaSessionCompat.setQueue(playQueue)
            currentlyPlayingQueueItemId = -1L
            if (mediaPlayer != null) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                try {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.abandonAudioFocusRequest(audioFocusRequest)
                } catch (_: UninitializedPropertyAccessException){ }
            }
            setMediaPlaybackState(STATE_STOPPED)
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            super.onSeekTo(pos)

            mediaPlayer?.apply {
                if (pos > this.duration.toLong()) return@apply

                val wasPlaying = this.isPlaying
                if (wasPlaying) this.pause()

                this.seekTo(pos.toInt())

                if (wasPlaying) {
                    this.start()
                    setMediaPlaybackState(STATE_PLAYING, getBundleWithSongDuration())
                } else setMediaPlaybackState(STATE_PAUSED, getBundleWithSongDuration())
            }
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat?) {
            onAddQueueItem(description, playQueue.size)
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat?, index: Int) {
            super.onAddQueueItem(description, index)

            val sortedQueue = playQueue.sortedByDescending {
                it.queueId
            }
            val presetQueueId = description?.extras?.getLong("queue_id")
            val queueId = when {
                presetQueueId != null && sortedQueue.find { it.queueId == presetQueueId } == null -> {
                    presetQueueId
                }
                sortedQueue.isNotEmpty() -> sortedQueue[0].queueId + 1
                else -> 0
            }

            val queueItem = QueueItem(description, queueId)
            try {
                playQueue.add(index, queueItem)
            } catch (exception: IndexOutOfBoundsException) {
                playQueue.add(playQueue.size, queueItem)
            }

            mediaSessionCompat.setQueue(playQueue)
        }

        override fun onFastForward() {
            super.onFastForward()

            val newPlaybackPosition = mediaPlayer?.currentPosition?.plus(5000) ?: return
            if (newPlaybackPosition > (mediaPlayer?.duration ?: return)) onSkipToNext()
            else onSeekTo(newPlaybackPosition.toLong())
        }

        override fun onRewind() {
            super.onRewind()

            val newPlaybackPosition = mediaPlayer?.currentPosition?.minus(5000) ?: return
            if (newPlaybackPosition < 0) onSkipToPrevious()
            else onSeekTo(newPlaybackPosition.toLong())
        }

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            super.onCommand(command, extras, cb)

            when (command) {
                "MOVE_QUEUE_ITEM" -> {
                    extras?.let {
                        val queueItemId = it.getLong("queueItemId", -1L)
                        val newIndex = it.getInt("newIndex", -1)
                        if (queueItemId == -1L || newIndex == -1 || newIndex >= playQueue.size) return@let

                        val oldIndex = playQueue.indexOfFirst { queueItem -> queueItem.queueId == queueItemId }
                        if (oldIndex == -1) return@let
                        val queueItem = playQueue[oldIndex]
                        playQueue.removeAt(oldIndex)
                        playQueue.add(newIndex, queueItem)
                        mediaSessionCompat.setQueue(playQueue)
                    }
                }

                "REMOVE_QUEUE_ITEM" -> {
                    extras?.let {
                        val queueItemId = extras.getLong("queueItemId", -1L)
                        when (queueItemId) {
                            -1L -> return@let
                            currentlyPlayingQueueItemId -> onSkipToNext()
                        }
                        playQueue.removeIf { it.queueId == queueItemId }
                        setPlayQueue()
                    }
                }

                "SET_REPEAT_MODE" -> {
                    extras?.let {
                        val repeatMode = extras.getInt("REPEAT_MODE", REPEAT_MODE_NONE)
                        mediaSessionCompat.setRepeatMode(repeatMode)
                    }
                }

                "SET_SHUFFLE_MODE" -> {
                    extras?.let {
                        val shuffleMode = extras.getInt("SHUFFLE_MODE", SHUFFLE_MODE_NONE)
                        mediaSessionCompat.setShuffleMode(shuffleMode)

                        if (shuffleMode == SHUFFLE_MODE_ALL) {
                            getCurrentQueueItem()?.let { currentQueueItem ->
                                playQueue.remove(currentQueueItem)
                                playQueue.shuffle()
                                playQueue.add(0, currentQueueItem)
                            }
                        } else {
                            playQueue.sortBy { it.queueId }
                        }

                        setPlayQueue()
                    }
                }

                "UPDATE_QUEUE_ITEM" -> {
                    extras?.let {
                        val queueItemId = it.getLong("queue_id")

                        val index = playQueue.indexOfFirst { item ->
                            item.queueId == queueItemId
                        }

                        if (index == -1) return

                        val extrasBundle = Bundle().apply {
                            putString("album", it.getString("album"))
                            putString("album_id", it.getString("album_id"))
                        }

                        val mediaDescription = MediaDescriptionCompat.Builder()
                            .setExtras(extrasBundle)
                            .setMediaId(playQueue[index].description.mediaId)
                            .setSubtitle(it.getString("artist"))
                            .setTitle(it.getString("title"))
                            .build()

                        playQueue.removeAt(index)
                        val updatedQueueItem = QueueItem(mediaDescription, queueItemId)
                        playQueue.add(index, updatedQueueItem)

                        if (queueItemId == currentlyPlayingQueueItemId) {
                            setCurrentMetadata()
                            refreshNotification()
                        }
                        setPlayQueue()
                    }
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (mediaPlayer != null && mediaPlayer!!.isPlaying) mediaSessionCallback.onPause()
        }
    }

    override fun onCreate() {
        super.onCreate()

        mediaSessionCompat = MediaSessionCompat(baseContext, channelId).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)
            setCallback(mediaSessionCallback)
            setSessionToken(sessionToken)
            val builder = Builder().setActions(ACTION_PLAY)
            setPlaybackState(builder.build())
        }

        val filter = IntentFilter(ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(noisyReceiver, filter)
        playbackPositionRunnable.run()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        setMediaPlaybackState(STATE_ERROR)
        mediaSessionCompat.controller.transportControls.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Toast.makeText(application, getString(R.string.error), Toast.LENGTH_LONG).show()
        return true
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        intent.action?.let {
            when (it) {
                "play" -> mediaSessionCallback.onPlay()
                "pause" -> mediaSessionCallback.onPause()
                "next" -> mediaSessionCallback.onSkipToNext()
                "previous" -> mediaSessionCallback.onSkipToPrevious()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionCompat.controller.transportControls.stop()
        handler.removeCallbacks(playbackPositionRunnable)
        unregisterReceiver(noisyReceiver)
        mediaSessionCompat.release()
        NotificationManagerCompat.from(this).cancel(1)
    }

    private fun getCurrentQueueItem(): QueueItem? {
        return playQueue.find {
            it.queueId == currentlyPlayingQueueItemId
        }
    }

    private fun setMediaPlaybackState(state: Int, bundle: Bundle? = null) {
        val playbackPosition = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val playbackSpeed = mediaPlayer?.playbackParams?.speed ?: 0f
        val playbackStateBuilder = Builder()
            .setState(state, playbackPosition, playbackSpeed)
            .setActiveQueueItemId(currentlyPlayingQueueItemId)
        bundle?.let { playbackStateBuilder.setExtras(it) }
        mediaSessionCompat.setPlaybackState(playbackStateBuilder.build())
    }

    private fun getBundleWithSongDuration(): Bundle {
        val playbackDuration = mediaPlayer?.duration ?: 0
        return Bundle().apply {
            putInt("duration", playbackDuration)
        }
    }

    private fun setCurrentMetadata() {
        val currentQueueItem = getCurrentQueueItem() ?: return
        val currentQueueItemDescription = currentQueueItem.description
        val metadataBuilder= MediaMetadataCompat.Builder().apply {
            putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentQueueItemDescription.mediaId)
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentQueueItemDescription.title.toString())
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentQueueItemDescription.subtitle.toString())
            val extras = currentQueueItemDescription.extras
            val albumName = extras?.getString("album") ?: "Unknown album"
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, albumName)
            val albumId = extras?.getString("album_id")
            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getArtworkByAlbumId(albumId))
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, albumId)
        }
        mediaSessionCompat.setMetadata(metadataBuilder.build())
    }

    private fun getArtworkByAlbumId(albumId: String?): Bitmap {
        albumId?.let {
            try {
                val directory = ContextWrapper(applicationContext).getDir("albumArt", Context.MODE_PRIVATE)
                val imageFile = File(directory, "$albumId.jpg")
                if (imageFile.exists()) {
                    return BitmapFactory.decodeStream(FileInputStream(imageFile))
                }
            } catch (_: Exception) { }
        }
        // If an error has occurred or the album ID is null, then return a default artwork image
        return BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_launcher_foreground)
    }

    private fun refreshNotification() {
        val isPlaying = mediaPlayer?.isPlaying ?: false
        val playPauseIntent = if (isPlaying) {
            Intent(applicationContext, MediaPlaybackService::class.java).setAction("pause")
        } else Intent(applicationContext, MediaPlaybackService::class.java).setAction("play")
        val nextIntent = Intent(applicationContext, MediaPlaybackService::class.java).setAction("next")
        val prevIntent = Intent(applicationContext, MediaPlaybackService::class.java).setAction("previous")

        val intent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.setPackage(null)
            ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val activityIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(applicationContext, channelId).apply {
            val mediaMetadata = mediaSessionCompat.controller.metadata

            // Previous button
            addAction(
                NotificationCompat.Action(R.drawable.ic_back, getString(R.string.play_prev),
                    PendingIntent.getService(applicationContext, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE)
                )
            )

            // Play/pause button
            val playOrPause = if (isPlaying) R.drawable.ic_pause
            else R.drawable.ic_play
            addAction(
                NotificationCompat.Action(playOrPause, getString(R.string.play_pause),
                    PendingIntent.getService(applicationContext, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)
                )
            )

            // Next button
            addAction(
                NotificationCompat.Action(R.drawable.ic_next, getString(R.string.play_next),
                    PendingIntent.getService(applicationContext, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE)
                )
            )

            setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSessionCompat.sessionToken)
            )

            val smallIcon = if (isPlaying) R.drawable.play
            else R.drawable.pause
            setSmallIcon(smallIcon)

            setContentIntent(activityIntent)

            // Add the metadata for the currently playing track
            setContentTitle(mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
            setContentText(mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
            setLargeIcon(mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))

            // Make the transport controls visible on the lockscreen
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            priority = NotificationCompat.PRIORITY_DEFAULT
        }
        // Display the notification and place the service in the foreground
        startForeground(1, builder.build())
    }

    private fun setPlayQueue() {
        mediaSessionCompat.setQueue(playQueue)
        setMediaPlaybackState(mediaSessionCompat.controller.playbackState.state)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return if (TextUtils.equals(clientPackageName, packageName)) {
            BrowserRoot(getString(R.string.app_name), null)
        } else null
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(null)
    }
}
