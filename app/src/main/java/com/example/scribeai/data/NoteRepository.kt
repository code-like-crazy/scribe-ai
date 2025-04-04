package com.example.scribeai.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository module for handling data operations. Abstracts the data sources (e.g., Room database)
 * from the rest of the app.
 */
class NoteRepository(private val noteDao: NoteDao) {

    // Flow for observing all notes, ordered by creation date
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    // Suspending function to insert a note (runs on a background thread)
    suspend fun insert(note: Note): Long {
        return noteDao.insert(note)
    }

    // Suspending function to update a note
    suspend fun update(note: Note) {
        noteDao.update(note)
    }

    // Suspending function to delete a note
    suspend fun delete(note: Note) {
        noteDao.delete(note)
    }

    // Function to get a specific note by ID (returns a Flow)
    fun getNoteById(id: Long): Flow<Note?> {
        return noteDao.getNoteById(id)
    }

    // Function to search notes by title or content (returns a Flow)
    fun searchNotes(query: String): Flow<List<Note>> {
        // Add '%' wildcards for LIKE query
        val formattedQuery = "%${query}%"
        return noteDao.searchNotes(formattedQuery)
    }

    // Function to get notes by tag (returns a Flow)
    fun getNotesByTag(tag: String): Flow<List<Note>> {
        return noteDao.getNotesByTag(
                tag
        ) // DAO handles wildcard internally if needed, or adjust here
    }

    // Function to get notes by type (returns a Flow)
    fun getNotesByType(noteType: NoteType): Flow<List<Note>> {
        return noteDao.getNotesByType(noteType)
    }

    // Function to get all unique tags (returns a Flow)
    fun getAllTags(): Flow<List<String>> {
        // 1. Get the Flow<List<String>> where each String is like "tag1,tag2", "tag3", etc.
        return noteDao.getAllTags().map { tagStringsList ->
            // 2. Process the list of tag strings
            tagStringsList
                    .flatMap { tagsString ->
                        // Split each string by comma (or your delimiter), trim whitespace
                        tagsString.split(',').map { it.trim() }
                    }
                    .filter { it.isNotBlank() } // Remove empty tags
                    .distinct() // Get unique tags
        }
    }
}
