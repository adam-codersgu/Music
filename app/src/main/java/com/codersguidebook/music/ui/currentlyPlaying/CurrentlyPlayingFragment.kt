package com.codersguidebook.music.ui.currentlyPlaying

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL
import android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE
import android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE
import android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.PlayQueueViewModel
import com.codersguidebook.music.R
import com.codersguidebook.music.databinding.FragmentCurrentlyPlayingBinding
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class CurrentlyPlayingFragment : Fragment() {

    private val playQueueViewModel: PlayQueueViewModel by activityViewModels()
    private var _binding: FragmentCurrentlyPlayingBinding? = null
    private val binding get() = _binding!!
    private var fastForwarding = false
    private var fastRewinding = false
    private lateinit var mainActivity: MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(android.R.transition.move)
        sharedElementReturnTransition = TransitionInflater.from(context).inflateTransition(android.R.transition.move)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurrentlyPlayingBinding.inflate(inflater, container, false)
        mainActivity = activity as MainActivity
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The currently playing fragment will overlay the active fragment from the
        // mobile_navigation navigation graph. We need to intercept touch events
        // that would otherwise reach the underlying fragment
        binding.root.setOnTouchListener { _, _ ->
            return@setOnTouchListener true
        }

        playQueueViewModel.currentlyPlayingSongMetadata.observe(viewLifecycleOwner) {
            updateCurrentlyDisplayedMetadata(it)
        }

        playQueueViewModel.playbackState.observe(viewLifecycleOwner) { state ->
            if (state == PlaybackStateCompat.STATE_PLAYING) binding.btnPlay.setImageResource(R.drawable.ic_pause)
            else binding.btnPlay.setImageResource(R.drawable.ic_play)
        }

        playQueueViewModel.playbackDuration.observe(viewLifecycleOwner) { duration ->
            duration?.let {
                binding.currentSeekBar.max = it
                binding.currentMax.text = SimpleDateFormat("mm:ss", Locale.UK).format(it)
            }
        }

        playQueueViewModel.playbackPosition.observe(viewLifecycleOwner) { position ->
            position?.let {
                binding.currentSeekBar.progress = position
                binding.currentPosition.text = SimpleDateFormat("mm:ss", Locale.UK).format(it)
            }
        }

        binding.btnPlay.setOnClickListener { mainActivity.playPauseControl() }

        binding.btnBackward.setOnClickListener{
            if (fastRewinding) fastRewinding = false
            else mainActivity.skipBack()
        }

        binding.btnBackward.setOnLongClickListener {
            fastRewinding = true
            lifecycleScope.launch {
                do {
                    mainActivity.fastRewind()
                    delay(500)
                } while (fastRewinding)
            }
            return@setOnLongClickListener false
        }

        binding.btnForward.setOnClickListener{
            if (fastForwarding) fastForwarding = false
            else mainActivity.skipForward()
        }

        binding.btnForward.setOnLongClickListener {
            fastForwarding = true
            lifecycleScope.launch {
                do {
                    mainActivity.fastForward()
                    delay(500)
                } while (fastForwarding)
            }
            return@setOnLongClickListener false
        }

        val accent = MaterialColors.getColor(mainActivity, com.google.android.material.R.attr.colorAccent, Color.CYAN)
        val onSurface = MaterialColors.getColor(mainActivity, com.google.android.material.R.attr.colorOnSurface, Color.LTGRAY)
        val onSurface60 = MaterialColors.compositeARGBWithAlpha(onSurface, 153)

        if (mainActivity.getShuffleMode() == SHUFFLE_MODE_ALL) {
            binding.currentButtonShuffle.setColorFilter(accent)
        }

        binding.currentButtonShuffle.setOnClickListener{
            if (mainActivity.toggleShuffleMode()) binding.currentButtonShuffle.setColorFilter(accent)
            else binding.currentButtonShuffle.setColorFilter(onSurface60)
        }

        when (mainActivity.getRepeatMode()) {
            REPEAT_MODE_ALL -> binding.currentButtonRepeat.setColorFilter(accent)
            REPEAT_MODE_ONE -> {
                binding.currentButtonRepeat.setColorFilter(accent)
                binding.currentButtonRepeat.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_repeat_one))
            }
        }

        binding.currentButtonRepeat.setOnClickListener {
            when (mainActivity.toggleRepeatMode()) {
                REPEAT_MODE_NONE -> {
                    binding.currentButtonRepeat.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_repeat))
                    binding.currentButtonRepeat.setColorFilter(onSurface60)
                }
                REPEAT_MODE_ALL -> {
                    binding.currentButtonRepeat.setColorFilter(accent)
                }
                REPEAT_MODE_ONE -> {
                    binding.currentButtonRepeat.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_repeat_one))
                }
            }
        }

        binding.currentClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.artwork.setOnClickListener {
            showPopup()
        }

        binding.currentSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mainActivity.seekTo(progress)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        mainActivity.hideStatusBars(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        mainActivity.hideStatusBars(false)
    }

    private fun updateCurrentlyDisplayedMetadata(metadata: MediaMetadataCompat?) = lifecycleScope.launch(
        Dispatchers.Main) {
        binding.title.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        binding.artist.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        binding.album.text = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)

        if (metadata != null) {
            val albumId = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            mainActivity.loadArtwork(albumId, binding.artwork)
        } else {
            Glide.with(mainActivity)
                .clear(binding.artwork)
        }
    }

    private fun showPopup() {
        PopupMenu(this.context, binding.currentClose).apply {
            inflate(R.menu.currently_playing_menu)

            setForceShowIcon(true)

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.search -> {
                        findNavController().popBackStack()
                        mainActivity.findNavController(R.id.nav_host_fragment).navigate(R.id.nav_search)
                    }
                    R.id.queue -> {
                        findNavController().popBackStack()
                        mainActivity.findNavController(R.id.nav_host_fragment).navigate(R.id.nav_queue)
                    }
                }
                true
            }
            show()
        }
    }

}
