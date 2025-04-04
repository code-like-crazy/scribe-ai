package com.example.scribeai.ui.noteedit

// Base imports
// Local project imports
// ML Kit imports
// Java IO/Util
// Local UI components
// Removed unused imports: MotionEvent, View, EditorInfo, InputMethodManager, EditText
// Removed Gemini imports, now handled by NoteEditGeminiProcessor
// Removed Chip import, now handled by NoteEditTagManager
// Removed IOException and kotlinx.coroutines.launch, now handled elsewhere
import android.app.Activity // Re-added import
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.scribeai.R
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.databinding.ActivityNoteEditBinding

// all coroutines

// Implement the callback interfaces
class NoteEditActivity : AppCompatActivity(), NoteEditResultCallback, GeminiProcessorCallback {

    // Binding remains
    private lateinit var binding: ActivityNoteEditBinding

    // --- View Model ---
    private val currentNoteId: Long? by lazy {
        intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it != -1L }
    }

    private val viewModel: NoteEditViewModel by viewModels {
        NoteEditViewModelFactory(
                NoteRepository(AppDatabase.getDatabase(this).noteDao()),
                currentNoteId
        )
    }

    // --- Managers ---
    private lateinit var uiManager: NoteEditUIManager
    private lateinit var resultHandler: NoteEditResultHandler
    private lateinit var previewManager: NoteEditPreviewManager
    private lateinit var tagManager: NoteEditTagManager // Add Tag Manager
    private lateinit var geminiProcessor: NoteEditGeminiProcessor // Add Gemini Processor

    companion object {
        const val EXTRA_NOTE_ID = "com.example.scribeai.EXTRA_NOTE_ID"
        // Keep NOTE_TYPE constants if needed for intent extras, otherwise remove
        private const val TAG = "NoteEditActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        // Gemini initialization moved to NoteEditGeminiProcessor's init block
        initializeManagers() // Initialize all helper classes including new ones

        // Set title
        supportActionBar?.title =
                if (currentNoteId == null) {
                    getString(R.string.title_new_note)
                } else {
                    getString(R.string.title_edit_note)
                }

        observeNoteDetails()
        // Tag setup delegated
        tagManager.setupTagInput()
        setupSaveButton()
        // Keyboard setup delegated
        NoteEditKeyboardUtil.setupKeyboardDismissalOnTouch(binding.nestedScrollView, this)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // initializeGeminiModel removed

    private fun initializeManagers() {
        // Initialize existing managers
        resultHandler = NoteEditResultHandler(activityResultRegistry, this, this, this)
        lifecycle.addObserver(resultHandler)
        previewManager = NoteEditPreviewManager(this, binding)
        uiManager = NoteEditUIManager(this, binding, viewModel, resultHandler)
        uiManager.setupInputModeButtons { resultHandler.getCurrentDrawingUri() }

        // Initialize new managers
        tagManager =
                NoteEditTagManager(
                        this,
                        binding.tagChipGroup,
                        binding.tagEditText,
                        binding.buttonAddTag,
                        viewModel
                )
        geminiProcessor =
                NoteEditGeminiProcessor(
                        this,
                        lifecycleScope, // Pass lifecycleScope for coroutines
                        binding.progressBar, // Pass progress bar
                        this // Pass this activity as the callback
                )
    }

    private fun observeNoteDetails() {
        viewModel.note.observe(this) { note ->
            note?.let {
                binding.titleEditText.setText(it.title)
                binding.contentEditText.setText(it.content)
                it.imageUri?.let { uriString ->
                    val imageUri = Uri.parse(uriString)
                    previewManager.showImagePreview(imageUri)
                    uiManager.showCameraMode()
                }
                        ?: run {
                            // No image URI, default to text mode
                            previewManager.hideAllPreviews()
                            resultHandler.setCurrentDrawingUri(null)
                            uiManager.showTextMode()
                        }
            }
                    ?: run {
                        // New note, default to text mode
                        uiManager.showTextMode()
                    }
        }

        // Observe tags - delegate UI update to tagManager
        viewModel.tags.observe(this) { tags -> tagManager.updateTagChips(tags) }
    }

    // Tag Management UI methods removed (setupTagInput, addTagFromInput, updateTagChips)

    // --- NoteEditResultCallback Implementation ---

    override fun onImageResult(uri: Uri) {
        viewModel.setSelectedImageUri(uri)
        previewManager.showImagePreview(uri)
        uiManager.showCameraMode()
        // Trigger OCR via the processor
        geminiProcessor.processImageForOcr(uri)
    }

    override fun onDrawingResult(uri: Uri) {
        // Drawing feature removed
    }

    override fun onResultCancelledOrFailed(isNewDrawingAttempt: Boolean) {
        // If cancelling a NEW drawing/image selection and no image exists, revert to text mode.
        // If cancelling an EDIT, keep the existing preview visible.
        if (isNewDrawingAttempt && viewModel.selectedImageUri.value == null) {
            uiManager.showTextMode()
            previewManager.hideAllPreviews()
        } else {
            // If editing was cancelled, the UI should already be showing the correct preview.
            // No explicit action needed here, but could add logging.
            Log.d(TAG, "Edit cancelled or failed, keeping current preview.")
        }
    }

    override fun showTextModeUI() {
        // Callback from ResultHandler if it needs to force text mode (e.g., dialog cancel)
        uiManager.showTextMode()
        previewManager.hideAllPreviews()
    }

    override fun showError(message: String) {
        showErrorToast(message)
    }

    // --- GeminiProcessorCallback Implementation ---

    override fun onOcrSuccess(markdownText: String) {
        Log.d(TAG, "Gemini OCR Success. Appending Markdown text.")
        val currentContent = binding.contentEditText.text.toString()
        // Append the Markdown text received from the processor
        val separator = if (currentContent.isNotBlank()) "\n\n---\n\n" else "" // Add separator
        binding.contentEditText.append("$separator$markdownText")
        Toast.makeText(this, "Text extracted!", Toast.LENGTH_SHORT).show()
        // Progress bar is hidden by the processor
    }

    override fun onOcrError(message: String) {
        Log.e(TAG, "Gemini OCR Error: $message")
        showErrorToast("AI Error: $message")
        // Progress bar is hidden by the processor
    }

    // Gemini Vision Processing methods removed (processImageForOcr, uriToBitmap)

    // --- Saving Logic ---

    private fun setupSaveButton() {
        binding.buttonSaveNote.setOnClickListener { saveNoteAndFinish() }
    }

    // Handle back navigation (toolbar home button) - Just finish without saving
    override fun onSupportNavigateUp(): Boolean {
        finish() // Simply finish the activity
        return true
    }

    // Handle system back button press - Just finish without saving
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish() // Simply finish the activity
        // super.onBackPressed() // Call super if you want default back behavior + finishing
    }

    private fun saveNoteAndFinish() {
        val title = binding.titleEditText.text.toString().trim()
        val content = binding.contentEditText.text.toString().trim()
        val imageUri = viewModel.selectedImageUri.value

        // --- Validation ---
        // 1. Check if title is empty
        if (title.isEmpty()) {
            binding.titleInputLayout.error =
                    getString(R.string.error_empty_title) // Show error on the field, removed toast
            return // Stop saving
        } else {
            binding.titleInputLayout.error = null // Clear error if title is not empty
        }

        // 2. Check if both title and image are missing (existing check)
        if (title.isEmpty() && imageUri == null
        ) { // This check might be redundant now but kept for safety
            com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            R.string.error_empty_note,
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )
                    .setAction(R.string.action_discard) {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                    .show()
            return
        }

        // Update the ViewModel's selected image URI before saving
        viewModel.setSelectedImageUri(imageUri)
        // Save the note
        viewModel.saveNote(title, content)
        Log.d(TAG, "Note save requested. Title: '$title', Content: '$content', ImageUri: $imageUri")

        Toast.makeText(this, R.string.note_saved_confirmation, Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
    }

    // --- Utility ---

    private fun showErrorToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // Keyboard Dismissal Logic methods removed (setupKeyboardDismissal, hideKeyboard)

    // Removed methods now handled by managers:
    // initializeGeminiModel, processImageForOcr, uriToBitmap, setupTagInput, addTagFromInput,
    // updateTagChips, setupKeyboardDismissal, hideKeyboard
    // (Keep list of originally removed methods for reference if needed)
    // setupInputModeButtons, showTextMode, showDrawingModeUi, showCameraMode, updateUiVisibility,
    // showImageSourceDialog, setupActivityResultLaunchers, registerGalleryLauncher,
    // registerCameraLauncher,
    // registerDrawingLauncher, handleImageResult, handleDrawingResult, launchGalleryPicker,
    // launchCamera,
    // createImageFile, showImagePreview, hideImagePreview, showDrawingPreview, hideDrawingPreview
}
