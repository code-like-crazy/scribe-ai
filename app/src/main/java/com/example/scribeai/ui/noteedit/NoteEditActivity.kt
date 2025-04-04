package com.example.scribeai.ui.noteedit

// Base imports
// Local project imports
// ML Kit imports
// Java IO/Util
// Local UI components
import android.app.Activity // Re-added import
import android.content.Context // Import Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent // Import MotionEvent
import android.view.View // Import View
import android.view.inputmethod.EditorInfo // Import EditorInfo
import android.view.inputmethod.InputMethodManager // Import InputMethodManager
import android.widget.EditText // Import EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.scribeai.BuildConfig // Import BuildConfig
import com.example.scribeai.R
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.databinding.ActivityNoteEditBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.chip.Chip // Import Chip
import java.io.IOException
import kotlinx.coroutines.launch

// Implement the callback interface for the result handler
class NoteEditActivity : AppCompatActivity(), NoteEditResultCallback {

    // Gemini AI Model
    private lateinit var generativeModel: GenerativeModel

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
        initializeGeminiModel() // Initialize Gemini
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

    private fun initializeGeminiModel() {
        // Retrieve the API key from BuildConfig
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            Log.e(TAG, "Gemini API Key is missing. Check local.properties and build configuration.")
            // Handle the missing key appropriately - maybe disable AI features or show an error
            Toast.makeText(this, "Error: Gemini API Key is missing.", Toast.LENGTH_LONG).show()
            // Depending on the desired behavior, you might want to return or throw an exception
            // For now, we'll proceed, but the GenerativeModel initialization will likely fail
        }

        // Configure the model - stop sequences prevent unwanted text like "Response:"
        val config = generationConfig {
            // Adjust temperature for creativity vs. factuality (lower is more factual)
            temperature = 0.2f
            // Limit output tokens if necessary
            // maxOutputTokens = 1024
            // Stop sequences to ensure only note content is returned
            stopSequences = listOf("---", "Note:", "Summary:", "Response:")
        }

        generativeModel =
                GenerativeModel(
                        // Use the specified model name
                        modelName = "gemini-2.0-flash",
                        apiKey = apiKey,
                        generationConfig = config
                )
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

    // --- Gemini Vision Processing ---

    private fun processImageForOcr(imageUri: Uri) {
        Log.d(TAG, "Starting Gemini Vision process for URI: $imageUri")
        binding.progressBar.visibility = View.VISIBLE // Show progress bar

        lifecycleScope.launch {
            try {
                val bitmap = uriToBitmap(imageUri)
                if (bitmap == null) {
                    showErrorToast("Failed to load image.")
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                // Strict prompt to extract only the text and format it as notes
                val prompt =
                        """
                Extract the exact text content from the image provided.
                Format the extracted text clearly as notes.
                Do NOT add any introductory phrases, explanations, summaries, or any text other than the extracted note content itself.
                Ensure the output is only the formatted notes based on the image text.
                """.trimIndent()

                val inputContent = content {
                    image(bitmap)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)

                response.text?.let { generatedText ->
                    Log.d(TAG, "Gemini Success. Generated text length: ${generatedText.length}")
                    val currentContent = binding.contentEditText.text.toString()
                    // Append directly, assuming Gemini formats it well based on the prompt
                    val separator = if (currentContent.isNotBlank()) "\n\n" else ""
                    binding.contentEditText.append("$separator$generatedText")
                    Toast.makeText(this@NoteEditActivity, "Text extracted!", Toast.LENGTH_SHORT)
                            .show()
                }
                        ?: run {
                            Log.w(TAG, "Gemini response text was null.")
                            showErrorToast("AI could not extract text from the image.")
                        }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision Failed", e)
                showErrorToast("AI text extraction failed: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE // Hide progress bar
            }
        }
    }

    // Helper function to convert Uri to Bitmap
    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error converting Uri to Bitmap", e)
            null
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
