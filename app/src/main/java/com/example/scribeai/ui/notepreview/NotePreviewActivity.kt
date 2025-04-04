package com.example.scribeai.ui.notepreview

import android.app.Activity
import android.content.Intent
import android.os.Bundle // Added missing import
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog // Added AlertDialog import
import androidx.appcompat.app.AppCompatActivity
// Removed Observer import as we'll observe LiveData directly
import com.example.scribeai.R
import com.example.scribeai.data.AppDatabase // Added missing import
import com.example.scribeai.data.Note
import com.example.scribeai.data.NoteRepository // Added missing import
import com.example.scribeai.databinding.ActivityNotePreviewBinding // Added ViewBinding import
import com.example.scribeai.ui.noteedit.NoteEditActivity
import com.example.scribeai.ui.noteedit.NoteEditViewModel
import com.example.scribeai.ui.noteedit.NoteEditViewModelFactory

class NotePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotePreviewBinding // ViewBinding instance
    private var currentNote: Note? = null
    private var noteId: Long = -1L // Use Long for ID, initialize to -1

    // Use the same ViewModel as NoteEdit for consistency in data handling
    private val noteEditViewModel: NoteEditViewModel by viewModels {
        // Instantiate repository here
        val repository = NoteRepository(AppDatabase.getDatabase(application).noteDao())
        // Pass repository and noteId (as Long?)
        NoteEditViewModelFactory(repository, if (noteId != -1L) noteId else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve noteId as Long
        noteId = intent.getLongExtra(NoteEditActivity.EXTRA_NOTE_ID, -1L)

        if (noteId != -1L) {
            // Observe the LiveData from the ViewModel
            noteEditViewModel.note.observe(this) { note ->
                note?.let {
                    currentNote = it
                    binding.textViewNoteContent.text = it.content
                    // Use ViewBinding for title if you have a Toolbar in the layout,
                    // otherwise set Activity title directly
                    title = it.title?.takeIf { t -> t.isNotBlank() } ?: getString(R.string.note_preview_title)
                } ?: run {
                    // Handle case where note is null after loading (e.g., deleted)
                    Toast.makeText(this, R.string.error_loading_note, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            // Trigger loading if ViewModel hasn't loaded it yet (e.g., process death)
            // The ViewModel's init block should handle initial load if noteId was passed correctly.
        } else {
            // Handle error: No valid note ID provided
            Toast.makeText(this, R.string.error_invalid_note_id, Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.buttonEditNote.setOnClickListener {
            if (noteId != -1L) {
                val intent = Intent(this, NoteEditActivity::class.java).apply {
                    putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId) // Pass Long ID
                }
                // Consider using ActivityResultLauncher for modern approach
                startActivityForResult(intent, EDIT_NOTE_REQUEST_CODE)
            }
        }

        binding.buttonDeleteNote.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        currentNote?.let { noteToDelete ->
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirmation_title)
                .setMessage(R.string.delete_confirmation_message)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    // User confirmed deletion
                    noteEditViewModel.deleteNote(noteToDelete)
                    Toast.makeText(this, R.string.note_deleted_confirmation, Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK) // Signal MainActivity that something changed
                    finish()
                }
                .setNegativeButton(R.string.action_cancel) { dialog, _ ->
                    // User cancelled
                    dialog.dismiss()
                }
                .show()
        } ?: run {
            // Should not happen if the button is only enabled when note is loaded, but handle defensively
            Toast.makeText(this, R.string.error_loading_note, Toast.LENGTH_SHORT).show()
        }
    }

    // Handle the result from NoteEditActivity
    @Deprecated("Deprecated in Java") // Suppress deprecation warning for onActivityResult
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_NOTE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Note was saved in NoteEditActivity.
            // The LiveData observer should automatically update the UI if the content changed.
            // We can optionally finish this screen or just stay. Let's finish for now.
            setResult(Activity.RESULT_OK) // Signal MainActivity that something might have changed
            finish()
        }
    }

    companion object {
        // Use a more descriptive name, matching NoteEditActivity if possible
        const val EDIT_NOTE_REQUEST_CODE = 1002 // Or reuse NoteEditActivity.REQUEST_CODE if public
    }
}
