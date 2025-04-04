package com.example.scribeai.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "notes")
@TypeConverters(Converters::class)
data class Note(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        @ColumnInfo(name = "title") val title: String = "", // Add title field
        @ColumnInfo(name = "content") val content: String, // Extracted text or typed content
        @ColumnInfo(name = "image_uri")
        val imageUri: String? = null, // URI of the original image, if applicable
        @ColumnInfo(name = "tags")
        val tags: List<String> = emptyList(), // AI-generated or user-defined tags
        @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
        @ColumnInfo(name = "note_type") val noteType: NoteType = NoteType.TEXT // Add type field
)

// Enum to represent the type of note
enum class NoteType {
    TEXT, // Typed note
    HANDWRITTEN, // Digital handwritten note (canvas data might be stored differently later)
    IMAGE // Note created from an image via OCR
}
