package com.example.scribeai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scribeai.core.data.AppDatabase
import com.example.scribeai.core.data.Note
import com.example.scribeai.core.data.NoteRepository
import com.example.scribeai.databinding.ActivityMainBinding
import com.example.scribeai.features.noteedit.NoteEditActivity
import com.example.scribeai.features.notelist.FilterNotesDialogFragment
import com.example.scribeai.features.notelist.NoteListViewModel
import com.example.scribeai.features.notelist.NoteListViewModelFactory
import com.example.scribeai.features.notelist.NotesAdapter
import com.example.scribeai.features.notelist.SwipeToDeleteCallback
import com.example.scribeai.features.notepreview.NotePreviewActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), FilterNotesDialogFragment.FilterDialogListener {

    private lateinit var binding: ActivityMainBinding

    private val noteListViewModel: NoteListViewModel by viewModels {
        NoteListViewModelFactory(NoteRepository(AppDatabase.getDatabase(this).noteDao()))
    }

    private lateinit var notesAdapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFab()
        setupSearchView()
        observeNotes()
        setupFocusClearing()
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteListViewModel.notes.collect { filteredNotesList ->
                    notesAdapter.submitList(filteredNotesList)
                    Log.d("MainActivity", "Filtered notes observed: ${filteredNotesList.size}")

                    binding.textViewSectionTitleNotes.text =
                            resources.getQuantityString(
                                    R.plurals.section_title_notes_with_count,
                                    filteredNotesList.size,
                                    filteredNotesList.size
                            )

                    binding.recyclerViewNotes.isVisible = filteredNotesList.isNotEmpty()
                    binding.textViewEmptyState.isVisible = filteredNotesList.isEmpty()
                }
            }
        }
    }

    private fun launchNoteEditActivity() {
        val intent = Intent(this, NoteEditActivity::class.java)
        startActivityForResult(intent, NOTE_ACTIVITY_REQUEST_CODE)
    }

    private fun launchNotePreviewActivity(noteId: Long) {
        val intent =
                Intent(this, NotePreviewActivity::class.java).apply {
                    putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
                }
        startActivityForResult(intent, NOTE_ACTIVITY_REQUEST_CODE)
    }

    private fun setupFab() {
        binding.fabAddNote.setOnClickListener { launchNoteEditActivity() }
    }

    private fun setupRecyclerView() {
        notesAdapter =
                NotesAdapter(
                        onItemClicked = { note -> launchNotePreviewActivity(note.id) },
                        onDeleteClicked = { note -> showDeleteConfirmationDialog(note) }
                )

        binding.recyclerViewNotes.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter

            val swipeToDeleteCallback =
                    SwipeToDeleteCallback(notesAdapter) { note ->
                        showDeleteConfirmationDialog(note)
                    }
            val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    private fun setupFilterButton() {
        binding.textViewShowFilters.setOnClickListener {
            FilterNotesDialogFragment().show(supportFragmentManager, FilterNotesDialogFragment.TAG)
        }
    }

    private fun setupFocusClearing() {
        binding.heroConstraintLayout.setOnTouchListener { v: View, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (binding.searchEditText.isFocused) {
                    binding.searchEditText.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
                }
            }
            false
        }
    }

    private fun showDeleteConfirmationDialog(note: Note) {
        val customView =
                LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirmation, null)

        val dialog = AlertDialog.Builder(this).setView(customView).create()

        val cancelButton = customView.findViewById<Button>(R.id.button_cancel)
        val deleteButton = customView.findViewById<Button>(R.id.button_delete)

        cancelButton.setOnClickListener { dialog.dismiss() }

        deleteButton.setOnClickListener {
            noteListViewModel.deleteNote(note)
            Snackbar.make(binding.root, R.string.note_deleted_confirmation, Snackbar.LENGTH_SHORT)
                    .show()
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        private const val NOTE_ACTIVITY_REQUEST_CODE = 1
    }

    override fun onFiltersApplied(selectedTags: Set<String>) {
        noteListViewModel.setSelectedFilterTags(selectedTags)
    }

    override fun onFiltersCleared() {
        noteListViewModel.setSelectedFilterTags(emptySet())
    }

    override suspend fun getAllAvailableTags(): List<String> {
        return noteListViewModel.getAllTags().first()
    }

    override fun getCurrentSelectedTags(): Set<String> {
        return noteListViewModel.selectedFilterTags.value
    }
}
