package com.example.scribeai.ui.noteedit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // Import viewModels delegate
import androidx.appcompat.app.AlertDialog // Import AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider // Import FileProvider
import com.bumptech.glide.Glide // Import Glide
import com.example.scribeai.R
import com.example.scribeai.data.AppDatabase // Import AppDatabase
import com.example.scribeai.data.NoteRepository // Import NoteRepository
import com.example.scribeai.databinding.ActivityNoteEditBinding
import com.example.scribeai.ui.drawing.DrawingActivity // Import DrawingActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File // Import File
import java.io.IOException // Import IOException
import java.text.SimpleDateFormat // Import SimpleDateFormat
import java.util.Date // Import Date
import java.util.Locale // Import Locale

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding

    // Get noteId from intent extras
    private val currentNoteId: Long? by lazy {
        intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it != -1L }
    }

    // Instantiate ViewModel using the factory
    private val viewModel: NoteEditViewModel by viewModels {
        NoteEditViewModelFactory(
                NoteRepository(AppDatabase.getDatabase(this).noteDao()),
                currentNoteId
        )
    }

    // Activity Result Launchers
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var drawingLauncher:
            ActivityResultLauncher<Intent> // Launcher for DrawingActivity
    private var imageUriForCamera: Uri? = null // Uri for storing camera image
    private var currentDrawingUri: Uri? = null // Store the current drawing URI locally

    companion object {
        const val EXTRA_NOTE_ID = "com.example.scribeai.EXTRA_NOTE_ID"
        private const val TAG = "NoteEditActivity" // For logging
    }

    // Removed redundant currentNoteId variable declaration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Show back button

        // Set title based on whether it's a new note or editing
        if (currentNoteId == null) {
            supportActionBar?.title = getString(R.string.title_new_note)
        } else {
            supportActionBar?.title = getString(R.string.title_edit_note)
            // Observe the note LiveData to populate fields when editing
            observeNoteDetails()
        }

        setupInputModeButtons()
        setupActivityResultLaunchers()
        setupSaveButton() // Add call to setup save button listener
    }

    private fun observeNoteDetails() {
        viewModel.note.observe(this) { note ->
            note?.let {
                binding.titleEditText.setText(it.title)
                binding.contentEditText.setText(it.content)
                // Load image or drawing preview if URI exists
                it.imageUri?.let { uriString ->
                    val imageUri = Uri.parse(uriString)
                    // Determine if it's a drawing or photo based on some logic
                    // For now, assume PNG is drawing, others are photos
                    // A better approach might be storing note type (TEXT, IMAGE, DRAWING)
                    if (uriString.endsWith(".png")) { // Simple check for drawing
                        showDrawingPreview(imageUri)
                        currentDrawingUri = imageUri // Store for editing
                    } else {
                        showImagePreview(imageUri)
                        currentDrawingUri = null // Clear drawing URI if it's a photo
                    }
                }
                        ?: run {
                            // No image URI, ensure previews are hidden
                            hideImagePreview()
                            hideDrawingPreview()
                            currentDrawingUri = null
                        }
                // TODO: Handle other fields like tags later
            }
        }
    }

    // --- Mode Switching and UI Visibility ---

    private fun setupInputModeButtons() {
        binding.buttonModeType.setOnClickListener { showTextMode() }
        binding.buttonModeDraw.setOnClickListener {
            // Launch DrawingActivity, pass current drawing URI if available
            val intent =
                    Intent(this, DrawingActivity::class.java).apply {
                        currentDrawingUri?.let {
                            putExtra(DrawingActivity.EXTRA_DRAWING_URI, it.toString())
                        }
                    }
            drawingLauncher.launch(intent)
            // UI update (showing preview) happens when activity returns result
        }
        binding.buttonModeCamera.setOnClickListener {
            showCameraMode() // Prepare UI for potential image preview
            showImageSourceDialog() // Let user choose camera/gallery
        }

        // Make drawing preview clickable to edit
        binding.drawingPreviewImage.setOnClickListener {
            currentDrawingUri?.let { uri ->
                val intent =
                        Intent(this, DrawingActivity::class.java).apply {
                            putExtra(DrawingActivity.EXTRA_DRAWING_URI, uri.toString())
                        }
                drawingLauncher.launch(intent)
            }
        }
    }

    // Show only Title and Content EditText
    private fun showTextMode() {
        binding.titleInputLayout.visibility = View.VISIBLE // Title always visible
        binding.contentInputLayout.visibility = View.VISIBLE
        binding.imagePreview.visibility = View.GONE
        binding.drawingPreviewImage.visibility = View.GONE
        Toast.makeText(this, "Text Mode", Toast.LENGTH_SHORT).show()
    }

    // Show Title and Drawing Preview (Content EditText hidden)
    private fun showDrawingModeUi() {
        binding.titleInputLayout.visibility = View.VISIBLE // Title always visible
        binding.contentInputLayout.visibility = View.GONE // Hide text content for pure drawing
        binding.imagePreview.visibility = View.GONE
        binding.drawingPreviewImage.visibility = View.VISIBLE
        // Toast might be annoying here if triggered by activity result
    }

    // Show Title, Content EditText (for OCR), and Image Preview
    private fun showCameraMode() {
        binding.titleInputLayout.visibility = View.VISIBLE // Title always visible
        binding.contentInputLayout.visibility = View.VISIBLE // Keep visible for OCR text
        binding.imagePreview.visibility = View.VISIBLE // Image preview will be shown here
        binding.drawingPreviewImage.visibility = View.GONE
    }

    private fun showImageSourceDialog() {
        val options =
                arrayOf(
                        getString(R.string.dialog_option_camera),
                        getString(R.string.dialog_option_gallery)
                )
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_select_image_source))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> launchCamera() // Camera selected
                        1 -> launchGalleryPicker() // Gallery selected
                    }
                }
                .show()
    }

    private fun setupActivityResultLaunchers() {
        pickImageLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
                    ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        result.data?.data?.let { uri ->
                            Log.d(TAG, "Image selected from gallery: $uri")
                            viewModel.setSelectedImageUri(uri) // Pass URI to ViewModel
                            showImagePreview(uri)
                            processImageForOcr(uri)
                        }
                                ?: run {
                                    Log.e(TAG, "Failed to get URI from gallery result")
                                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT)
                                            .show()
                                }
                    } else {
                        Log.d(TAG, "Image selection cancelled or failed")
                    }
                }

        // Setup takePictureLauncher
        takePictureLauncher =
                registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                    if (success) {
                        imageUriForCamera?.let { uri ->
                            viewModel.setSelectedImageUri(uri) // Pass URI to ViewModel
                            showImagePreview(uri)
                            processImageForOcr(uri)
                            currentDrawingUri = null // Clear drawing URI if photo is taken
                        }
                                ?: run {
                                    Log.e(
                                            TAG,
                                            "Camera returned success but imageUriForCamera is null"
                                    )
                                    Toast.makeText(
                                                    this,
                                                    "Failed to get captured image",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                    } else {
                        Log.d(TAG, "Image capture cancelled or failed")
                        // Optionally delete the temporary file if capture failed/cancelled
                        imageUriForCamera?.let { uri ->
                            try {
                                contentResolver.delete(uri, null, null)
                                Log.d(TAG, "Temporary camera file deleted: $uri")
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Error deleting temporary camera file", e)
                            }
                        }
                    }
                    // Reset temporary URI holder
                    imageUriForCamera = null
                }

        // Setup DrawingActivity Launcher
        drawingLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
                    ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        result.data?.getStringExtra(DrawingActivity.RESULT_EXTRA_SAVED_URI)?.let {
                                uriString ->
                            val savedUri = Uri.parse(uriString)
                            Log.d(TAG, "Drawing saved/updated. URI: $savedUri")
                            viewModel.setSelectedImageUri(savedUri) // Update ViewModel
                            currentDrawingUri = savedUri // Update local reference
                            showDrawingPreview(savedUri) // Show the preview
                            showDrawingModeUi() // Ensure correct UI state after drawing
                        }
                                ?: run {
                                    Log.e(TAG, "DrawingActivity returned OK but no URI found.")
                                }
                    } else { // Handle cancellation or failure
                        Log.d(TAG, "Drawing cancelled or failed. Result code: ${result.resultCode}")
                        // If the user cancelled *editing* an existing drawing,
                        // we should keep the drawing preview visible.
                        // If they cancelled creating a *new* drawing (and no image exists),
                        // then revert to text mode.
                        if (result.resultCode == Activity.RESULT_CANCELED &&
                                        currentDrawingUri == null &&
                                        viewModel.selectedImageUri.value == null
                        ) {
                            // Only revert to text mode if cancelling a NEW drawing attempt
                            // and no other image/drawing was previously set.
                            showTextMode()
                        } else if (currentDrawingUri != null) {
                            // If cancelling while editing, ensure the drawing mode UI stays visible
                            // with the *original* drawing.
                            showDrawingModeUi()
                            showDrawingPreview(currentDrawingUri!!) // Reshow the original preview
                        }
                        // Otherwise (e.g., other failure codes, or cancelling edit of photo note),
                        // the current UI state might be appropriate, or revert to text mode if
                        // unsure.
                        // For simplicity, we only explicitly handle reverting to text mode when
                        // cancelling a brand new drawing.
                    }
                }
    }

    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // Launch camera intent
    private fun launchCamera() {
        try {
            val photoFile: File = createImageFile()
            imageUriForCamera =
                    FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider", // Match authority in
                            // AndroidManifest.xml
                            photoFile
                    )
            takePictureLauncher.launch(imageUriForCamera)
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating image file for camera", ex)
            Toast.makeText(this, "Could not start camera", Toast.LENGTH_SHORT).show()
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "Error getting URI for file provider. Check authority.", ex)
            Toast.makeText(this, "Could not start camera (File Provider issue)", Toast.LENGTH_SHORT)
                    .show()
        }
    }

    // Helper to create a unique image file
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(null) // Use app-specific external storage
        return File.createTempFile(
                        "JPEG_${timeStamp}_", /* prefix */
                        ".jpg", /* suffix */
                        storageDir /* directory */
                )
                .apply {
                    // Save file path for use with ACTION_VIEW intents
                    // currentPhotoPath = absolutePath // If needed elsewhere
                    Log.d(TAG, "Created image file: $absolutePath")
                }
    }

    private fun showImagePreview(imageUri: Uri) {
        // Assumes showCameraMode() was called before to set layout visibility
        binding.imagePreview.visibility = View.VISIBLE // Just make it visible
        Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_image_placeholder) // Add placeholder drawable
                .error(R.drawable.ic_broken_image)
                .into(binding.imagePreview)
    }

    private fun hideImagePreview() {
        binding.imagePreview.visibility = View.GONE
    }

    // Functions to show/hide drawing preview
    private fun showDrawingPreview(drawingUri: Uri) {
        // Assumes showDrawingModeUi() was called before to set layout visibility
        binding.drawingPreviewImage.visibility = View.VISIBLE // Just make it visible
        Glide.with(this)
                .load(drawingUri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_broken_image)
                .into(binding.drawingPreviewImage)
    }

    private fun hideDrawingPreview() {
        binding.drawingPreviewImage.visibility = View.GONE
    }

    private fun processImageForOcr(imageUri: Uri) {
        Log.d(TAG, "Starting OCR process for URI: $imageUri")
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
                        // Append recognized text to the content field
                        val currentContent = binding.contentEditText.text.toString()
                        val separator =
                                if (currentContent.isNotBlank()) "\n\n--- OCR Text ---\n" else ""
                        binding.contentEditText.append("$separator${visionText.text}")
                        Toast.makeText(this, "Text recognized!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR Failed", e)
                        Toast.makeText(
                                        this,
                                        "Text recognition failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                    }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing image for OCR", e)
            Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Setup listener for the new save button
    private fun setupSaveButton() {
        binding.buttonSaveNote.setOnClickListener { saveNote() }
    }

    // Handle back navigation (from toolbar home button)
    override fun onSupportNavigateUp(): Boolean {
        // TODO: Add confirmation dialog if changes are unsaved
        finish()
        return true
    }

    private fun saveNote() {
        val title = binding.titleEditText.text.toString().trim()
        val content = binding.contentEditText.text.toString().trim()
        // Drawing is saved via DrawingActivity result, URI is already in ViewModel if successful

        // Check if the note is empty (title or an image/drawing URI must exist)
        // Content can be empty for a drawing-only note.
        val imageUriIsSet = viewModel.selectedImageUri.value != null
        if (title.isEmpty() && !imageUriIsSet) {
            com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            R.string.error_empty_note,
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    )
                    .show()
            return // Return early if note is truly empty
        }

        // Call ViewModel to save the note.
        // The ViewModel's selectedImageUri LiveData holds the correct URI (photo or drawing)
        viewModel.saveNote(title, content)

        // Show confirmation, set result, and finish
        Toast.makeText(this, R.string.note_saved_confirmation, Toast.LENGTH_SHORT)
                .show() // Use Toast for simplicity
        setResult(Activity.RESULT_OK) // Signal success to MainActivity/NotePreviewActivity
        finish()
        // Removed extra closing brace here
    }

    // TODO: Add onBackPressed handling for unsaved changes confirmation
}
