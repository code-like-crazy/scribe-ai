package com.example.scribeai.features.noteedit

// Removed unused import: import androidx.core.app.ActivityCompat.startActivityForResult
// Import the ViewModel
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater // Import LayoutInflater
import android.view.View
import android.widget.Button // Import Button
import androidx.appcompat.app.AlertDialog
import com.example.scribeai.R
import com.example.scribeai.databinding.ActivityNoteEditBinding

// Interface to decouple UIManager from specific ActivityResultHandler implementation
interface NoteEditLauncher {
        // fun launchDrawing(intent: Intent) // Drawing feature removed
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
                binding.buttonModeType.setOnClickListener { updateMode(isTextMode = true) }
                binding.buttonModeCamera.setOnClickListener {
                        updateMode(isTextMode = false)
                        showImageSourceDialog()
                }
                // Initial state
                updateMode(isTextMode = true)
        }

        fun showTextMode() {
                updateMode(isTextMode = true)
        }

        fun showCameraMode() {
                updateMode(isTextMode = false)
        }

        private fun updateMode(isTextMode: Boolean) {
                // Update content visibility
                binding.titleInputLayout.visibility = View.VISIBLE
                binding.contentInputLayout.visibility = View.VISIBLE
                binding.imagePreviewContainer.visibility =
                        if (!isTextMode && viewModel.selectedImageUri.value != null) View.VISIBLE
                        else View.GONE

                if (isTextMode) {
                        viewModel.setSelectedImageUri(null)
                }

                // Update button styles for text mode
                binding.buttonModeType.apply {
                        setBackgroundColor(
                                context.getColor(
                                        if (isTextMode) R.color.colorPrimary
                                        else android.R.color.transparent
                                )
                        )
                        strokeWidth = if (isTextMode) 0 else 1
                        strokeColor = ColorStateList.valueOf(context.getColor(R.color.colorSurface))
                        setTextColor(
                                context.getColor(
                                        if (isTextMode) R.color.colorOnPrimary
                                        else R.color.colorOnSurface
                                )
                        )
                        iconTint =
                                ColorStateList.valueOf(
                                        context.getColor(
                                                if (isTextMode) R.color.colorOnPrimary
                                                else R.color.colorOnSurface
                                        )
                                )
                }

                // Update button styles for camera mode
                binding.buttonModeCamera.apply {
                        setBackgroundColor(
                                context.getColor(
                                        if (!isTextMode) R.color.colorPrimary
                                        else android.R.color.transparent
                                )
                        )
                        strokeWidth = if (!isTextMode) 0 else 1
                        strokeColor = ColorStateList.valueOf(context.getColor(R.color.colorSurface))
                        setTextColor(
                                context.getColor(
                                        if (!isTextMode) R.color.colorOnPrimary
                                        else R.color.colorOnSurface
                                )
                        )
                        iconTint =
                                ColorStateList.valueOf(
                                        context.getColor(
                                                if (!isTextMode) R.color.colorOnPrimary
                                                else R.color.colorOnSurface
                                        )
                                )
                }
        }

        private fun showImageSourceDialog() {
                val options =
                        arrayOf(
                                context.getString(R.string.dialog_option_camera),
                                context.getString(R.string.dialog_option_gallery)
                        )

                // Inflate the custom layout
                val customView =
                        LayoutInflater.from(context)
                                .inflate(R.layout.dialog_image_source_content, null)

                // Create the dialog using the builder
                val dialog =
                        AlertDialog.Builder(context)
                                .setTitle(context.getString(R.string.dialog_title_image_input_mode))
                                .setView(customView) // Set the custom view
                                .setOnCancelListener {
                                        // Handle cancellation same as before
                                        if (viewModel.selectedImageUri.value == null) {
                                                showTextMode()
                                        }
                                }
                                .create() // Create the dialog instance

                // Find buttons inside the custom view and set listeners
                val cancelButton = customView.findViewById<Button>(R.id.dialog_button_cancel)
                val cameraButton = customView.findViewById<Button>(R.id.dialog_button_camera)
                val galleryButton = customView.findViewById<Button>(R.id.dialog_button_gallery)

                cancelButton.setOnClickListener {
                        // Handle cancellation same as setOnCancelListener
                        if (viewModel.selectedImageUri.value == null) {
                                showTextMode()
                        }
                        dialog.dismiss()
                }

                cameraButton.setOnClickListener {
                        launcher.launchCamera()
                        dialog.dismiss()
                }

                galleryButton.setOnClickListener {
                        launcher.launchGallery()
                        dialog.dismiss()
                }

                dialog.show() // Show the configured dialog
        }
}
