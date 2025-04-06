package com.example.scribeai.features.noteedit

import android.app.Activity
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.example.scribeai.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class NoteEditTagManager(
        private val context: Context,
        private val tagChipGroup: ChipGroup,
        private val tagEditText: EditText,
        private val addTagButton: Button,
        private val viewModel: NoteEditViewModel
) {

    private var lastTagAddTime: Long = 0
    private val MIN_TIME_BETWEEN_ADDS_MS = 200

    fun setupTagInput() {
        addTagButton.setOnClickListener { addTagFromInput() }

        tagEditText.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTagAddTime < MIN_TIME_BETWEEN_ADDS_MS) {
                    if (context is Activity) NoteEditKeyboardUtil.hideKeyboard(context)
                    return@setOnEditorActionListener true
                }

                val currentTagText = tagEditText.text.toString().trim()
                if (currentTagText.isNotEmpty()) {
                    addTagFromInput()
                } else {
                    Toast.makeText(context, R.string.error_empty_tag, Toast.LENGTH_SHORT).show()
                    if (context is Activity) NoteEditKeyboardUtil.hideKeyboard(context)
                }
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun addTagFromInput() {
        val tagText = tagEditText.text.toString().trim()
        if (tagText.isEmpty()) {
            return
        }

        viewModel.addTag(tagText)
        tagEditText.text?.clear()
        if (context is Activity) {
            NoteEditKeyboardUtil.hideKeyboard(context)
        }
        lastTagAddTime = System.currentTimeMillis()
    }

    fun updateTagChips(tags: List<String>) {
        tagChipGroup.removeAllViews()
        tags.forEach { tag ->
            val chip =
                    Chip(context).apply {
                        text = tag
                        isCloseIconVisible = true
                        setOnCloseIconClickListener { viewModel.removeTag(tag) }
                    }
            tagChipGroup.addView(chip)
        }
    }
}
