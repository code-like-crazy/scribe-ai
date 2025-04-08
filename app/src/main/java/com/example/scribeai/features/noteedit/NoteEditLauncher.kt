package com.example.scribeai.features.noteedit

/**
 * Interface defining methods for launching external activities or components
 * related to note editing, such as camera or gallery for image selection.
 */
interface NoteEditLauncher {
    /**
     * Launches the device camera to capture an image.
     */
    fun launchCamera()

    /**
     * Launches the device gallery to pick an existing image.
     */
    fun launchGallery()

    /**
     * Shows an error message to the user (e.g., via Toast).
     * Added here as both ResultHandler and UIManager might need it,
     * and it was previously part of the callback which might be refactored.
     */
     fun showError(message: String)

     /**
      * Shows a dialog to let the user choose between camera and gallery.
      * Although implemented in Activity, defining it here makes the dependency explicit
      * for classes like UIManager if they were to trigger it (though currently Activity does).
      * Consider removing if only Activity calls its own implementation directly.
      */
     // fun showImageSourceDialog() // Keep commented or remove if not needed by implementers
}
