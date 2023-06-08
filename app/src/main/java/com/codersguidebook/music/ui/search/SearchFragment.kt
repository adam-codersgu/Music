package com.codersguidebook.music.ui.search

import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.MusicDatabase
import com.codersguidebook.music.R
import com.codersguidebook.music.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private var musicDatabase: MusicDatabase? = null
    private var searchView: SearchView? = null
    private lateinit var mainActivity: MainActivity

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

        // TODO: Initialise the adapter and apply it to the RecyclerView

        binding.recyclerView.itemAnimator = DefaultItemAnimator()

        setupMenu()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
