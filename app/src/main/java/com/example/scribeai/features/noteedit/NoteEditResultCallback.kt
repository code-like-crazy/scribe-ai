package com.example.scribeai.features.noteedit

import android.net.Uri

interface NoteEditResultCallback {
    fun onImageResult(uri: Uri)
    fun onDrawResult(uri: Uri)
    fun onResultCancelledOrFailed(isNewDrawingAttempt: Boolean)
    fun showTextModeUI()
    fun showError(message: String)
}
