package com.example.scribeai.features.noteedit

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.example.scribeai.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class NoteEditFormatManager(
        private val contentEditText: EditText,
        private val formattingToolbar: ChipGroup
) {
    private var selectionStart = 0
    private var selectionEnd = 0
    private var isUpdatingText = false

    init {
        setupFormatButtons()
        setupTextWatcher()
    }

    private fun setupFormatButtons() {
        // Bold
        formattingToolbar.let { chipGroup ->
            // Bold
            chipGroup.findViewById<Chip>(R.id.format_chip_bold)?.setOnClickListener {
                applyMarkdown("**")
            }

            // Italic
            chipGroup.findViewById<Chip>(R.id.format_chip_italic)?.setOnClickListener {
                applyMarkdown("*")
            }

            // H1
            chipGroup.findViewById<Chip>(R.id.format_chip_h1)?.setOnClickListener { applyHeading() }

            // List
            chipGroup.findViewById<Chip>(R.id.format_chip_list)?.setOnClickListener { applyList() }
        }
    }

    private fun setupTextWatcher() {
        contentEditText.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        if (!isUpdatingText) {
                            // Store current selection
                            selectionStart = contentEditText.selectionStart
                            selectionEnd = contentEditText.selectionEnd
                        }
                    }
                }
        )
    }

    private fun applyMarkdown(marker: String) {
        val text = contentEditText.text.toString()
        val start = contentEditText.selectionStart.coerceAtLeast(0)
        val end = contentEditText.selectionEnd.coerceAtLeast(0)

        val selectedText = text.substring(start.coerceAtMost(end), end.coerceAtLeast(start))
        val newText =
                if (selectedText.isEmpty()) {
                    // No selection, just insert markers and place cursor between them
                    text.substring(0, start) + marker + marker + text.substring(end)
                } else {
                    // Wrap selected text with markers
                    text.substring(0, start) + marker + selectedText + marker + text.substring(end)
                }

        updateText(newText, start, end, marker.length)
    }

    private fun applyHeading() {
        val text = contentEditText.text.toString()
        val start = contentEditText.selectionStart.coerceAtLeast(0)

        // Find the start of the current line
        var lineStart = start
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }

        // Check if line already starts with #
        val currentLine =
                text.substring(
                        lineStart,
                        (text.indexOf('\n', lineStart)).takeIf { it != -1 } ?: text.length
                )
        val newText =
                if (currentLine.trimStart().startsWith("# ")) {
                    // Remove heading
                    text.substring(0, lineStart) +
                            currentLine.replaceFirst("# ", "") +
                            text.substring(lineStart + currentLine.length)
                } else {
                    // Add heading
                    text.substring(0, lineStart) +
                            "# " +
                            currentLine +
                            text.substring(lineStart + currentLine.length)
                }

        updateText(newText, start, start, 0)
    }

    private fun applyList() {
        val text = contentEditText.text.toString()
        val start = contentEditText.selectionStart.coerceAtLeast(0)

        // Find the start of the current line
        var lineStart = start
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }

        // Check if line already starts with -
        val currentLine =
                text.substring(
                        lineStart,
                        (text.indexOf('\n', lineStart)).takeIf { it != -1 } ?: text.length
                )
        val newText =
                if (currentLine.trimStart().startsWith("- ")) {
                    // Remove list marker
                    text.substring(0, lineStart) +
                            currentLine.replaceFirst("- ", "") +
                            text.substring(lineStart + currentLine.length)
                } else {
                    // Add list marker
                    text.substring(0, lineStart) +
                            "- " +
                            currentLine +
                            text.substring(lineStart + currentLine.length)
                }

        updateText(newText, start, start, 0)
    }

    private fun updateText(newText: String, start: Int, end: Int, markerLength: Int) {
        isUpdatingText = true
        contentEditText.setText(newText)

        // Restore selection or place cursor appropriately
        if (start == end) {
            // No selection, place cursor between markers
            contentEditText.setSelection(start + markerLength)
        } else {
            // Restore selection including markers
            contentEditText.setSelection(start, end + (markerLength * 2))
        }
        isUpdatingText = false
    }
}
