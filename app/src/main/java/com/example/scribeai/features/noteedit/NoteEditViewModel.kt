package com.example.scribeai.features.noteedit

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.scribeai.core.data.Note
import com.example.scribeai.core.data.NoteRepository
import com.example.scribeai.core.data.NoteType
import java.util.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class NoteEditViewModel(private val repository: NoteRepository, private val noteId: Long?) :
        ViewModel() {

    private val _note = MutableLiveData<Note?>()
    val note: LiveData<Note?> = _note

    private val _selectedImageUri = MutableLiveData<Uri?>()
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    private val _tags = MutableLiveData<List<String>>(emptyList())
    val tags: LiveData<List<String>> = _tags

    init {
        if (noteId != null) {
            loadNote(noteId)
        }
    }

    private fun loadNote(id: Long) {
        viewModelScope.launch {
            val loadedNote = repository.getNoteById(id).firstOrNull()
            _note.value = loadedNote
            _tags.value = loadedNote?.tags ?: emptyList()
            loadedNote?.imageUri?.let { _selectedImageUri.value = Uri.parse(it) }
        }
    }

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun addTag(tag: String) {
        val currentTags = _tags.value?.toMutableList() ?: mutableListOf()
        val trimmedTag = tag.trim()
        if (trimmedTag.isNotBlank() && !currentTags.contains(trimmedTag)) {
            currentTags.add(trimmedTag)
            _tags.value = currentTags
        }
    }

    fun removeTag(tag: String) {
        val currentTags = _tags.value?.toMutableList() ?: mutableListOf()
        if (currentTags.remove(tag)) {
            _tags.value = currentTags
        }
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
            val imageUriToSave = _selectedImageUri.value

            if (existingNote == null) {
                val noteType =
                        when {
                            imageUriToSave?.toString()?.endsWith(".png") == true -> NoteType.IMAGE
                            imageUriToSave != null -> NoteType.IMAGE
                            else -> NoteType.TEXT
                        }
                val newNote =
                        Note(
                                title = trimmedTitle,
                                content = trimmedContent,
                                imageUri = imageUriToSave?.toString(),
                                tags = _tags.value ?: emptyList(),
                                createdAt = timestamp,
                                noteType = noteType
                        )
                repository.insert(newNote)
            } else {
                val noteType =
                        when {
                            imageUriToSave?.toString()?.endsWith(".png") == true -> NoteType.IMAGE
                            imageUriToSave != null -> NoteType.IMAGE
                            else -> existingNote.noteType
                        }

                val updatedNote =
                        existingNote.copy(
                                title = trimmedTitle,
                                content = trimmedContent,
                                imageUri = imageUriToSave?.toString() ?: existingNote.imageUri,
                                tags = _tags.value ?: existingNote.tags,
                                createdAt = timestamp,
                                noteType = noteType
                        )
                repository.update(updatedNote)
            }

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
