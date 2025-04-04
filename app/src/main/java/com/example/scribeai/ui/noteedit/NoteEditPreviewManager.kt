package com.example.scribeai.ui.noteedit

import android.content.Context
import android.net.Uri
import android.view.View
import com.bumptech.glide.Glide
import com.example.scribeai.R
import com.example.scribeai.databinding.ActivityNoteEditBinding

class NoteEditPreviewManager(
        private val context: Context,
        private val binding: ActivityNoteEditBinding
) {

    fun showImagePreview(imageUri: Uri) {
        binding.imagePreview.visibility = View.VISIBLE
        Glide.with(context)
                .load(imageUri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_broken_image)
                .into(binding.imagePreview)
    }

    fun hideImagePreview() {
        binding.imagePreview.visibility = View.GONE
        // Clear Glide resources when hiding to free up memory
        // Context null check is redundant for non-nullable constructor parameter
        Glide.with(context).clear(binding.imagePreview)
    }

    fun hideAllPreviews() {
        hideImagePreview()
    }
}
