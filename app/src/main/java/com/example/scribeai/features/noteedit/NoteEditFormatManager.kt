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
        formattingToolbar.let { chipGroup ->
            chipGroup.findViewById<Chip>(R.id.format_chip_bold)?.setOnClickListener {
                applyMarkdown("**")
            }

            chipGroup.findViewById<Chip>(R.id.format_chip_italic)?.setOnClickListener {
                applyMarkdown("*")
            }

            chipGroup.findViewById<Chip>(R.id.format_chip_h1)?.setOnClickListener {
                applyHeading("# ")
            }

            chipGroup.findViewById<Chip>(R.id.format_chip_h2)?.setOnClickListener {
                applyHeading("## ")
            }

            chipGroup.findViewById<Chip>(R.id.format_chip_h3)?.setOnClickListener {
                applyHeading("### ")
            }

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
                    text.substring(0, start) + marker + marker + text.substring(end)
                } else {
                    text.substring(0, start) + marker + selectedText + marker + text.substring(end)
                }

        val newCursorPos =
                if (start == end) {
                    start + marker.length
                } else {
                    end + (marker.length * 2)
                }
        updateText(newText, newCursorPos)
    }

    private fun applyHeading(headingMarker: String) {
        val text = contentEditText.text.toString()
        val originalCursorPos = contentEditText.selectionStart.coerceAtLeast(0)

        var lineStart = originalCursorPos
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }
        val lineEnd = (text.indexOf('\n', lineStart)).takeIf { it != -1 } ?: text.length
        val currentLineContent = text.substring(lineStart, lineEnd)

        val isAdding = !currentLineContent.trimStart().startsWith(headingMarker)
        var newCursorOffset = 0

        val newText =
                if (isAdding) {
                    newCursorOffset = headingMarker.length
                    text.substring(0, lineStart) +
                            headingMarker +
                            currentLineContent +
                            text.substring(lineEnd)
                } else {
                    val trimmedLine = currentLineContent.trimStart()
                    val markerToRemove = trimmedLine.takeWhile { it == '#' } + " "
                    newCursorOffset = -markerToRemove.length
                    text.substring(0, lineStart) +
                            currentLineContent.replaceFirst(markerToRemove, "") +
                            text.substring(lineEnd)
                }

        val cursorLineOffset = originalCursorPos - lineStart
        val newCursorPos = (lineStart + cursorLineOffset + newCursorOffset).coerceAtLeast(lineStart)

        updateText(newText, newCursorPos)
    }

    private fun applyList() {
        val text = contentEditText.text.toString()
        val originalCursorPos = contentEditText.selectionStart.coerceAtLeast(0)

        var lineStart = originalCursorPos
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }
        val lineEnd = (text.indexOf('\n', lineStart)).takeIf { it != -1 } ?: text.length
        val currentLineContent = text.substring(lineStart, lineEnd)
        val listMarker = "- "
        var newCursorOffset = 0

        val newText =
                if (currentLineContent.trimStart().startsWith(listMarker)) {
                    newCursorOffset = -listMarker.length
                    text.substring(0, lineStart) +
                            currentLineContent.replaceFirst(listMarker, "") +
                            text.substring(lineEnd)
                } else {
                    newCursorOffset = listMarker.length
                    text.substring(0, lineStart) +
                            listMarker +
                            currentLineContent +
                            text.substring(lineEnd)
                }

        val cursorLineOffset = originalCursorPos - lineStart
        val newCursorPos = (lineStart + cursorLineOffset + newCursorOffset).coerceAtLeast(lineStart)

        updateText(newText, newCursorPos)
    }

    private fun updateText(newText: String, newCursorPosition: Int) {
        isUpdatingText = true
        val currentScrollY = contentEditText.scrollY
        contentEditText.setText(newText)
        val validCursorPos = newCursorPosition.coerceIn(0, newText.length)
        contentEditText.setSelection(validCursorPos)
        contentEditText.scrollTo(0, currentScrollY)
        isUpdatingText = false
    }
}
