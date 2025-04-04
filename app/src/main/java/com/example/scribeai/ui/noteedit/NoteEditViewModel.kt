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
import kotlinx.coroutines.flow.firstOrNull // Import firstOrNull
import kotlinx.coroutines.launch
import java.util.*

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

            if (trimmedTitle.isEmpty() && trimmedContent.isEmpty()) {
                // Optional: Add some error state LiveData if needed
                return@launch
            }

            val existingNote = _note.value // Get current note if editing
            val timestamp = System.currentTimeMillis() // Use Long timestamp
            val imageUriToSave = _selectedImageUri.value // Get the currently selected image URI

            if (existingNote == null) {
                // Create new note
                val noteType = if (imageUriToSave != null) NoteType.IMAGE else NoteType.TEXT
                val newNote = Note(
                    title = trimmedTitle,
                    content = trimmedContent, // OCR text is appended in Activity for now
                    imageUri = imageUriToSave?.toString(), // Save URI as String
                    tags = emptyList(), // Not handled yet
                    createdAt = timestamp,
                    noteType = noteType
                )
                repository.insert(newNote)
            } else {
                // Update existing note
                // Decide if updating image URI is allowed or needed. For now, keep original if editing.
                // If we want to allow changing/removing image, more logic is needed.
                // Let's assume for now we only update text content if editing an image note,
                // or update text/image if editing a text note and adding an image.
                val noteType = if (imageUriToSave != null) NoteType.IMAGE else existingNote.noteType // Keep original type if no new image
                val finalImageUri = imageUriToSave?.toString() ?: existingNote.imageUri // Use new URI if present, else keep old

                val updatedNote = existingNote.copy(
                    title = trimmedTitle,
                    content = trimmedContent, // Update content
                    imageUri = finalImageUri, // Update image URI if changed
                    createdAt = timestamp, // Update timestamp on edit
                    noteType = noteType // Update type if changed
                    // Keep original tags for now
                )
                repository.update(updatedNote)
            }
            // Clear the selected image URI after saving
            _selectedImageUri.postValue(null)
        }
    }
}

// Factory for creating NoteEditViewModel with dependencies
class NoteEditViewModelFactory(
    private val repository: NoteRepository,
    private val noteId: Long?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteEditViewModel(repository, noteId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
