package com.example.scribeai.features.noteedit

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater // Add LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.example.scribeai.R
import com.example.scribeai.core.data.AppDatabase
import com.example.scribeai.core.data.NoteRepository
import com.example.scribeai.databinding.ActivityNoteEditBinding
import com.example.scribeai.databinding.DialogImageSourceContentBinding // Import correct binding
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class NoteEditActivity : AppCompatActivity(), NoteEditResultCallback, GeminiProcessorCallback {

    private lateinit var binding: ActivityNoteEditBinding

    private val currentNoteId: Long? by lazy {
        intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it != -1L }
    }

    private val viewModel: NoteEditViewModel by viewModels {
        NoteEditViewModelFactory(
                NoteRepository(AppDatabase.getDatabase(this).noteDao()),
                currentNoteId
        )
    }

    private lateinit var uiManager: NoteEditUIManager
    private lateinit var resultHandler: NoteEditResultHandler
    private lateinit var previewManager: NoteEditPreviewManager
    private lateinit var tagManager: NoteEditTagManager
    private lateinit var geminiProcessor: NoteEditGeminiProcessor
    private lateinit var formatManager: NoteEditFormatManager

    companion object {
        const val EXTRA_NOTE_ID = "com.example.scribeai.EXTRA_NOTE_ID"
        private const val TAG = "NoteEditActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initializeManagers()
        if (currentNoteId == null) {
            getString(R.string.title_new_note)
        } else {
            getString(R.string.title_edit_note)
        }

        observeNoteDetails()
        tagManager.setupTagInput()
        setupSaveButton()
        com.example.scribeai.features.noteedit.NoteEditKeyboardUtil.setupKeyboardDismissalOnTouch(
                binding.nestedScrollView,
                this
        )
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializeManagers() {
        resultHandler = NoteEditResultHandler(activityResultRegistry, this, this, this)
        lifecycle.addObserver(resultHandler)
        previewManager = NoteEditPreviewManager(this, binding)
        // uiManager = NoteEditUIManager(this, binding, viewModel, resultHandler) // UIManager might
        // be simplified or removed later if not needed

        // Setup listener for the new button
        binding.buttonAddExistingNotes.setOnClickListener {
            showImageSourceSelectionDialog() // Show the dialog to choose source
        }

        tagManager =
                NoteEditTagManager(
                        this,
                        binding.tagChipGroup,
                        binding.tagEditText,
                        binding.buttonAddTag,
                        viewModel
                )
        // Get references to processing overlay views
        val overlay =
                binding.root.findViewById<ConstraintLayout>(R.id.note_processing_overlay)
                        ?: throw IllegalStateException("Processing overlay view not found")

        val progressIndicator =
                findViewById<CircularProgressIndicator>(R.id.note_processing_progress)
                        ?: throw IllegalStateException("Progress indicator view not found")

        val processingText =
                findViewById<TextView>(R.id.note_processing_text)
                        ?: throw IllegalStateException("Processing text view not found")

        geminiProcessor =
                NoteEditGeminiProcessor(
                        this,
                        lifecycleScope,
                        overlay,
                        progressIndicator,
                        processingText,
                        this
                )

        val toolbarContainer = findViewById<View>(com.example.scribeai.R.id.formatting_toolbar)
        val chipGroup =
                toolbarContainer.findViewById<ChipGroup>(
                        com.example.scribeai.R.id.formatting_chip_group
                )
        formatManager = NoteEditFormatManager(binding.contentEditText, chipGroup)
    }

    private fun observeNoteDetails() {
        viewModel.note.observe(this) { note ->
            note?.let {
                binding.titleEditText.setText(it.title)
                binding.contentEditText.setText(it.content)
                it.imageUri?.let { uriString ->
                    val imageUri = Uri.parse(uriString)
                    previewManager.showImagePreview(imageUri)
                    // No need to switch modes explicitly anymore
                }
                        ?: run {
                            // No image URI
                            previewManager.hideAllPreviews()
                        }
            }
                    ?: run { previewManager.hideAllPreviews() } // Default hide preview if no note
        }

        viewModel.tags.observe(this) { tags -> tagManager.updateTagChips(tags) }
    }

    private fun copyImageToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(sourceUri)
            val fileName = "IMG_${UUID.randomUUID()}.jpg"
            val internalDir = File(filesDir, "note_images")
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }
            val outputFile = File(internalDir, fileName)
            val outputStream = FileOutputStream(outputFile)

            inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error copying image to internal storage", e)
            showErrorToast("Failed to save image attachment.")
            null
        }
    }

    override fun onImageResult(uri: Uri) {
        val internalUri = copyImageToInternalStorage(uri)
        if (internalUri != null) {
            viewModel.setSelectedImageUri(internalUri)
            previewManager.showImagePreview(internalUri)
            // No need to switch modes explicitly
            geminiProcessor.processImageForOcr(internalUri)
        } else {
            onResultCancelledOrFailed(true)
        }
    }

    override fun onResultCancelledOrFailed(isNewDrawingAttempt: Boolean) {
        // If cancelled and no image was previously selected, hide preview.
        if (viewModel.selectedImageUri.value == null) {
            previewManager.hideAllPreviews()
        } else {
            Log.d(TAG, "Image selection cancelled or failed, keeping current preview.")
        }
    }

    // This method might be obsolete now, depending on NoteEditResultCallback usage
    override fun showTextModeUI() {
        previewManager.hideAllPreviews() // Just ensure preview is hidden if needed
    }

    override fun showError(message: String) {
        showErrorToast(message)
    }

    override fun onOcrSuccess(markdownText: String) {
        Log.d(TAG, "Gemini OCR Success. Appending Markdown text.")
        val currentContent = binding.contentEditText.text.toString()
        val separator = if (currentContent.isNotBlank()) "\n\n---\n\n" else ""
        binding.contentEditText.append("$separator$markdownText")
        Toast.makeText(this, "Text extracted!", Toast.LENGTH_SHORT).show()
    }

    override fun onOcrError(message: String) {
        Log.e(TAG, "Gemini OCR Error: $message")
        showErrorToast("AI Error: $message")
    }

    private fun setupSaveButton() {
        binding.buttonSaveNote.setOnClickListener { saveNoteAndFinish() }
    }

    override fun onDrawResult(uri: Uri) {
        val internalUri = copyImageToInternalStorage(uri)
        if (internalUri != null) {
            viewModel.setSelectedImageUri(internalUri)
            previewManager.showImagePreview(internalUri)
            // No need to switch modes explicitly
        } else {
            onResultCancelledOrFailed(true) // Keep existing logic for failure
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
    }

    /**
     * Validates the note input fields (title, content/image). Shows errors directly on the UI
     * elements if validation fails.
     * @return true if input is valid, false otherwise.
     */
    private fun validateNoteInput(title: String, content: String): Boolean {
        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.error_empty_title)
            return false
        } else {
            binding.titleInputLayout.error = null
        }

        if (content.isEmpty() && viewModel.selectedImageUri.value == null) {
            com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            R.string.error_empty_note_content_or_image,
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )
                    .setAction(R.string.action_discard) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                    .show()
            return false
        }

        return true
    }

    private fun saveNoteAndFinish() {
        val title = binding.titleEditText.text.toString().trim()
        val content = binding.contentEditText.text.toString().trim()

        if (!validateNoteInput(title, content)) {
            return
        }

        viewModel.saveNote(title, content)
        Log.d(
                TAG,
                "Note save requested. Title: '$title', Content: '$content', ImageUri: ${viewModel.selectedImageUri.value}"
        )

        Toast.makeText(this, R.string.note_saved_confirmation, Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showErrorToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showImageSourceSelectionDialog() {
        val dialogBinding = DialogImageSourceContentBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        dialogBinding.dialogButtonCamera.setOnClickListener {
            resultHandler.launchCamera()
            dialog.dismiss()
        }

        dialogBinding.dialogButtonGallery.setOnClickListener {
            resultHandler.launchGallery()
            dialog.dismiss()
        }

        dialogBinding.dialogButtonCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
