package com.example.scribeai.ui.noteedit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Callback interface for the Activity
interface NoteEditResultCallback {
    fun onImageResult(uri: Uri)
    fun onDrawingResult(uri: Uri) // Kept for backward compatibility
    fun onResultCancelledOrFailed(isNewDrawingAttempt: Boolean)
    fun showTextModeUI()
    fun showError(message: String)
}

class NoteEditResultHandler(
        private val registry: ActivityResultRegistry,
        private val lifecycleOwner: LifecycleOwner,
        private val context: Context,
        private val callback: NoteEditResultCallback
) : DefaultLifecycleObserver, NoteEditLauncher { // Using existing NoteEditLauncher from UIManager

    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private var imageUriForCamera: Uri? = null
    private var currentPhotoPath: String? = null

    companion object {
        private const val TAG = "NoteEditResultHandler"
        private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".provider"
    }

    override fun onCreate(owner: LifecycleOwner) {
        registerGalleryLauncher()
        registerCameraLauncher()
    }

    private fun registerGalleryLauncher() {
        pickImageLauncher =
                registry.register(
                        "pickImage",
                        lifecycleOwner,
                        ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        result.data?.data?.let { uri ->
                            Log.d(TAG, "Image selected from gallery: $uri")
                            callback.onImageResult(uri)
                        }
                                ?: run {
                                    Log.e(TAG, "Failed to get URI from gallery result")
                                    callback.showError("Failed to load image")
                                    callback.onResultCancelledOrFailed(isNewDrawingAttempt = false)
                                }
                    } else {
                        Log.d(TAG, "Image selection cancelled or failed")
                        callback.onResultCancelledOrFailed(isNewDrawingAttempt = false)
                    }
                }
    }

    private fun registerCameraLauncher() {
        takePictureLauncher =
                registry.register(
                        "takePicture",
                        lifecycleOwner,
                        ActivityResultContracts.TakePicture()
                ) { success ->
                    val capturedUri = imageUriForCamera
                    imageUriForCamera = null

                    if (success) {
                        capturedUri?.let { uri ->
                            Log.d(TAG, "Image captured successfully: $uri")
                            callback.onImageResult(uri)
                        }
                                ?: run {
                                    Log.e(
                                            TAG,
                                            "Camera returned success but imageUriForCamera was null"
                                    )
                                    callback.showError("Failed to get captured image")
                                    callback.onResultCancelledOrFailed(isNewDrawingAttempt = false)
                                }
                    } else {
                        Log.d(TAG, "Image capture cancelled or failed")
                        deleteTemporaryFile(capturedUri)
                        callback.onResultCancelledOrFailed(isNewDrawingAttempt = false)
                    }
                }
    }

    override fun launchCamera() {
        try {
            val photoFile: File = createImageFile()
            imageUriForCamera =
                    FileProvider.getUriForFile(
                            context,
                            "${context.applicationContext.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX",
                            photoFile
                    )
            takePictureLauncher.launch(imageUriForCamera)
        } catch (ex: IOException) {
            Log.e(TAG, "Error creating image file for camera", ex)
            callback.showError("Could not start camera")
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "Error getting URI for file provider. Check authority.", ex)
            callback.showError("Could not start camera (File Provider issue)")
        }
    }

    override fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // Drawing feature removed - empty implementation
    override fun launchDrawing(intent: Intent) {
        // No-op as drawing feature is removed
        callback.onResultCancelledOrFailed(isNewDrawingAttempt = true)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(null)
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
            Log.d(TAG, "Created image file: $absolutePath")
        }
    }

    private fun deleteTemporaryFile(uri: Uri?) {
        uri?.let {
            try {
                val deletedRows = context.contentResolver.delete(uri, null, null)
                if (deletedRows > 0) {
                    Log.d(TAG, "Temporary camera file deleted: $uri")
                } else {
                    Log.w(TAG, "Temporary camera file not found or could not be deleted: $uri")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Error deleting temporary camera file (SecurityException)", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting temporary camera file", e)
            }
        }
    }

    // Methods kept for backward compatibility but do nothing
    fun setCurrentDrawingUri(uri: Uri?) {}
    fun getCurrentDrawingUri(): Uri? = null
}
