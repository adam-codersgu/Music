package com.codersguidebook.music

import android.content.*
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.media.session.PlaybackState.STATE_NONE
import android.media.session.PlaybackState.STATE_SKIPPING_TO_NEXT
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import android.text.TextUtils
import android.view.KeyEvent
import android.widget.Toast
import androidx.media.MediaBrowserServiceCompat
import java.io.IOException

class MediaPlaybackService : MediaBrowserServiceCompat(), OnErrorListener {

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

    private val mediaSessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            val keyEvent: KeyEvent? = mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)

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
                // Refresh the notification and metadata so user can see the song has changed
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
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        setMediaPlaybackState(STATE_ERROR)
        mediaSessionCompat.controller.transportControls.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Toast.makeText(application, getString(R.string.error), Toast.LENGTH_LONG).show()
        return true
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

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserService.BrowserRoot? {
        return if (TextUtils.equals(clientPackageName, packageName)) {
            MediaBrowserService.BrowserRoot(getString(R.string.app_name), null)
        } else null
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(null)
    }
}
