package com.codersguidebook.music.ui.songs

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.MusicViewModel
import com.codersguidebook.music.R
import com.codersguidebook.music.Song
import com.codersguidebook.music.databinding.FragmentSongsBinding
import com.codersguidebook.recyclerviewfastscroller.RecyclerViewScrollbar

class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    private var isUpdating = false
    private var unhandledRequestReceived = false
    private lateinit var adapter: SongsAdapter
    private lateinit var musicViewModel: MusicViewModel
    private lateinit var mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        mainActivity = activity as MainActivity
        musicViewModel = ViewModelProvider(mainActivity)[MusicViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.itemAnimator = DefaultItemAnimator()

        binding.scrollbar.recyclerView = binding.recyclerView

        adapter = SongsAdapter(mainActivity, this)
        binding.recyclerView.adapter = adapter
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        musicViewModel.allSongs.observe(viewLifecycleOwner) {
            updateRecyclerView(it)
        }

        binding.recyclerView.addOnScrollListener(object: RecyclerViewScrollbar
        .OnScrollListener(binding.scrollbar) {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0 && binding.fab.visibility == View.VISIBLE) binding.fab.hide()
                else if (dy < 0 && binding.fab.visibility != View.VISIBLE) binding.fab.show()
            }
        })
    }

    private fun updateRecyclerView(songs: List<Song>) {
        if (isUpdating) {
            unhandledRequestReceived = true
            return
        }
        isUpdating = true

        binding.fab.setOnClickListener {
            mainActivity.playNewPlayQueue(songs, shuffle = true)
        }

        if (adapter.songs.isEmpty()) {
            adapter.songs.addAll(songs)
            adapter.notifyItemRangeInserted(0, songs.size)
        } else {
            adapter.processNewSongs(songs)
        }

        isUpdating = false
        if (unhandledRequestReceived) {
            unhandledRequestReceived = false
            musicViewModel.allSongs.value?.let { updateRecyclerView(it) }
        }
    }

    fun showPopup(view: View, song: Song) {
        PopupMenu(this.context, view).apply {
            inflate(R.menu.song_options)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.play_next -> mainActivity.playNext(song)
                    R.id.edit_metadata -> {
                        val action = SongsFragmentDirections.actionEditSong(song)
                        mainActivity.findNavController(R.id.nav_host_fragment).navigate(action)
                    }
                }
                true
            }
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
