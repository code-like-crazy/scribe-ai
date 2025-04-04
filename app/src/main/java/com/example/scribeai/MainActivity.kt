package com.example.scribeai

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.Note
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.databinding.ActivityMainBinding
import com.example.scribeai.ui.noteedit.NoteEditActivity
import com.example.scribeai.ui.notelist.NoteListViewModel
import com.example.scribeai.ui.notelist.NoteListViewModelFactory
import com.example.scribeai.ui.notelist.NotesAdapter
import com.example.scribeai.ui.notepreview.NotePreviewActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // View Binding instance
    private lateinit var binding: ActivityMainBinding

    // ViewModel instance using activity-ktx delegate
    private val noteListViewModel: NoteListViewModel by viewModels {
        // Provide the factory to construct the ViewModel with its dependencies
        NoteListViewModelFactory(NoteRepository(AppDatabase.getDatabase(this).noteDao()))
    }

    // Adapter instance
    private lateinit var notesAdapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView Adapter (needs ViewModel)
        setupRecyclerView()

        // Setup FAB click listener
        setupFab()

        // Setup Search View
        setupSearchView()

        // Observe notes from ViewModel
        observeNotes()
    }

    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                noteListViewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

     private fun observeNotes() {
        // Use lifecycleScope and repeatOnLifecycle for safe collection from Flows
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteListViewModel.notes.collect { notesList ->
                    // Submit the updated list to the ListAdapter
                    notesAdapter.submitList(notesList)
                    Log.d("MainActivity", "Notes observed: ${notesList.size}")

                    // Toggle empty state visibility using the new layout IDs
                    binding.recyclerViewNotes.isVisible = notesList.isNotEmpty()
                    binding.textViewEmptyState.isVisible = notesList.isEmpty()
                }
            }
        }
    }

    // Helper function to launch NoteEditActivity (for creating new notes)
    private fun launchNoteEditActivity() {
        val intent = Intent(this, NoteEditActivity::class.java)
        // No ID needed for new note
        startActivityForResult(intent, NOTE_ACTIVITY_REQUEST_CODE) // Use startActivityForResult to refresh list
    }

    // Helper function to launch NotePreviewActivity (for viewing existing notes)
    private fun launchNotePreviewActivity(noteId: Long) {
        val intent = Intent(this, NotePreviewActivity::class.java).apply {
            putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId) // Reuse the same extra key
        }
        startActivityForResult(intent, NOTE_ACTIVITY_REQUEST_CODE) // Use startActivityForResult to refresh list
    }

    private fun setupFab() {
        binding.fabAddNote.setOnClickListener {
            launchNoteEditActivity() // Launch edit screen for new note
        }
    }

    private fun setupRecyclerView() {
        // Initialize adapter with both click listeners
        notesAdapter = NotesAdapter(
            onItemClicked = { note ->
                launchNotePreviewActivity(note.id) // Launch preview screen for existing note
            },
            onDeleteClicked = { note ->
                showDeleteConfirmationDialog(note) // Show confirmation before deleting
            }
        )

        binding.recyclerViewNotes.apply { // Use the correct RecyclerView ID
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
        }
    }

    private fun showDeleteConfirmationDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirmation_title)
            .setMessage(R.string.delete_confirmation_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                // Call ViewModel to delete the note
                noteListViewModel.deleteNote(note)
                // Optional: Show a confirmation Snackbar
                Snackbar.make(binding.root, R.string.note_deleted_confirmation, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null) // Just dismiss the dialog
            .show()
    }

    // Remove the deprecated onActivityResult as list updates are handled by the observer

    companion object {
        // Keep the request code if needed for NoteEditActivity/NotePreviewActivity results,
        // but it's not strictly necessary for list refresh anymore.
        private const val NOTE_ACTIVITY_REQUEST_CODE = 1
    }
}
