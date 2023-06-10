package com.codersguidebook.music.ui.search

import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.MusicDatabase
import com.codersguidebook.music.R
import com.codersguidebook.music.databinding.FragmentSearchBinding
import com.codersguidebook.music.ui.songs.SongsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private var musicDatabase: MusicDatabase? = null
    private var searchView: SearchView? = null
    private lateinit var adapter: SongsAdapter
    private lateinit var mainActivity: MainActivity
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        mainActivity = activity as MainActivity
        musicDatabase = MusicDatabase.getDatabase(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SongsAdapter(mainActivity)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = DefaultItemAnimator()

        setupMenu()

        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mainActivity.iconifySearchView()
                findNavController().popBackStack()
            }
        }

        mainActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    override fun onStop() {
        super.onStop()
        mainActivity.hideKeyboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        onBackPressedCallback.remove()
    }

    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                val searchItem = menu.findItem(R.id.search)
                searchView = searchItem.actionView as SearchView

                val onQueryListener = object : SearchView.OnQueryTextListener {
                    override fun onQueryTextChange(newText: String): Boolean {
                        search("%$newText%")
                        return true
                    }
                    override fun onQueryTextSubmit(query: String): Boolean = true
                }

                searchView?.apply {
                    isIconifiedByDefault = false
                    queryHint = getString(R.string.search_hint)
                    setOnQueryTextListener(onQueryListener)
                }
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) { }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun search(query: String) = lifecycleScope.launch(Dispatchers.IO) {
        binding.noResults.isGone = true
        val songs = musicDatabase!!.musicDao().getSongsLikeSearch(query).take(10)

        lifecycleScope.launch(Dispatchers.Main) {
            if (songs.isEmpty()) binding.noResults.isVisible = true
            adapter.processNewSongs(songs)
        }
    }
}
