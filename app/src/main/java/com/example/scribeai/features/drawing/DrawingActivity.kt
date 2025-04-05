package com.example.scribeai.features.drawing

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder // Needed for loading Bitmap from URI
import android.net.Uri
import android.os.Build // Needed for ImageDecoder version check
import android.os.Bundle
import android.provider.MediaStore // Needed for older Bitmap loading
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.scribeai.databinding.ActivityDrawingBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DrawingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawingBinding
    private var outputUri: Uri? = null // To store the URI of the saved drawing

    companion object {
        const val EXTRA_DRAWING_URI =
                "com.example.scribeai.EXTRA_DRAWING_URI" // Input URI (optional)
        const val RESULT_EXTRA_SAVED_URI =
                "com.example.scribeai.RESULT_EXTRA_SAVED_URI" // Output URI
        private const val TAG = "DrawingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarDrawing)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Enable back button

        // Load existing drawing if URI is passed
        intent.getStringExtra(EXTRA_DRAWING_URI)?.let { uriString ->
            try {
                // DrawingView removed, cannot load bitmap
                Log.w(TAG, "DrawingView removed, cannot load existing drawing from URI: $uriString")
                Toast.makeText(this, "Drawing feature disabled", Toast.LENGTH_SHORT).show()
                // Optionally finish immediately if loading was the only purpose
                // finish()
            } catch (e: Exception) { // Catch generic exception for loading issues
                Log.e(TAG, "Error loading existing drawing from URI: $uriString", e)
                Toast.makeText(this, "Failed to load existing drawing", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle toolbar navigation (back button)
        binding.toolbarDrawing.setNavigationOnClickListener {
            // TODO: Add confirmation if drawing is unsaved?
            setResult(Activity.RESULT_CANCELED) // Indicate cancellation
            finish()
        }

        // TODO: Add menu/buttons for Save, Clear, Color, Stroke Width
        // For now, let's assume saving happens on back press if changed, or via a dedicated button
        // later.
        // We'll implement a basic save on back press for now.
    }

    // Helper function to load Bitmap from URI (handles different Android versions)
    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use ImageDecoder for Android P (API 28) and above
            val source = ImageDecoder.createSource(this.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // Ensure the bitmap is mutable for drawing on top
                decoder.isMutableRequired = true
            }
        } else {
            // Use MediaStore for older versions (deprecated but necessary for compatibility)
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                    // Create a mutable copy for drawing
                    .copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun saveDrawingAndFinish() {
        // DrawingView removed, cannot save
        Log.w(TAG, "DrawingView removed, cannot save drawing.")
        Toast.makeText(this, "Drawing feature disabled", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_CANCELED) // Indicate failure or cancellation
        finish()
        /* Original save logic:
        try {
            // val bitmap = binding.drawingViewFullscreen.getBitmap() // Removed
            // ... rest of saving logic ...
            }
            outputUri =
                    FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider",
                            drawingFile
                    )
            Log.d(TAG, "Drawing saved to URI: $outputUri")

            // Return the URI to the calling activity
            val resultIntent =
                    Intent().apply { putExtra(RESULT_EXTRA_SAVED_URI, outputUri.toString()) }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } catch (e: IOException) {
            Log.e(TAG, "Error saving drawing", e)
            Toast.makeText(this, "Failed to save drawing", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED) // Indicate failure
            // finish() // Finish even if saving failed
        }
        */
    }

    // Helper to create a unique file for drawings
    @Throws(IOException::class)
    private fun createDrawingFile(): File {
        val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(null)
        return File.createTempFile("DRAWING_${timeStamp}_", ".png", storageDir).apply {
            Log.d(TAG, "Created drawing file: $absolutePath")
        }
    }

    // Override back press to trigger save (temporary solution)
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // TODO: Check if drawing has actually changed before saving
        saveDrawingAndFinish() // This method already calls finish()
        super.onBackPressed() // Call super to satisfy Lint, even if redundant
    }

    // Handle toolbar back button press (same as system back press for now)
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
