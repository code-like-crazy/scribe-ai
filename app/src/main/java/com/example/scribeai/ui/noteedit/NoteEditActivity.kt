package com.example.scribeai.ui.noteedit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
// Removed Menu and MenuItem imports
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // Import viewModels delegate
import androidx.appcompat.app.AppCompatActivity
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
            // TODO: Switch to text input mode (show/hide relevant views)
            Toast.makeText(this, "Text Mode selected (Not fully implemented)", Toast.LENGTH_SHORT).show()
            hideImagePreview() // Hide image if switching back to text
        }
        binding.buttonModeDraw.setOnClickListener {
            // TODO: Switch to drawing mode
            Toast.makeText(this, "Draw Mode selected (Not implemented)", Toast.LENGTH_SHORT).show()
            hideImagePreview()
        }
        binding.buttonModeCamera.setOnClickListener {
            // For now, just launch gallery picker. Camera capture later.
            // TODO: Add dialog to choose Camera or Gallery
            launchGalleryPicker()
        }
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

        // TODO: Setup takePictureLauncher later
    }

    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // TODO: Implement launchCamera() later

    private fun showImagePreview(imageUri: Uri) {
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
        binding.imagePreview.visibility = View.GONE
        // Optionally show the text content input when hiding image
        // binding.contentInputLayout.visibility = View.VISIBLE
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
        val content = binding.contentEditText.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            Snackbar.make(binding.root, R.string.error_empty_note, Snackbar.LENGTH_SHORT).show()
            return
        }

        // Call ViewModel to save the note
        // ViewModel now handles imageUri internally based on selectedImageUri LiveData
        viewModel.saveNote(title, content)

        // Show confirmation, set result, and finish
        Toast.makeText(this, R.string.note_saved_confirmation, Toast.LENGTH_SHORT).show() // Use Toast for simplicity
        setResult(Activity.RESULT_OK) // Signal success to MainActivity/NotePreviewActivity
        finish()
    }

    // TODO: Add onBackPressed handling for unsaved changes confirmation
}
