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
            chipGroup.findViewById<Chip>(R.id.format_chip_h1)?.setOnClickListener {
                applyHeading("# ")
            }

            // H2 (New)
            chipGroup.findViewById<Chip>(R.id.format_chip_h2)?.setOnClickListener {
                applyHeading("## ")
            }

            // H3 (New)
            chipGroup.findViewById<Chip>(R.id.format_chip_h3)?.setOnClickListener {
                applyHeading("### ")
            }

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

        // For bold/italic, place cursor between markers if no selection, or after the selection +
        // markers
        val newCursorPos =
                if (start == end) {
                    start + marker.length // Place cursor between markers
                } else {
                    end + (marker.length * 2) // Place cursor after the end marker
                }
        updateText(newText, newCursorPos) // Call the corrected updateText
    }

    private fun applyHeading(headingMarker: String) {
        val text = contentEditText.text.toString()
        val originalCursorPos = contentEditText.selectionStart.coerceAtLeast(0)

        // Find the start of the current line
        var lineStart = originalCursorPos // Use originalCursorPos to find the line start
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }
        val lineEnd = (text.indexOf('\n', lineStart)).takeIf { it != -1 } ?: text.length
        val currentLineContent = text.substring(lineStart, lineEnd)

        // Determine if we are adding or removing the heading
        val isAdding = !currentLineContent.trimStart().startsWith(headingMarker)
        var newCursorOffset = 0

        val newText =
                if (isAdding) {
                    // Add heading marker
                    newCursorOffset = headingMarker.length
                    text.substring(0, lineStart) +
                            headingMarker +
                            currentLineContent +
                            text.substring(lineEnd)
                } else {
                    // Remove heading marker (handle potential multiple #s)
                    val trimmedLine = currentLineContent.trimStart()
                    val markerToRemove = trimmedLine.takeWhile { it == '#' } + " "
                    newCursorOffset = -markerToRemove.length
                    text.substring(0, lineStart) +
                            currentLineContent.replaceFirst(markerToRemove, "") +
                            text.substring(lineEnd)
                }

        // Calculate new cursor position relative to the original position within the line
        val cursorLineOffset = originalCursorPos - lineStart // This calculation should be fine now
        val newCursorPos = (lineStart + cursorLineOffset + newCursorOffset).coerceAtLeast(lineStart)

        updateText(newText, newCursorPos) // Call the corrected updateText
    }

    // applyList was missing its definition in the previous merge
    private fun applyList() {
        val text = contentEditText.text.toString()
        val originalCursorPos = contentEditText.selectionStart.coerceAtLeast(0)

        // Find the start of the current line
        var lineStart = originalCursorPos // Use originalCursorPos to find the line start
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }
        val lineEnd = (text.indexOf('\n', lineStart)).takeIf { it != -1 } ?: text.length
        val currentLineContent = text.substring(lineStart, lineEnd)
        val listMarker = "- "
        var newCursorOffset = 0

        val newText =
                if (currentLineContent.trimStart().startsWith(listMarker)) {
                    // Remove list marker
                    newCursorOffset = -listMarker.length
                    text.substring(0, lineStart) +
                            currentLineContent.replaceFirst(listMarker, "") +
                            text.substring(lineEnd)
                } else {
                    // Add list marker at the beginning of the line
                    newCursorOffset = listMarker.length
                    text.substring(0, lineStart) +
                            listMarker +
                            currentLineContent +
                            text.substring(lineEnd)
                }

        // Calculate new cursor position relative to the original position within the line
        val cursorLineOffset = originalCursorPos - lineStart // This calculation should be fine now
        val newCursorPos = (lineStart + cursorLineOffset + newCursorOffset).coerceAtLeast(lineStart)

        updateText(newText, newCursorPos) // Call the corrected updateText
    }

    // Simplified updateText, takes only the new text and desired cursor position
    // Removed erroneous 'private' modifiers from previous merge attempt
    private fun updateText(newText: String, newCursorPosition: Int) {
        isUpdatingText = true
        val currentScrollY = contentEditText.scrollY // Preserve scroll position
        contentEditText.setText(newText)
        // Ensure cursor position is valid
        val validCursorPos = newCursorPosition.coerceIn(0, newText.length)
        contentEditText.setSelection(validCursorPos)
        contentEditText.scrollTo(0, currentScrollY) // Restore scroll position
        isUpdatingText = false
    }
}
