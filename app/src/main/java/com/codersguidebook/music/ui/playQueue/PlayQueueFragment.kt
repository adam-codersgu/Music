package com.codersguidebook.music.ui.playQueue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.databinding.FragmentPlayQueueBinding

class PlayQueueFragment : Fragment() {
    private var _binding: FragmentPlayQueueBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainActivity: MainActivity

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

        // TODO: Initialise PlayQueueAdapter here
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
