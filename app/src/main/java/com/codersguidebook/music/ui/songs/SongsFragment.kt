package com.codersguidebook.music.ui.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.MusicViewModel
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

        adapter = SongsAdapter(mainActivity)
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

        adapter.processNewSongs(songs)

        isUpdating = false
        if (unhandledRequestReceived) {
            unhandledRequestReceived = false
            musicViewModel.allSongs.value?.let { updateRecyclerView(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
