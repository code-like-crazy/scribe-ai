package com.example.scribeai

// Keep only necessary imports
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View // Add View import for listener
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.Note
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.databinding.ActivityMainBinding
import com.example.scribeai.ui.noteedit.NoteEditActivity
import com.example.scribeai.ui.notelist.NoteListViewModel
import com.example.scribeai.ui.notelist.NoteListViewModelFactory
import com.example.scribeai.ui.notelist.NotesAdapter
import com.example.scribeai.ui.notepreview.NotePreviewActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
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
    private var selectedFilterTags = mutableSetOf<String>()

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
                noteListViewModel.notes.collect { notesList ->
                    // Apply tag filter locally
                    val filteredList =
                            if (selectedFilterTags.isEmpty()) {
                                notesList
                            } else {
                                notesList.filter { note ->
                                    note.tags.any { tag -> selectedFilterTags.contains(tag) }
                                }
                            }

                    // Submit the filtered list to the ListAdapter
                    notesAdapter.submitList(filteredList)
                    Log.d(
                            "MainActivity",
                            "Notes observed: ${notesList.size}, Filtered: ${filteredList.size}"
                    )

                    // Update section title with count
                    binding.textViewSectionTitleNotes.text =
                            getString(R.string.section_title_notes_with_count, filteredList.size)

                    // Toggle empty state visibility using the new layout IDs
                    binding.recyclerViewNotes.isVisible = filteredList.isNotEmpty()
                    binding.textViewEmptyState.isVisible = filteredList.isEmpty()
                }
            }
        }
    }

    // Helper function to launch NoteEditActivity (for creating new notes)
    private fun launchNoteEditActivity() {
        val intent = Intent(this, NoteEditActivity::class.java)
        // No ID needed for new note
        startActivityForResult(
                intent,
                NOTE_ACTIVITY_REQUEST_CODE
        ) // Use startActivityForResult to refresh list
    }

    // Helper function to launch NotePreviewActivity (for viewing existing notes)
    private fun launchNotePreviewActivity(noteId: Long) {
        val intent =
                Intent(this, NotePreviewActivity::class.java).apply {
                    putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId) // Reuse the same extra key
                }
        startActivityForResult(
                intent,
                NOTE_ACTIVITY_REQUEST_CODE
        ) // Use startActivityForResult to refresh list
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
                        onItemClicked = { note ->
                            launchNotePreviewActivity(
                                    note.id
                            ) // Launch preview screen for existing note
                        },
                        onDeleteClicked = { note ->
                            showDeleteConfirmationDialog(note) // Show confirmation before deleting
                        }
                )

        binding.recyclerViewNotes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter

            // Setup swipe-to-delete
            ItemTouchHelper(
                            object :
                                    ItemTouchHelper.SimpleCallback(
                                            0, // No drag and drop
                                            ItemTouchHelper.LEFT // Only enable left swipe
                                    ) {
                                override fun onMove(
                                        recyclerView: RecyclerView,
                                        viewHolder: RecyclerView.ViewHolder,
                                        target: RecyclerView.ViewHolder
                                ): Boolean = false // Disable drag and drop

                                override fun onSwiped(
                                        viewHolder: RecyclerView.ViewHolder,
                                        direction: Int
                                ) {
                                    val position = viewHolder.adapterPosition
                                    val note = notesAdapter.currentList[position]
                                    // Reset the item position (to prevent visual glitch)
                                    notesAdapter.notifyItemChanged(position)
                                    // Show delete confirmation
                                    showDeleteConfirmationDialog(note)
                                }
                            }
                    )
                    .attachToRecyclerView(this)
        }
    }

    private fun setupFilterButton() {
        binding.textViewShowFilters.setOnClickListener { showFilterDialog() }
    }

    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_notes, null)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupTags)
        val buttonClear = dialogView.findViewById<View>(R.id.buttonClearFilters)
        val buttonApply = dialogView.findViewById<View>(R.id.buttonApplyFilters)

        val dialog =
                AlertDialog.Builder(this)
                        .setView(dialogView) // Use the correct overload
                        .create()

        // Define predefined tags and colors (simplified example)
        val predefinedTags =
                listOf(
                        "CS 101",
                        "MATH 203",
                        "PHYS 110",
                        "CHEM 305",
                        "ECON 101",
                        "Lecture Notes",
                        "Study Guide",
                        "Lab Report",
                        "Assignment",
                        "Project Ideas",
                        "Exam Prep",
                        "Reading Summary"
                )
        // Basic color cycling for demonstration
        val tagColors =
                listOf(
                        Pair(0xFFE1F5FE.toInt(), 0xFF01579B.toInt()), // Light Blue / Dark Blue
                        Pair(0xFFE8F5E9.toInt(), 0xFF1B5E20.toInt()), // Light Green / Dark Green
                        Pair(0xFFFFFDE7.toInt(), 0xFFF57F17.toInt()), // Light Yellow / Dark Yellow
                        Pair(0xFFFCE4EC.toInt(), 0xFF880E4F.toInt()), // Light Pink / Dark Pink
                        Pair(0xFFF3E5F5.toInt(), 0xFF4A148C.toInt()), // Light Purple / Dark Purple
                        Pair(0xFFE0F2F1.toInt(), 0xFF004D40.toInt()), // Light Teal / Dark Teal
                        Pair(0xFFFFF8E1.toInt(), 0xFFFF6F00.toInt()), // Light Orange / Dark Orange
                        Pair(0xFFEDE7F6.toInt(), 0xFF311B92.toInt()), // Light Indigo / Dark Indigo
                        Pair(
                                0xFFFBE9E7.toInt(),
                                0xFFBF360C.toInt()
                        ), // Light Deep Orange / Dark Deep Orange
                        Pair(0xFFE3F2FD.toInt(), 0xFF0D47A1.toInt()), // Lighter Blue / Darker Blue
                        Pair(0xFFFFEBEE.toInt(), 0xFFB71C1C.toInt()), // Light Red / Dark Red
                        Pair(
                                0xFFE8EAF6.toInt(),
                                0xFF1A237E.toInt()
                        ) // Lighter Indigo / Darker Indigo
                )

        chipGroup.removeAllViews() // Clear existing chips
        predefinedTags.forEachIndexed { index, tag ->
            val chip =
                    Chip(this@MainActivity).apply {
                        text = tag
                        isCheckable = true
                        isChecked = selectedFilterTags.contains(tag)

                        // Apply colors
                        val colors = tagColors[index % tagColors.size]
                        chipBackgroundColor =
                                android.content.res.ColorStateList.valueOf(colors.first)
                        setTextColor(colors.second)
                        // Optional: Add ripple effect, corner radius etc. via style or
                        // programmatically
                    }
            chipGroup.addView(chip)
        }

        buttonClear.setOnClickListener {
            selectedFilterTags.clear()
            chipGroup.clearCheck()
            // Re-collecting the flow is needed to update the UI with cleared filters
            // Calling observeNotes() directly might not re-trigger collection properly.
            // Instead, rely on the existing observer reacting to state changes if possible,
            // or manage filter state in ViewModel for better reactivity.
            // For simplicity here, we'll just clear the set and let the existing observer run.
            // Trigger the main observer to re-filter the list
            triggerNotesUpdate()
            dialog.dismiss()
        }

        buttonApply.setOnClickListener {
            selectedFilterTags.clear()
            val checkedIds = chipGroup.checkedChipIds // Get list of checked chip IDs
            checkedIds.forEach { chipId: Int -> // Explicitly type the loop variable
                val chip = chipGroup.findViewById<Chip>(chipId)
                if (chip != null) { // Check if chip is found
                    selectedFilterTags.add(chip.text.toString())
                }
            }
            // Trigger the main observer to re-filter the list
            triggerNotesUpdate()
            dialog.dismiss()
        }

        dialog.show()
    }

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
        AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirmation_title)
                .setMessage(R.string.delete_confirmation_message)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    // Call ViewModel to delete the note
                    noteListViewModel.deleteNote(note)
                    // Optional: Show a confirmation Snackbar
                    Snackbar.make(
                                    binding.root,
                                    R.string.note_deleted_confirmation,
                                    Snackbar.LENGTH_SHORT
                            )
                            .show()
                }
                .setNegativeButton(R.string.action_cancel, null) // Just dismiss the dialog
                .show()
    }

    // Helper function to manually trigger notes update/re-collection
    private fun triggerNotesUpdate() {
        // This is a simplified way to hint that the data might need refreshing.
        // A more robust solution involves managing filter state in the ViewModel.
        lifecycleScope.launch {
            // Re-collect the latest notes based on current search/filter state
            noteListViewModel.notes.first()
            // The observer should handle submitting the list
        }
    }

    // Remove the deprecated onActivityResult as list updates are handled by the observer

    companion object {
        // Keep the request code if needed for NoteEditActivity/NotePreviewActivity results,
        // but it's not strictly necessary for list refresh anymore.
        private const val NOTE_ACTIVITY_REQUEST_CODE = 1
    }
}
