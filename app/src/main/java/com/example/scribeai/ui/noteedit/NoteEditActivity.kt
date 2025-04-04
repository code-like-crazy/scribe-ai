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
import androidx.appcompat.app.AlertDialog // Import AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // Import viewModels delegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider // Import FileProvider
import com.bumptech.glide.Glide // Import Glide
import com.example.scribeai.R
import com.example.scribeai.data.AppDatabase // Import AppDatabase
import com.example.scribeai.data.Note
import com.example.scribeai.data.NoteRepository // Import NoteRepository
import com.example.scribeai.databinding.ActivityNoteEditBinding
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import android.graphics.Bitmap // Import Bitmap
import java.io.File // Import File
import java.io.FileOutputStream // Import FileOutputStream
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
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri> // For camera capture later
    private var imageUriForCamera: Uri? = null // Uri for storing camera image

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
                // Load image if URI exists (editing an existing image note)
                it.imageUri?.let { uriString ->
                    val imageUri = Uri.parse(uriString)
                    showImagePreview(imageUri)
                }
                // TODO: Handle other fields like tags later
            }
        }
    }

    private fun setupInputModeButtons() {
        binding.buttonModeType.setOnClickListener {
            // Switch to text input mode
            binding.contentInputLayout.visibility = View.VISIBLE
            binding.imagePreview.visibility = View.GONE
            binding.drawingView.visibility = View.GONE
            // Optionally clear drawing view? binding.drawingView.clearCanvas()
            Toast.makeText(this, "Text Mode", Toast.LENGTH_SHORT).show() // Simplified Toast
        }
        binding.buttonModeDraw.setOnClickListener {
            // Switch to drawing mode
            binding.contentInputLayout.visibility = View.GONE
            binding.imagePreview.visibility = View.GONE
            binding.drawingView.visibility = View.VISIBLE
            Toast.makeText(this, "Draw Mode", Toast.LENGTH_SHORT).show() // Simplified Toast
        }
        binding.buttonModeCamera.setOnClickListener {
            // Keep text input visible for potential OCR results
            binding.contentInputLayout.visibility = View.VISIBLE
            binding.drawingView.visibility = View.GONE
            // Image preview visibility is handled by show/hideImagePreview
            showImageSourceDialog() // Show dialog to choose Camera or Gallery
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.dialog_option_camera), getString(R.string.dialog_option_gallery))
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
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d(TAG, "Image selected from gallery: $uri")
                    viewModel.setSelectedImageUri(uri) // Pass URI to ViewModel
                    showImagePreview(uri)
                    processImageForOcr(uri)
                } ?: run {
                    Log.e(TAG, "Failed to get URI from gallery result")
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            } else {
                 Log.d(TAG, "Image selection cancelled or failed")
            }
        }

        // Setup takePictureLauncher
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                imageUriForCamera?.let { uri ->
                    Log.d(TAG, "Image captured successfully: $uri")
                    viewModel.setSelectedImageUri(uri) // Pass URI to ViewModel
                    showImagePreview(uri)
                    processImageForOcr(uri)
                } ?: run {
                     Log.e(TAG, "Camera returned success but imageUriForCamera is null")
                     Toast.makeText(this, "Failed to get captured image", Toast.LENGTH_SHORT).show()
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
    }

    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // Launch camera intent
    private fun launchCamera() {
        try {
            val photoFile: File = createImageFile()
            imageUriForCamera = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider", // Match authority in AndroidManifest.xml
                photoFile
            )
            takePictureLauncher.launch(imageUriForCamera)
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating image file for camera", ex)
            Toast.makeText(this, "Could not start camera", Toast.LENGTH_SHORT).show()
        } catch (ex: IllegalArgumentException) {
             Log.e(TAG, "Error getting URI for file provider. Check authority.", ex)
             Toast.makeText(this, "Could not start camera (File Provider issue)", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to create a unique image file
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(null) // Use app-specific external storage
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save file path for use with ACTION_VIEW intents
            // currentPhotoPath = absolutePath // If needed elsewhere
            Log.d(TAG, "Created image file: $absolutePath")
        }
    }


    private fun showImagePreview(imageUri: Uri) {
        // When showing image, hide text input and drawing view
        binding.contentInputLayout.visibility = View.GONE
        binding.drawingView.visibility = View.GONE
        binding.imagePreview.visibility = View.VISIBLE
        Glide.with(this)
            .load(imageUri)
            .placeholder(R.drawable.ic_image_placeholder) // Add placeholder drawable
            .error(R.drawable.ic_broken_image) // Add error drawable
            .into(binding.imagePreview)
        // Optionally hide the text content input when showing image
        // binding.contentInputLayout.visibility = View.GONE
    }

     private fun hideImagePreview() {
        // When hiding image (e.g., switching mode), ensure text input is visible
        // and drawing view is hidden.
        binding.imagePreview.visibility = View.GONE
        binding.drawingView.visibility = View.GONE
        binding.contentInputLayout.visibility = View.VISIBLE
    }

    private fun processImageForOcr(imageUri: Uri) {
        Log.d(TAG, "Starting OCR process for URI: $imageUri")
        try {
            val inputImage = InputImage.fromFilePath(this, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "OCR Success. Detected text blocks: ${visionText.textBlocks.size}")
                    // Append recognized text to the content field
                    val currentContent = binding.contentEditText.text.toString()
                    val separator = if (currentContent.isNotBlank()) "\n\n--- OCR Text ---\n" else ""
                    binding.contentEditText.append("$separator${visionText.text}")
                    Toast.makeText(this, "Text recognized!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR Failed", e)
                    Toast.makeText(this, "Text recognition failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing image for OCR", e)
            Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Setup listener for the new save button
    private fun setupSaveButton() {
        binding.buttonSaveNote.setOnClickListener {
            saveNote()
        }
    }

    // Handle back navigation (from toolbar home button)
    override fun onSupportNavigateUp(): Boolean {
        // TODO: Add confirmation dialog if changes are unsaved
        finish()
        return true
    }


    private fun saveNote() {
        val title = binding.titleEditText.text.toString().trim()
        // Content might be empty if it's just a drawing
        val content = binding.contentEditText.text.toString().trim()
        var drawingUri: Uri? = null

        // Check if drawing view is visible and potentially save the drawing
        if (binding.drawingView.visibility == View.VISIBLE) {
            try {
                val bitmap = binding.drawingView.getBitmap()
                // Save bitmap to a file and get its URI
                val drawingFile = createDrawingFile() // Similar to createImageFile but maybe different prefix/suffix
                FileOutputStream(drawingFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos) // Save as PNG
                }
                drawingUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    drawingFile
                )
                Log.d(TAG, "Drawing saved to URI: $drawingUri")
                // Set the URI in the ViewModel *before* calling saveNote
                viewModel.setSelectedImageUri(drawingUri)

            } catch (e: IOException) {
                Log.e(TAG, "Error saving drawing", e)
                Toast.makeText(this, "Failed to save drawing", Toast.LENGTH_SHORT).show()
                // Decide if saving should be aborted or continue without drawing
                return // Abort saving if drawing fails
            }
        }

        // Check if the note is empty (considering title, content, and potential drawing/image)
        // The ViewModel's selectedImageUri holds either gallery/camera URI or the new drawing URI
        val imageUriIsSet = viewModel.selectedImageUri.value != null
        if (title.isEmpty() && content.isEmpty() && !imageUriIsSet) {
            Snackbar.make(binding.root, R.string.error_empty_note, Snackbar.LENGTH_SHORT).show()
            return
        }


        // Call ViewModel to save the note.
        // If drawing was saved, its URI is already set in the ViewModel.
        // If gallery/camera image was selected, its URI is also set.
        viewModel.saveNote(title, content)

        // Show confirmation, set result, and finish
        Toast.makeText(this, R.string.note_saved_confirmation, Toast.LENGTH_SHORT).show() // Use Toast for simplicity
        setResult(Activity.RESULT_OK) // Signal success to MainActivity/NotePreviewActivity
        finish()
    }

     // Helper to create a unique file for drawings (similar to createImageFile)
    @Throws(IOException::class)
    private fun createDrawingFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(null) // Use app-specific external storage
        return File.createTempFile(
            "DRAWING_${timeStamp}_", /* prefix */
            ".png", /* suffix */
            storageDir /* directory */
        ).apply {
            Log.d(TAG, "Created drawing file: $absolutePath")
        }
    }

    // TODO: Add onBackPressed handling for unsaved changes confirmation
}
