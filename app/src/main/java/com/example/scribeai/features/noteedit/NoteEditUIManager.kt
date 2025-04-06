package com.example.scribeai.features.noteedit

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.example.scribeai.R
import com.example.scribeai.databinding.ActivityNoteEditBinding

interface NoteEditLauncher {
        fun launchCamera()
        fun launchGallery()
}

class NoteEditUIManager(
        private val context: Context,
        private val binding: ActivityNoteEditBinding,
        private val viewModel: NoteEditViewModel,
        private val launcher: NoteEditLauncher
) {

        fun setupInputModeButtons(currentDrawingUriProvider: () -> Uri?) {
                binding.buttonModeType.setOnClickListener { updateMode(isTextMode = true) }
                binding.buttonModeCamera.setOnClickListener {
                        updateMode(isTextMode = false)
                        showImageSourceDialog()
                }
                updateMode(isTextMode = true)
        }

        fun showTextMode() {
                updateMode(isTextMode = true)
        }

        fun showCameraMode() {
                updateMode(isTextMode = false)
        }

        private fun updateMode(isTextMode: Boolean) {
                binding.titleInputLayout.visibility = View.VISIBLE
                binding.contentInputLayout.visibility = View.VISIBLE
                binding.imagePreviewContainer.visibility =
                        if (!isTextMode && viewModel.selectedImageUri.value != null) View.VISIBLE
                        else View.GONE

                if (isTextMode) {
                        viewModel.setSelectedImageUri(null)
                }

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

                val customView =
                        LayoutInflater.from(context)
                                .inflate(R.layout.dialog_image_source_content, null)

                val dialog =
                        AlertDialog.Builder(context)
                                .setTitle(context.getString(R.string.dialog_title_image_input_mode))
                                .setView(customView)
                                .setOnCancelListener {
                                        if (viewModel.selectedImageUri.value == null) {
                                                showTextMode()
                                        }
                                }
                                .create()

                val cancelButton = customView.findViewById<Button>(R.id.dialog_button_cancel)
                val cameraButton = customView.findViewById<Button>(R.id.dialog_button_camera)
                val galleryButton = customView.findViewById<Button>(R.id.dialog_button_gallery)

                cancelButton.setOnClickListener {
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

                dialog.show()
        }
}
