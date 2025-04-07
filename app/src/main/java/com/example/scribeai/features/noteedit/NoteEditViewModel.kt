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
import kotlinx.coroutines.launch

class NoteEditViewModel(private val repository: NoteRepository, private val noteId: Long?) :
        ViewModel() {

    private val _note = MutableLiveData<Note?>()
    val note: LiveData<Note?> = _note

    private val _tags = MutableLiveData<List<String>>(emptyList())
    val tags: LiveData<List<String>> = _tags

    private val _selectedImageUri = MutableLiveData<Uri?>()
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    private val _mode = MutableLiveData<String>("text")
    val mode: LiveData<String> = _mode

    init {
        noteId?.let { id ->
            viewModelScope.launch {
                repository.getNoteById(id).collect { note ->
                    _note.value = note
                    _tags.value = note?.tags ?: emptyList()
                    note?.imageUri?.let { uri -> _selectedImageUri.value = Uri.parse(uri) }
                }
            }
        }
    }

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun setMode(mode: String) {
        _mode.value = mode
    }

    fun addTag(tag: String) {
        val currentTags = _tags.value?.toMutableList() ?: mutableListOf()
        if (!currentTags.contains(tag)) {
            currentTags.add(tag)
            _tags.value = currentTags
        }
    }

    fun removeTag(tag: String) {
        val currentTags = _tags.value?.toMutableList() ?: return
        currentTags.remove(tag)
        _tags.value = currentTags
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }

    fun saveNote(title: String, content: String) {
        viewModelScope.launch {
            val note =
                    Note(
                            id = noteId ?: 0,
                            title = title,
                            content = content,
                            imageUri = _selectedImageUri.value?.toString(),
                            tags = _tags.value ?: emptyList(),
                            createdAt = System.currentTimeMillis(),
                            noteType =
                                    when (_mode.value) {
                                        "text" -> NoteType.TEXT
                                        "draw" -> NoteType.HANDWRITTEN
                                        else -> NoteType.IMAGE
                                    }
                    )

            if (noteId == null) {
                repository.insert(note)
            } else {
                repository.update(note)
            }
        }
    }
}

class NoteEditViewModelFactory(private val repository: NoteRepository, private val noteId: Long?) :
        ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return NoteEditViewModel(repository, noteId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
