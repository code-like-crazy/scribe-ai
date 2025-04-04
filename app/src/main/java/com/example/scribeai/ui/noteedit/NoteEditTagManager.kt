package com.example.scribeai.ui.noteedit

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.scribeai.R
import com.google.android.material.chip.Chip // Added import
import com.google.android.material.chip.ChipGroup // Added import

// NoteEditViewModel is in the same package

class NoteEditTagManager(
        private val context: Context,
        private val tagChipGroup: ChipGroup,
        private val tagEditText: EditText,
        private val addTagButton: Button,
        private val viewModel: NoteEditViewModel
) {

    fun setupTagInput() {
        addTagButton.setOnClickListener { addTagFromInput() }

        tagEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTagFromInput()
                true // Consume the event
            } else {
                false // Do not consume the event
            }
        }
    }

    private fun addTagFromInput() {
        val tagText = tagEditText.text.toString().trim()
        if (tagText.isNotEmpty()) {
            viewModel.addTag(tagText) // Add tag via ViewModel
            tagEditText.text?.clear() // Clear input field
        } else {
            Toast.makeText(context, R.string.error_empty_tag, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateTagChips(tags: List<String>) {
        tagChipGroup.removeAllViews()
        tags.forEach { tag ->
            val chip =
                    Chip(context).apply {
                        text = tag
                        isCloseIconVisible = true
                        setOnCloseIconClickListener { viewModel.removeTag(tag) }
                        // Optional styling can be added here if needed
                    }
            tagChipGroup.addView(chip)
        }
    }
}
