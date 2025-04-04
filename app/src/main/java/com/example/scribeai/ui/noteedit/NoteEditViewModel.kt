package com.example.scribeai.ui.noteedit

import android.net.Uri // Import Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scribeai.data.Note
import com.example.scribeai.data.NoteRepository
import com.example.scribeai.data.NoteType
import java.util.*
import kotlinx.coroutines.flow.firstOrNull // Import firstOrNull
import kotlinx.coroutines.launch

class NoteEditViewModel(
        private val repository: NoteRepository,
        private val noteId: Long? // Null if creating a new note
) : ViewModel() {

    // LiveData to hold the note being edited (if noteId is not null)
    private val _note = MutableLiveData<Note?>()
    val note: LiveData<Note?> = _note

    // LiveData to hold the URI of the image selected for OCR/display
    private val _selectedImageUri = MutableLiveData<Uri?>()
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    init {
        if (noteId != null) {
            loadNote(noteId)
        }
    }

    private fun loadNote(id: Long) {
        viewModelScope.launch {
            // Collect the first value from the flow (or null if flow is empty)
            _note.value = repository.getNoteById(id).firstOrNull()
        }
    }

    // Function called by Activity when an image is selected
    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun saveNote(title: String, content: String) {
        viewModelScope.launch {
            val trimmedTitle = title.trim()
            val trimmedContent = content.trim()

            if (trimmedTitle.isEmpty() &&
                            trimmedContent.isEmpty() &&
                            _selectedImageUri.value == null
            ) {
                // Optional: Add some error state LiveData if needed
                return@launch
            }

            val existingNote = _note.value // Get current note if editing
            val timestamp = System.currentTimeMillis() // Use Long timestamp
            val imageUriToSave =
                    _selectedImageUri.value // Already contains both camera and drawing URIs

            if (existingNote == null) {
                // Create new note
                val noteType =
                        when {
                            imageUriToSave?.toString()?.endsWith(".png") == true ->
                                    NoteType.IMAGE // Drawing
                            imageUriToSave != null -> NoteType.IMAGE // Camera photo
                            else -> NoteType.TEXT
                        }
                val newNote =
                        Note(
                                title = trimmedTitle,
                                content = trimmedContent,
                                imageUri = imageUriToSave?.toString(),
                                tags = emptyList(),
                                createdAt = timestamp,
                                noteType = noteType
                        )
                repository.insert(newNote)
            } else {
                // Update existing note
                val noteType =
                        when {
                            imageUriToSave?.toString()?.endsWith(".png") == true ->
                                    NoteType.IMAGE // Drawing
                            imageUriToSave != null -> NoteType.IMAGE // Camera photo
                            else -> existingNote.noteType
                        }

                val updatedNote =
                        existingNote.copy(
                                title = trimmedTitle,
                                content = trimmedContent,
                                imageUri = imageUriToSave?.toString() ?: existingNote.imageUri,
                                createdAt = timestamp,
                                noteType = noteType
                        )
                repository.update(updatedNote)
            }

            // Clear the selected image URI after saving
            _selectedImageUri.postValue(null)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }
}

// Factory for creating NoteEditViewModel with dependencies
class NoteEditViewModelFactory(private val repository: NoteRepository, private val noteId: Long?) :
        ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return NoteEditViewModel(repository, noteId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
