package com.codersguidebook.music.ui.playQueue

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.PlayQueueViewModel
import com.codersguidebook.music.databinding.FragmentPlayQueueBinding

class PlayQueueFragment : Fragment() {
    private var _binding: FragmentPlayQueueBinding? = null
    private val binding get() = _binding!!
    private val playQueueViewModel: PlayQueueViewModel by activityViewModels()
    private lateinit var mainActivity: MainActivity
    private lateinit var adapter: PlayQueueAdapter

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : SimpleCallback(UP or DOWN, 0) {
            var to: Int? = null
            var queueItem: QueueItem? = null

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ACTION_STATE_DRAG) viewHolder?.itemView?.alpha = 0.5f
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f

                if (to != null && queueItem != null) {
                    playQueueViewModel.playQueue.value = adapter.playQueue
                    mainActivity.notifyQueueItemMoved(queueItem!!.queueId, to!!)
                    to = null
                    queueItem = null
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.layoutPosition
                to = target.layoutPosition
                if (from != to) {
                    queueItem = adapter.playQueue[from]
                    adapter.playQueue.removeAt(from)
                    adapter.playQueue.add(to!!, queueItem!!)
                    adapter.notifyItemMoved(from, to!!)
                }

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
        }
        ItemTouchHelper(simpleItemTouchCallback)
    }

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

        playQueueViewModel.currentQueueItemId.observe(viewLifecycleOwner) { position ->
            position?.let { adapter.changeCurrentlyPlayingQueueItemId(it) }
        }

        itemTouchHelper.attachToRecyclerView(binding.root)
    }

    override fun onResume() {
        super.onResume()

        val currentlyPlayingQueueItemIndex = adapter.playQueue.indexOfFirst {queueItem ->
            queueItem.queueId == adapter.currentlyPlayingQueueId
        }

        if (currentlyPlayingQueueItemIndex != -1) {
            (binding.root.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(currentlyPlayingQueueItemIndex, 0)
        }
    }

    fun startDragging(viewHolder: RecyclerView.ViewHolder) = itemTouchHelper.startDrag(viewHolder)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
