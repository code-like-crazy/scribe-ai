package com.example.scribeai.ui.noteedit

// Removed unused import: import androidx.core.app.ActivityCompat.startActivityForResult
// Import the ViewModel
import android.content.Context
import android.content.Intent
import android.net.Uri // Import Uri
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.example.scribeai.R
import com.example.scribeai.databinding.ActivityNoteEditBinding

// Interface to decouple UIManager from specific ActivityResultHandler implementation
interface NoteEditLauncher {
    fun launchDrawing(intent: Intent)
    // Simplified launcher interface for now
    fun launchCamera()
    fun launchGallery()
}

class NoteEditUIManager(
        private val context: Context,
        private val binding: ActivityNoteEditBinding,
        private val viewModel: NoteEditViewModel, // Needed to clear URI on text mode switch
        private val launcher: NoteEditLauncher // Use interface for launching intents
) {

    fun setupInputModeButtons(currentDrawingUriProvider: () -> Uri?) {
        binding.buttonModeType.setOnClickListener { showTextMode() }
        binding.buttonModeCamera.setOnClickListener {
            // Prepare UI first, then let user choose source via dialog
            showCameraMode() // Set UI state for image preview
            showImageSourceDialog() // Show dialog to choose Camera/Gallery
        }
    }

    fun showTextMode() {
        updateUiVisibility(contentVisible = true, imageVisible = false)
        viewModel.setSelectedImageUri(null) // Clear image URI when switching to text mode
        // currentDrawingUri is managed in Activity/ResultHandler, but ensure ViewModel is clear
    }

    // Drawing mode removed
    fun showCameraMode() {
        updateUiVisibility(contentVisible = true, imageVisible = true)
        // Don't clear drawing URI here, let the result handler do that if a photo is taken/selected
    }

    private fun updateUiVisibility(contentVisible: Boolean, imageVisible: Boolean) {
        binding.titleInputLayout.visibility = View.VISIBLE // Title is always visible
        binding.contentInputLayout.visibility = if (contentVisible) View.VISIBLE else View.GONE
        binding.imagePreview.visibility = if (imageVisible) View.VISIBLE else View.GONE
        // Update button states (optional visual feedback)
        binding.buttonModeType.isSelected = contentVisible && !imageVisible
        binding.buttonModeCamera.isSelected = imageVisible
    }

    private fun showImageSourceDialog() {
        val options =
                arrayOf(
                        context.getString(R.string.dialog_option_camera),
                        context.getString(R.string.dialog_option_gallery)
                )
        AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_title_select_image_source))
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> launcher.launchCamera() // Delegate camera launch
                        1 -> launcher.launchGallery() // Delegate gallery launch
                    }
                    dialog.dismiss() // Dismiss the dialog after selection
                }
                .setOnCancelListener {
                    // If the user cancels the dialog, and no image/drawing is currently set,
                    // revert to text mode to avoid staying in camera mode with nothing to show.
                    if (viewModel.selectedImageUri.value == null) {
                        showTextMode()
                    }
                }
                .show()
    }
}
