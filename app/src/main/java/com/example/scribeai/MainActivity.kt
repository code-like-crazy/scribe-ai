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
import com.example.scribeai.ui.noteedit.NoteEditActivity // Import NoteEditActivity
import com.example.scribeai.ui.notelist.NoteListViewModel
import com.example.scribeai.ui.notelist.NoteListViewModelFactory
import com.example.scribeai.ui.notelist.NotesAdapter
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

    // Helper function to launch NoteEditActivity (used by FAB and empty state button)
    private fun launchNoteEditActivity(noteId: Long? = null) {
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            if (noteId != null) {
                putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
            }
        }
        startActivity(intent)
    }

    // Update setupFab and setupRecyclerView to use the helper function
    private fun setupFab() {
        binding.fabAddNote.setOnClickListener {
            launchNoteEditActivity() // No ID for new note
        }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter { note ->
            launchNoteEditActivity(note.id) // Pass ID for editing
        }

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
            // Optional: Add ItemDecoration for spacing if needed
        }
    }
}
