package com.example.scribeai.features.noteedit

import android.app.Activity // Import Activity
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.scribeai.R
import com.google.android.material.chip.Chip // Added import
import com.google.android.material.chip.ChipGroup // Added import

class NoteEditTagManager(
        private val context: Context,
        private val tagChipGroup: ChipGroup,
        private val tagEditText: EditText,
        private val addTagButton: Button,
        private val viewModel: NoteEditViewModel
) {

    private var lastTagAddTime: Long = 0 // Timestamp of the last successful tag addition
    private val MIN_TIME_BETWEEN_ADDS_MS = 200 // Threshold to prevent double-adds

    fun setupTagInput() {
        addTagButton.setOnClickListener { addTagFromInput() }

        tagEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val currentTime = System.currentTimeMillis()
                // Check if Enter was pressed very shortly after a successful add
                if (currentTime - lastTagAddTime < MIN_TIME_BETWEEN_ADDS_MS) {
                    // Likely a double-trigger, just consume the event and ensure keyboard is hidden
                    if (context is Activity) NoteEditKeyboardUtil.hideKeyboard(context)
                    return@setOnEditorActionListener true // Consume event
                }

                // Not a double-trigger, proceed with normal logic
                val currentTagText = tagEditText.text.toString().trim()
                if (currentTagText.isNotEmpty()) {
                    // Field has text, add the tag
                    addTagFromInput() // This will update lastTagAddTime on success
                } else {
                    // Field was empty when Enter was pressed (and not a double-trigger). Show
                    // toast.
                    Toast.makeText(context, R.string.error_empty_tag, Toast.LENGTH_SHORT).show()
                    if (context is Activity) NoteEditKeyboardUtil.hideKeyboard(context)
                }
                // Consume the event regardless of whether text was added or toast was shown
                return@setOnEditorActionListener true
            }
            false // Not IME_ACTION_DONE
        }
    }

    private fun addTagFromInput() {
        val tagText = tagEditText.text.toString().trim()
        if (tagText.isEmpty()) {
            // If called via button click with empty text, do nothing.
            return
        }

        // Proceed only if tagText is not empty
        viewModel.addTag(tagText) // Add tag via ViewModel
        tagEditText.text?.clear() // Clear input field
        // Then hide keyboard (which should also handle focus)
        if (context is Activity) { // Check if context is an Activity
            NoteEditKeyboardUtil.hideKeyboard(context) // Pass only the activity
        }
        // Record the time of successful addition
        lastTagAddTime = System.currentTimeMillis()
        // The listener handles the empty case for IME_ACTION_DONE.
        // If the Add button is clicked with an empty field, we simply do nothing.
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
