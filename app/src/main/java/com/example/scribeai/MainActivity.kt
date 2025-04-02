package com.example.scribeai

import android.os.Bundle
import android.util.Log
import android.widget.SearchView // Use android.widget.SearchView for compatibility
import android.widget.Toast
import androidx.activity.viewModels // Import for viewModels delegate
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.databinding.ActivityMainBinding // Import generated ViewBinding class
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
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter { note ->
            // Handle note item click -> Navigate to Note Detail Screen later
            Toast.makeText(this, "Clicked on: ${note.title}", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Clicked note ID: ${note.id}")
            // Intent intent = new Intent(this, NoteDetailActivity.class);
            // intent.putExtra("NOTE_ID", note.id);
            // startActivity(intent);
        }

        binding.notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
            // Optional: Add ItemDecoration for spacing if needed
        }
    }

     private fun setupFab() {
        binding.fabAddNote.setOnClickListener {
            // Handle FAB click -> Navigate to Note Creation Screen later
             Toast.makeText(this, "Add new note clicked", Toast.LENGTH_SHORT).show()
             Log.d("MainActivity", "FAB clicked")
            // Intent intent = new Intent(this, NoteCreateActivity.class);
            // startActivity(intent);
        }
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
                }
            }
        }
    }
}
