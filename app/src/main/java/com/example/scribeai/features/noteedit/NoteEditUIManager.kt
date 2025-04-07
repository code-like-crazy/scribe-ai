package com.example.scribeai.features.noteedit

import android.content.Context
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.scribeai.R
import com.example.scribeai.databinding.ActivityNoteEditBinding
import com.google.android.material.button.MaterialButton

interface NoteEditLauncher {
        fun launchCamera()
        fun launchGallery()
}

class ImageSourceDialog(context: Context, private val onSourceSelected: (Boolean) -> Unit) :
        AlertDialog.Builder(context) {
        init {
                setTitle(R.string.dialog_image_source_title)
                setView(R.layout.dialog_image_source_content)
                create().also { dialog ->
                        dialog.setOnShowListener {
                                dialog.findViewById<Button>(R.id.dialog_button_camera)
                                        ?.setOnClickListener {
                                                onSourceSelected(true)
                                                dialog.dismiss()
                                        }
                                dialog.findViewById<Button>(R.id.dialog_button_gallery)
                                        ?.setOnClickListener {
                                                onSourceSelected(false)
                                                dialog.dismiss()
                                        }
                        }
                }
        }
}

class NoteEditUIManager(
        private val context: Context,
        private val binding: ActivityNoteEditBinding,
        private val viewModel: NoteEditViewModel,
        private val launcher: NoteEditLauncher
) {
        companion object {
                private const val MODE_TEXT = "text"
                private const val MODE_CAMERA = "camera"
                private const val MODE_DRAW = "draw"
        }

        private var onModeSelected: ((String) -> Boolean?)? = null

        fun setupInputModeButtons(onModeSelected: ((String) -> Boolean?)? = null) {
                this.onModeSelected = onModeSelected

                binding.buttonModeType.setOnClickListener { showTextMode() }
                binding.buttonModeCamera.setOnClickListener { showCameraModeDialog() }
                // binding.buttonModeDraw.setOnClickListener {
                //         val handled = onModeSelected?.invoke(MODE_DRAW) ?: false
                //         if (!handled) {
                //                 showDrawMode()
                //         }
                // }
        }

        private fun showCameraModeDialog() {
                ImageSourceDialog(context) { useCamera ->
                                if (useCamera) {
                                        launcher.launchCamera()
                                } else {
                                        launcher.launchGallery()
                                }
                        }
                        .show()
        }

        fun showTextMode() {
                updateButtonStates(MODE_TEXT)
                viewModel.setMode(MODE_TEXT)
        }

        fun showCameraMode() {
                updateButtonStates(MODE_CAMERA)
                viewModel.setMode(MODE_CAMERA)
        }

        fun showDrawMode() {
                updateButtonStates(MODE_DRAW)
                viewModel.setMode(MODE_DRAW)
        }

        private fun updateButtonStates(selectedMode: String) {
                val selectedColor = ContextCompat.getColor(context, R.color.primary)
                val selectedBgColor = ContextCompat.getColor(context, R.color.border)
                val defaultColor = ContextCompat.getColor(context, R.color.primary)
                val defaultBgColor = ContextCompat.getColor(context, R.color.border)

                fun updateButtonState(button: MaterialButton, isSelectedMode: Boolean) {
                        button.isSelected = isSelectedMode
                        button.setTextColor(if (isSelectedMode) selectedColor else defaultColor)
                        button.backgroundTintList = null
                        button.setBackgroundColor(
                                if (isSelectedMode) selectedBgColor else defaultBgColor
                        )
                }

                updateButtonState(binding.buttonModeType, selectedMode == MODE_TEXT)
                updateButtonState(binding.buttonModeCamera, selectedMode == MODE_CAMERA)
                // updateButtonState(binding.buttonModeDraw, selectedMode == MODE_DRAW)
        }
}
