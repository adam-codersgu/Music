package com.codersguidebook.music.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.codersguidebook.music.R
import com.codersguidebook.music.databinding.FragmentLibraryBinding
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private var viewPagerPosition: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        arguments?.let {
            val safeArgs = LibraryFragmentArgs.fromBundle(it)
            viewPagerPosition = safeArgs.position
        }

        _binding = FragmentLibraryBinding.inflate(inflater, container, false)

        val viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter
        binding.viewPager.currentItem = viewPagerPosition ?: 0

        val navView: NavigationView = requireActivity().findViewById(R.id.nav_view)
        val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> navView.setCheckedItem(R.id.nav_queue)
                    1 -> navView.setCheckedItem(R.id.nav_songs)
                }
            }
        }
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)

        val namesArray = arrayOf(getString(R.string.play_queue),getString(R.string.songs))
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = namesArray[position]
        }.attach()
        binding.tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
