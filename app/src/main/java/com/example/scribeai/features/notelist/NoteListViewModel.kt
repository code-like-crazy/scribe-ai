package com.example.scribeai.features.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scribeai.core.data.Note
import com.example.scribeai.core.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedFilterTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedFilterTags: StateFlow<Set<String>> = _selectedFilterTags.asStateFlow()

    val notes: StateFlow<List<Note>> =
            combine(_searchQuery.debounce(300), _selectedFilterTags) { query, tags ->
                        Pair(query, tags)
                    }
                    .flatMapLatest { (query, tags) ->
                        val baseNotesFlow =
                                if (query.isBlank()) {
                                    repository.allNotes
                                } else {
                                    repository.searchNotes(query)
                                }

                        // Apply tag filtering if tags are selected
                        baseNotesFlow.map { notesList ->
                            if (tags.isEmpty()) {
                                notesList
                            } else {
                                notesList.filter { note ->
                                    note.tags.any { tag -> tags.contains(tag) }
                                }
                            }
                        }
                    }
                    .stateIn(
                            scope = viewModelScope,
                            started = SharingStarted.WhileSubscribed(5000),
                            initialValue = emptyList()
                    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilterTags(tags: Set<String>) {
        _selectedFilterTags.value = tags
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    fun getAllTags(): Flow<List<String>> {
        return repository.getAllTags()
    }
}

class NoteListViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return NoteListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
