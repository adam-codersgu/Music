package com.codersguidebook.music.ui.songs

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.codersguidebook.music.MainActivity
import com.codersguidebook.music.R
import com.codersguidebook.music.Song
import com.codersguidebook.music.databinding.FragmentEditSongBinding
import java.io.FileNotFoundException
import java.io.IOException

class EditSongFragment : Fragment() {

    private var _binding: FragmentEditSongBinding? = null
    private val binding get() = _binding!!
    private var song: Song? = null
    private var newArtwork: Bitmap? = null
    private lateinit var mainActivity: MainActivity

    private val registerResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            try {
                result.data?.data?.let { uri ->
                    newArtwork = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(requireActivity().contentResolver, uri)
                    )
                    Glide.with(this)
                        .load(uri)
                        .centerCrop()
                        .into(binding.editSongArtwork)
                }
            } catch (_: FileNotFoundException) {
            } catch (_: IOException) { }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        arguments?.let {
            val safeArgs = EditSongFragmentArgs.fromBundle(it)
            song = safeArgs.song
        }

        _binding = FragmentEditSongBinding.inflate(inflater, container, false)
        mainActivity = activity as MainActivity
        setupMenu()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editSongTitle.text = SpannableStringBuilder(song?.title)
        binding.editSongArtist.text = SpannableStringBuilder(song?.artist)
        binding.editSongDisc.text = SpannableStringBuilder(song?.track.toString().substring(0, 1))
        binding.editSongTrack.text = SpannableStringBuilder(song?.track.toString().substring(1, 4)
            .toInt().toString())
        binding.editSongYear.text = SpannableStringBuilder(song!!.year)

        mainActivity.loadArtwork(song?.albumId, binding.editSongArtwork)

        binding.editSongArtwork.setOnClickListener {
            registerResult.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI))
        }

        binding.editSongArtworkIcon.setOnClickListener {
            registerResult.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI))
        }
    }

    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.search)?.isVisible = false
                menu.findItem(R.id.save)?.isVisible = true
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) { }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return menuItemSelected(menuItem)
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun menuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.save -> {
                val newTitle = binding.editSongTitle.text.toString()
                val newArtist = binding.editSongArtist.text.toString()
                val newDisc = binding.editSongDisc.text.toString()
                val newTrack = binding.editSongTrack.text.toString()
                val newYear = binding.editSongYear.text.toString()

                // Check no fields are blank
                if (newTitle.isNotEmpty() && newArtist.isNotEmpty() && newDisc.isNotEmpty() && newTrack.isNotEmpty() && newYear.isNotEmpty()) {
                    val completeTrack = when (newTrack.length) {
                        3 -> newDisc + newTrack
                        2 -> newDisc + "0" + newTrack
                        else -> newDisc + "00" + newTrack
                    }.toInt()

                    // Check something has actually been changed
                    if (newTitle != song!!.title || newArtist != song!!.artist || completeTrack != song!!.track || newYear != song!!.year || newArtwork != null) {

                        // Save the new artwork if the artwork has been changed
                        newArtwork?.let { artwork ->
                            mainActivity.saveImage(song?.albumId!!, artwork)
                        }

                        song!!.title = newTitle
                        song!!.artist = newArtist
                        song!!.track = completeTrack
                        song!!.year = newYear

                        mainActivity.updateSong(song!!)
                    }

                    Toast.makeText(activity, getString(R.string.details_saved), Toast.LENGTH_SHORT).show()
                    requireView().findNavController().popBackStack()
                } else Toast.makeText(activity, getString(R.string.check_fields_not_empty), Toast.LENGTH_SHORT).show()
                true
            }
            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
