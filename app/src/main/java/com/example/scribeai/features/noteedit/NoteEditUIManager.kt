package com.example.scribeai.features.noteedit

import android.content.Context
import android.util.Log // Ensure Log is imported
import android.view.View // Ensure View is imported
import com.example.scribeai.databinding.ActivityNoteEditBinding

// TODO: Refactor or remove this class as its original purpose (managing input mode buttons)
// is no longer relevant after the UI changes. The remaining functions might be moved
// directly into the Activity or another more suitable manager class if still needed.
class NoteEditUIManager(
        private val context: Context,
        private val binding: ActivityNoteEditBinding,
        private val viewModel: NoteEditViewModel,
        private val launcher: NoteEditLauncher // Keep launcher if needed for other UI actions
) {

    companion object {
        private const val TAG = "NoteEditUIManager"
    }

    // Functions related to input mode buttons (setupInputModeButtons, selectMode,
    // updateButtonStates, updateButtonAppearance) are removed as the buttons no longer exist.

    // Keep functions that manage visibility of content/preview if they are still used elsewhere,
    // otherwise, they can also be removed or refactored.

    fun showTextMode() {
        // This function might still be relevant if called from somewhere else,
        // e.g., when an image is explicitly cleared.
        // It should now only handle visibility changes, not button states.
        Log.d(TAG, "Showing Text Mode UI elements")
        binding.contentInputLayout.visibility = View.VISIBLE
        binding.imagePreviewContainer.visibility = View.GONE
        binding.formattingToolbar.root.visibility = View.VISIBLE // Show formatting toolbar
        // No button states to update
        // Consider if clearing the image URI should happen here or be managed solely by the Activity/ViewModel
        // viewModel.setSelectedImageUri(null)
    }

    fun showCameraMode() {
        // This function's original purpose was tied to the camera button.
        // It might be obsolete now, as image selection is triggered differently.
        // If kept, it should only manage visibility based on whether an image exists.
        Log.d(TAG, "Showing Camera Mode UI elements (Image Preview)")
        binding.contentInputLayout.visibility = View.VISIBLE // Keep content visible
        binding.imagePreviewContainer.visibility =
                if (viewModel.selectedImageUri.value != null) View.VISIBLE else View.GONE
        binding.formattingToolbar.root.visibility = View.VISIBLE // Keep formatting toolbar visible

        // The logic to launch the image source dialog is now in the Activity.
        // No button states to update.
    }

    fun showDrawMode() {
        // This function was likely tied to a draw button that might also be removed.
        // If drawing functionality is kept but triggered differently, adapt this.
        // Otherwise, remove it.
        Log.d(TAG, "Showing Draw Mode UI elements (Image Preview)")
        binding.contentInputLayout.visibility = View.GONE // Hide text input? Check requirements
        binding.imagePreviewContainer.visibility = View.VISIBLE // Show drawing preview
        binding.formattingToolbar.root.visibility = View.GONE // Hide formatting toolbar? Check requirements
        // No button states to update.
    }
}
