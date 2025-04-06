package com.example.scribeai

// Keep only necessary imports
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater // Import LayoutInflater
import android.view.MotionEvent
import android.view.View // Add View import for listener
import android.view.inputmethod.InputMethodManager
import android.widget.Button // Import Button
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scribeai.core.data.AppDatabase // Explicit import
import com.example.scribeai.core.data.Note // Explicit import
import com.example.scribeai.core.data.NoteRepository // Explicit import
import com.example.scribeai.databinding.ActivityMainBinding
import com.example.scribeai.features.noteedit.NoteEditActivity // Explicit import
import com.example.scribeai.features.notelist.FilterNotesDialogFragment // Explicit import
import com.example.scribeai.features.notelist.NoteListViewModel // Explicit import
import com.example.scribeai.features.notelist.NoteListViewModelFactory // Explicit import
import com.example.scribeai.features.notelist.NotesAdapter // Explicit import
import com.example.scribeai.features.notelist.SwipeToDeleteCallback // Explicit import
import com.example.scribeai.features.notepreview.NotePreviewActivity // Explicit import
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first // Import needed for listener impl
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), FilterNotesDialogFragment.FilterDialogListener {

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

        // Setup Floating Action Button (FAB) click listener
        setupFab()

        // Setup Search View
        setupSearchView()

        // Observe notes from ViewModel
        observeNotes()

        // Setup focus clearing for search
        setupFocusClearing()

        // Setup filter button click listener
        setupFilterButton()
    }

    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        noteListViewModel.setSearchQuery(s.toString())
                    }
                    override fun afterTextChanged(s: Editable?) {}
                }
        )
    }

    private fun observeNotes() {
        // Use lifecycleScope and repeatOnLifecycle for safe collection from Flows
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe the combined and filtered notes directly from the ViewModel
                noteListViewModel.notes.collect { filteredNotesList ->
                    // Submit the already filtered list to the ListAdapter
                    notesAdapter.submitList(filteredNotesList)
                    Log.d("MainActivity", "Filtered notes observed: ${filteredNotesList.size}")

                    // Update section title with count
                    binding.textViewSectionTitleNotes.text =
                            resources.getQuantityString(
                                    R.plurals.section_title_notes_with_count,
                                    filteredNotesList.size,
                                    filteredNotesList.size
                            )

                    // Toggle empty state visibility using the new layout IDs
                    binding.recyclerViewNotes.isVisible = filteredNotesList.isNotEmpty()
                    binding.textViewEmptyState.isVisible = filteredNotesList.isEmpty()
                }
            }
        }
    }

    // Helper function to launch NoteEditActivity (for creating new notes)
    private fun launchNoteEditActivity() {
        val intent = Intent(this, NoteEditActivity::class.java)
        startActivityForResult(intent, NOTE_ACTIVITY_REQUEST_CODE)
    }

    // Helper function to launch NotePreviewActivity (for viewing existing notes)
    private fun launchNotePreviewActivity(noteId: Long) {
        val intent =
                Intent(this, NotePreviewActivity::class.java).apply {
                    putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
                }
        startActivityForResult(intent, NOTE_ACTIVITY_REQUEST_CODE)
    }

    private fun setupFab() {
        binding.fabAddNote.setOnClickListener {
            launchNoteEditActivity() // Launch edit screen for new note
        }
    }

    private fun setupRecyclerView() {
        // Initialize adapter with click listener
        notesAdapter =
                NotesAdapter(
                        onItemClicked = { note -> launchNotePreviewActivity(note.id) },
                        onDeleteClicked = { note -> showDeleteConfirmationDialog(note) }
                )

        binding.recyclerViewNotes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter

            // Setup swipe-to-delete using the dedicated callback class
            val swipeToDeleteCallback =
                    SwipeToDeleteCallback(notesAdapter) { note ->
                        // Action to perform on swipe: show delete confirmation
                        showDeleteConfirmationDialog(note)
                    }
            val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    private fun setupFilterButton() {
        binding.textViewShowFilters.setOnClickListener {
            // Show the DialogFragment
            FilterNotesDialogFragment().show(supportFragmentManager, FilterNotesDialogFragment.TAG)
        }
    }

    // Removed populateChips function (moved to DialogFragment)
    // Removed showFilterDialog function (replaced by DialogFragment)

    private fun setupFocusClearing() {
        // Explicitly type the lambda parameters
        binding.heroConstraintLayout.setOnTouchListener { v: View, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (binding.searchEditText.isFocused) {
                    binding.searchEditText.clearFocus()
                    // Hide keyboard
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
                }
            }
            // Return false so touch events are still processed by children if needed
            false
        }
    }

    private fun showDeleteConfirmationDialog(note: Note) {
        // Inflate the custom layout
        val customView =
                LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirmation, null)

        // Create the dialog using the builder
        val dialog =
                AlertDialog.Builder(this)
                        .setView(customView) // Set the custom view
                        .create() // Create the dialog instance

        // Find buttons inside the custom view and set listeners
        val cancelButton = customView.findViewById<Button>(R.id.button_cancel)
        val deleteButton = customView.findViewById<Button>(R.id.button_delete)

        cancelButton.setOnClickListener {
            dialog.dismiss() // User cancelled
        }

        deleteButton.setOnClickListener {
            // User confirmed deletion
            noteListViewModel.deleteNote(note)
            Snackbar.make(binding.root, R.string.note_deleted_confirmation, Snackbar.LENGTH_SHORT)
                    .show()
            dialog.dismiss() // Dismiss the dialog
        }

        dialog.show() // Show the configured dialog
    }

    // Remove the triggerNotesUpdate function entirely

    // Remove the deprecated onActivityResult as list updates are handled by the observer

    companion object {
        // Keep the request code if needed for NoteEditActivity/NotePreviewActivity results,
        // but it's not strictly necessary for list refresh anymore.
        private const val NOTE_ACTIVITY_REQUEST_CODE = 1
    }

    // --- FilterDialogListener Implementation ---

    override fun onFiltersApplied(selectedTags: Set<String>) {
        noteListViewModel.setSelectedFilterTags(selectedTags)
    }

    override fun onFiltersCleared() {
        noteListViewModel.setSelectedFilterTags(emptySet())
    }

    // Provide data needed by the dialog from the ViewModel
    override suspend fun getAllAvailableTags(): List<String> {
        // Note: Using .first() blocks until the first value is emitted.
        // Consider if a cached value or different approach is better for performance.
        return noteListViewModel.getAllTags().first()
    }

    override fun getCurrentSelectedTags(): Set<String> {
        // Provide the current selection from the ViewModel's state
        return noteListViewModel.selectedFilterTags.value
    }
    // --- End FilterDialogListener Implementation ---
}
