package com.example.scribeai.ui.noteedit

// Base imports
// Local project imports
// ML Kit imports
// Java IO/Util
// Local UI components
import android.app.Activity // Re-added import
import android.content.Context // Import Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent // Import MotionEvent
import android.view.View // Import View
import android.view.inputmethod.EditorInfo // Import EditorInfo
import android.view.inputmethod.InputMethodManager // Import InputMethodManager
import android.widget.EditText // Import EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.scribeai.R
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.databinding.ActivityNoteEditBinding
import com.google.android.material.chip.Chip // Import Chip
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException

// Implement the callback interface for the result handler
class NoteEditActivity : AppCompatActivity(), NoteEditResultCallback {

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
    // Removed old launcher/URI variables

    companion object {
        const val EXTRA_NOTE_ID = "com.example.scribeai.EXTRA_NOTE_ID"
        // Keep NOTE_TYPE constants if needed for intent extras, otherwise remove
        private const val TAG = "NoteEditActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar() // Setup toolbar first
        initializeManagers() // Initialize helper classes

        // Set title based on whether it's a new note or editing
        supportActionBar?.title =
                if (currentNoteId == null) {
                    getString(R.string.title_new_note)
                } else {
                    getString(R.string.title_edit_note)
                }

        observeNoteDetails() // Observe ViewModel for note data
        setupTagInput() // Setup tag input listeners
        setupSaveButton() // Setup save button listener
        setupKeyboardDismissal() // Setup touch listener for keyboard dismissal
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun initializeManagers() {
        // Result Handler needs ActivityResultRegistry, LifecycleOwner, Context, and Callback (this)
        resultHandler = NoteEditResultHandler(activityResultRegistry, this, this, this)
        lifecycle.addObserver(resultHandler) // Add observer for onCreate registration

        // Preview Manager needs Context and Binding
        previewManager = NoteEditPreviewManager(this, binding)

        // UI Manager needs Context, Binding, ViewModel, and Launcher (ResultHandler)
        uiManager = NoteEditUIManager(this, binding, viewModel, resultHandler)

        // Setup the input mode buttons via the UI Manager
        // Pass a lambda to get the current drawing URI from the result handler
        uiManager.setupInputModeButtons { resultHandler.getCurrentDrawingUri() }
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

        // Observe tags
        viewModel.tags.observe(this) { tags -> updateTagChips(tags) }
    }

    // --- Tag Management UI ---

    private fun setupTagInput() {
        binding.buttonAddTag.setOnClickListener { addTagFromInput() } // Use binding

        binding.tagEditText.setOnEditorActionListener { _, actionId, _ -> // Use binding
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTagFromInput()
                true // Consume the event
            } else {
                false // Do not consume the event
            }
        }
    }

    private fun addTagFromInput() {
        val tagText = binding.tagEditText.text.toString().trim() // Use binding
        if (tagText.isNotEmpty()) {
            viewModel.addTag(tagText)
            binding.tagEditText.text?.clear() // Use binding
        } else {
            Toast.makeText(this, R.string.error_empty_tag, Toast.LENGTH_SHORT)
                    .show() // Use string resource
        }
    }

    private fun updateTagChips(tags: List<String>) {
        binding.tagChipGroup.removeAllViews() // Use binding
        tags.forEach { tag ->
            val chip =
                    Chip(this).apply { // Use binding
                        text = tag
                        isCloseIconVisible = true
                        setOnCloseIconClickListener { viewModel.removeTag(tag) }
                        // Optional: Apply chip styling from theme/style resource
                        // setChipBackgroundColorResource(R.color.secondary)
                        // setCloseIconTintResource(R.color.muted_foreground)
                        // setTextColorResource(R.color.secondary_foreground)
                    }
            binding.tagChipGroup.addView(chip) // Use binding
        }
    }

    // --- End Tag Management UI ---

    // --- NoteEditResultCallback Implementation ---

    override fun onImageResult(uri: Uri) {
        viewModel.setSelectedImageUri(uri)
        previewManager.showImagePreview(uri)
        uiManager.showCameraMode() // Set UI for image + OCR text
        processImageForOcr(uri) // Trigger OCR
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

    // --- OCR Processing (Remains in Activity for now) ---

    private fun processImageForOcr(imageUri: Uri) {
        Log.d(TAG, "Starting OCR process for URI: $imageUri")
        // (Implementation remains the same)
        try {
            val inputImage = InputImage.fromFilePath(this, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer
                    .process(inputImage)
                    .addOnSuccessListener { visionText ->
                        Log.d(
                                TAG,
                                "OCR Success. Detected text blocks: ${visionText.textBlocks.size}"
                        )
                        val currentContent = binding.contentEditText.text.toString()
                        val separator =
                                if (currentContent.isNotBlank()) "\n\n--- OCR Text ---\n" else ""
                        binding.contentEditText.append("$separator${visionText.text}")
                        Toast.makeText(this, "Text recognized!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR Failed", e)
                        showErrorToast("Text recognition failed: ${e.message}")
                    }
        } catch (e: IOException) {
            Log.e(TAG, "Error preparing image for OCR (IO)", e)
            showErrorToast("Error reading image file for OCR")
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing image for OCR (General)", e)
            showErrorToast("Error processing image: ${e.message}")
        }
    }

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

    // --- Keyboard Dismissal Logic ---
    private fun setupKeyboardDismissal() {
        // Add explicit types to lambda parameters
        binding.nestedScrollView.setOnTouchListener { v: View, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
                v.clearFocus() // Clear focus from the scroll view itself if needed
                // Also clear focus from any EditText that might have it
                currentFocus?.let { focusedView ->
                    if (focusedView is EditText) {
                        focusedView.clearFocus()
                    }
                }
            }
            // Return false so touch events are still processed for scrolling etc.
            false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = currentFocus
        // If no view currently has focus, create a new one, just so we can grab a window token from
        // it
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // Removed methods now handled by managers:
    // setupInputModeButtons, showTextMode, showDrawingModeUi, showCameraMode, updateUiVisibility,
    // showImageSourceDialog, setupActivityResultLaunchers, registerGalleryLauncher,
    // registerCameraLauncher,
    // registerDrawingLauncher, handleImageResult, handleDrawingResult, launchGalleryPicker,
    // launchCamera,
    // createImageFile, showImagePreview, hideImagePreview, showDrawingPreview, hideDrawingPreview
}
