package com.example.scribeai.ui.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scribeai.data.Note
import com.example.scribeai.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class) // For flatMapLatest
class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {

    // Private MutableStateFlow to hold the current search query
    private val _searchQuery = MutableStateFlow("")
    // Private MutableStateFlow to hold the selected filter tags
    private val _selectedFilterTags = MutableStateFlow<Set<String>>(emptySet())
    // Public StateFlow to observe the selected filter tags
    val selectedFilterTags: StateFlow<Set<String>> = _selectedFilterTags.asStateFlow()

    // Public Flow representing the list of notes to display
    // It combines the latest notes from the repository with the current search query and selected
    // tags
    val notes: StateFlow<List<Note>> =
            combine(_searchQuery.debounce(300), _selectedFilterTags) { query, tags ->
                        Pair(query, tags) // Combine query and tags into a Pair
                    }
                    .flatMapLatest { (query, tags) ->
                        // Get base notes (either all or searched)
                        val baseNotesFlow =
                                if (query.isBlank()) {
                                    repository.allNotes
                                } else {
                                    repository.searchNotes(query)
                                }

                        // Apply tag filtering if tags are selected
                        baseNotesFlow.map { notesList ->
                            if (tags.isEmpty()) {
                                notesList // No tags selected, return all base notes
                            } else {
                                notesList.filter { note ->
                                    note.tags.any { tag -> tags.contains(tag) }
                                }
                            }
                        }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started =
                                    SharingStarted.WhileSubscribed(
                                            5000
                                    ), // Keep flow active for 5s after last observer
                            initialValue = emptyList() // Initial value while loading
                    )

    // Function to update the search query
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Function to update the selected filter tags
    fun setSelectedFilterTags(tags: Set<String>) {
        _selectedFilterTags.value = tags
    }

    // Example function for deleting a note (can be called from UI)
    fun deleteNote(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    // Function to get all unique tags
    fun getAllTags(): Flow<List<String>> {
        return repository.getAllTags()
    }
}

/**
 * Factory class to instantiate NoteListViewModel with its dependencies (NoteRepository). This is
 * necessary because the ViewModel has a non-empty constructor.
 */
class NoteListViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return NoteListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
