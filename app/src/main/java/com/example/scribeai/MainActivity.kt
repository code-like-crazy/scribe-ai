package com.example.scribeai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View // Import View
import android.widget.SearchView // Use android.widget.SearchView for compatibility
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible // Import isVisible extension
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.databinding.ActivityMainBinding // Import generated ViewBinding class
import com.example.scribeai.ui.noteedit.NoteEditActivity
import com.example.scribeai.ui.notelist.NoteListViewModel
import com.example.scribeai.ui.notelist.NoteListViewModelFactory
import com.example.scribeai.ui.notelist.NotesAdapter
import com.example.scribeai.ui.notepreview.NotePreviewActivity // Import NotePreviewActivity
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

        // Set up the Toolbar
        setSupportActionBar(binding.toolbar)

        // Setup RecyclerView Adapter
        setupRecyclerView()

        // Setup FAB click listener
        setupFab()

        // Setup Search View
        setupSearchView()

        // Observe notes from ViewModel
        observeNotes()

        // Setup Empty State Button Listener
        setupEmptyStateButton()
    }


    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Optional: Handle submission if needed (e.g., close keyboard)
                return false // Let the system handle default behavior (usually nothing)
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Update ViewModel with the new search query
                noteListViewModel.setSearchQuery(newText.orEmpty())
                return true // Indicate we handled the text change
            }
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

                    // Toggle empty state visibility
                    binding.notesRecyclerView.isVisible = notesList.isNotEmpty()
                    binding.emptyStateLayout.isVisible = notesList.isEmpty()
                }
            }
        }
    }

    private fun setupEmptyStateButton() {
        binding.emptyStateAddButton.setOnClickListener {
            // Same action as FAB
            launchNoteEditActivity()
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

    // Update setupFab and setupRecyclerView to use the correct helper functions
    private fun setupFab() {
        binding.fabAddNote.setOnClickListener {
            launchNoteEditActivity() // Launch edit screen for new note
        }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter { note ->
            launchNotePreviewActivity(note.id) // Launch preview screen for existing note
        }

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
            // Optional: Add ItemDecoration for spacing if needed
        }
    }

    // Handle results from NoteEditActivity and NotePreviewActivity to refresh list if needed
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == NOTE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            // A note was potentially created, edited, or deleted.
            // The ViewModel should ideally handle refreshing the list automatically
            // if data source changes are observed correctly.
            // For simplicity, we can just re-observe or trigger a refresh manually if needed.
            // Toast.makeText(this, "List might need refresh", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val NOTE_ACTIVITY_REQUEST_CODE = 1 // Request code for starting note activities
    }
}
