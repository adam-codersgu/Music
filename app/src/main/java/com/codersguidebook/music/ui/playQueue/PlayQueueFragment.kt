package com.codersguidebook.music.ui.playQueue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.PlayQueueViewModel
import com.codersguidebook.music.databinding.FragmentPlayQueueBinding

class PlayQueueFragment : Fragment() {
    private var _binding: FragmentPlayQueueBinding? = null
    private val binding get() = _binding!!
    private val playQueueViewModel: PlayQueueViewModel by activityViewModels()
    private lateinit var mainActivity: MainActivity
    private lateinit var adapter: PlayQueueAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayQueueBinding.inflate(inflater, container, false)
        mainActivity = activity as MainActivity

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.itemAnimator = DefaultItemAnimator()

        adapter = PlayQueueAdapter(mainActivity, this)
        binding.root.adapter = adapter

        playQueueViewModel.playQueue.observe(viewLifecycleOwner) { playQueue ->
            if (adapter.playQueue.isEmpty()) {
                adapter.playQueue.addAll(playQueue)
                adapter.notifyItemRangeInserted(0, playQueue.size)
            } else {
                adapter.processNewPlayQueue(playQueue)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
