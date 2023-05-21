package com.codersguidebook.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import android.view.Menu
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.codersguidebook.music.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private var currentPlaybackPosition = 0
    private var currentPlaybackDuration = 0
    private var currentQueueItemId = -1L
    private var playQueue = listOf<QueueItem>()
    private val playQueueViewModel: PlayQueueViewModel by viewModels()
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var sharedPreferences: SharedPreferences

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            super.onConnected()

            mediaBrowser.sessionToken.also { token ->
                val mediaControllerCompat = MediaControllerCompat(this@MainActivity, token)
                MediaControllerCompat.setMediaController(this@MainActivity, mediaControllerCompat)
            }

            MediaControllerCompat.getMediaController(this@MainActivity)
                .registerCallback(controllerCallback)
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            super.onPlaybackStateChanged(state)

            // TODO: Refresh the play queue
            refreshPlayQueue()
            if (state?.activeQueueItemId != currentQueueItemId) {
                currentQueueItemId = state?.activeQueueItemId ?: -1
                savePlayQueueId(currentQueueItemId)
            }

            playQueueViewModel.playbackState.value = state?.state ?: PlaybackStateCompat.STATE_NONE
            when (state?.state) {
                STATE_PLAYING, STATE_PAUSED -> {
                    currentPlaybackPosition = state.position.toInt()
                    state.extras?.let {
                        currentPlaybackDuration = it.getInt("duration", 0)
                        playQueueViewModel.playbackDuration.value = currentPlaybackDuration
                    }
                    playQueueViewModel.playbackPosition.value = currentPlaybackPosition
                }
                STATE_STOPPED -> {
                    currentPlaybackDuration = 0
                    playQueueViewModel.playbackDuration.value = 0
                    currentPlaybackPosition = 0
                    playQueueViewModel.playbackPosition.value = 0
                    playQueueViewModel.currentlyPlayingSongMetadata.value = null
                }
                STATE_ERROR -> refreshMusicLibrary()
                else -> return
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)

            if (metadata?.description?.mediaId !=
                playQueueViewModel.currentlyPlayingSongMetadata.value?.description?.mediaId) {
                playQueueViewModel.playbackPosition.value = 0
            }

            playQueueViewModel.currentlyPlayingSongMetadata.value = metadata
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MediaPlaybackService::class.java),
            connectionCallbacks,
            intent.extras
        )
        mediaBrowser.connect()

        createChannelForMediaPlayerNotification()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()

        MediaControllerCompat.getMediaController(this)?.apply {
            transportControls.stop()
            unregisterCallback(controllerCallback)
        }
        mediaBrowser.disconnect()
    }

    private fun createChannelForMediaPlayerNotification() {
        val channel = NotificationChannel(
            "music", "Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "All app notifications"
            setSound(null, null)
            setShowBadge(false)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun playNewPlayQueue(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false)
            = lifecycleScope.launch(Dispatchers.Default) {
        if (songs.isEmpty() || startIndex >= songs.size) {
            Toast.makeText(this@MainActivity, getString(R.string.error), Toast.LENGTH_LONG).show()
            return@launch
        }
        mediaController.transportControls.stop()

        val startSongIndex = if (shuffle) (songs.indices).random()
        else startIndex

        val startSongDesc = buildMediaDescription(songs[startSongIndex], startSongIndex.toLong())

        val mediaControllerCompat = MediaControllerCompat.getMediaController(this@MainActivity)
        mediaControllerCompat.addQueueItem(startSongDesc)
        skipToAndPlayQueueItem(startSongIndex.toLong())

        for ((index, song) in songs.withIndex()) {
            if (index == startSongIndex) continue
            val songDesc = buildMediaDescription(song, index.toLong())
            mediaControllerCompat.addQueueItem(songDesc, index)
        }

        when {
            shuffle -> setShuffleMode(SHUFFLE_MODE_ALL)
            mediaControllerCompat.shuffleMode == SHUFFLE_MODE_ALL -> setShuffleMode(SHUFFLE_MODE_NONE)
        }
    }

    private fun buildMediaDescription(song: Song, queueId: Long? = null): MediaDescriptionCompat {
        val extrasBundle = Bundle().apply {
            putString("album", song.album)
            putString("album_id", song.albumId)
            queueId?.let {
                putLong("queue_id", queueId)
            }
        }

        return MediaDescriptionCompat.Builder()
            .setExtras(extrasBundle)
            .setMediaId(song.songId.toString())
            .setSubtitle(song.artist)
            .setTitle(song.title)
            .build()
    }

    fun skipToAndPlayQueueItem(queueItemId: Long) {
        mediaController.transportControls.skipToQueueItem(queueItemId)
        mediaController.transportControls.play()
    }

    /* private fun setShuffleMode(shuffleMode: Int) {
        sharedPreferences.edit().apply {
            putInt(SHUFFLE_MODE, shuffleMode)
            apply()
        }

        val bundle = Bundle().apply {
            putInt(SHUFFLE_MODE, shuffleMode)
        }

        mediaController.sendCommand(SET_SHUFFLE_MODE, bundle, null)
    } */
}