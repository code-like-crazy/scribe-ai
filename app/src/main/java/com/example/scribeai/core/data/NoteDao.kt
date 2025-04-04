package com.example.scribeai.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
        // Insert a new note or replace if conflict occurs (e.g., same ID)
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(note: Note): Long // Return Long (new rowId or updated rowId)

        // Update an existing note
        @Update suspend fun update(note: Note)

        // Delete a note
        @Delete suspend fun delete(note: Note)

        // Get a single note by its ID
        @Query("SELECT * FROM notes WHERE id = :id") fun getNoteById(id: Long): Flow<Note?>

        // Get all notes, ordered by creation date (most recent first)
        @Query("SELECT * FROM notes ORDER BY created_at DESC") fun getAllNotes(): Flow<List<Note>>

        // Search notes by title or content (case-insensitive)
        @Query(
                "SELECT * FROM notes WHERE LOWER(title) LIKE LOWER(:query) OR LOWER(content) LIKE LOWER(:query) ORDER BY created_at DESC"
        )
        fun searchNotes(query: String): Flow<List<Note>>

        // Get notes containing a specific tag (case-insensitive, checks within comma-separated
        // list)
        // Note: This uses LIKE which might not be the most efficient for large datasets or complex
        // tag
        // structures.
        // Consider normalizing tags into a separate table for better performance if needed.
        @Query(
                "SELECT * FROM notes WHERE LOWER(tags) LIKE LOWER('%' || :tag || '%') ORDER BY created_at DESC"
        )
        fun getNotesByTag(tag: String): Flow<List<Note>>

        // Get notes by type
        @Query("SELECT * FROM notes WHERE note_type = :noteType ORDER BY created_at DESC")
        fun getNotesByType(noteType: NoteType): Flow<List<Note>>

        // Get all tags strings (each string might contain multiple comma-separated tags)
        @Query("SELECT tags FROM notes WHERE tags IS NOT NULL AND tags != ''")
        fun getAllTags():
                Flow<List<String>> // Return Flow<List<String>> where each String is the raw
        // tags property from a Note
}
