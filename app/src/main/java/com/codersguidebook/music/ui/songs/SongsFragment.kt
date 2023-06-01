package com.codersguidebook.music.ui.songs

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import android.view.View
import android.view.ViewGroup
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

        // TODO: Initialise the SongsAdapter class here

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    OUTTAKE

    Also, the onViewCreated method attaches an onClick listener to the floating action button from the fragment_songs layout. If the user clicks the button, then the MainActivity class’s playNewSongs method will play the user’s entire music library on shuffle.


    // Shuffle the music library then play it
    binding.fab.setOnClickListener {
        mainActivity.playNewSongs(completeLibrary, 0, true)
    }

    private fun initialiseAdapter() {
        adapter = SongsAdapter(mainActivity)
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    private fun requestNewData() {
        musicLibraryViewModel.allSongs.value?.let { updateRecyclerView(it) }
    }
}
