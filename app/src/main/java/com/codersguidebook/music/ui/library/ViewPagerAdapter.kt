package com.codersguidebook.music.ui.library

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.codersguidebook.music.ui.playQueue.PlayQueueFragment
import com.codersguidebook.music.ui.songs.SongsFragment

class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return if (position == 0) PlayQueueFragment()
        else SongsFragment()
    }
}
