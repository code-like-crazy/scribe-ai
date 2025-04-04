package com.example.scribeai.ui.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scribeai.data.Note
import com.example.scribeai.data.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {

    // Private MutableStateFlow to hold the current search query
    private val _searchQuery = MutableStateFlow("")

    // Public Flow representing the list of notes to display
    // It combines the latest notes from the repository with the current search query
    val notes: StateFlow<List<Note>> =
            _searchQuery
                    .debounce(300) // Add a small delay to avoid searching on every key press
                    .flatMapLatest { query ->
                        if (query.isBlank()) {
                            repository.allNotes
                        } else {
                            repository.searchNotes(query)
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
