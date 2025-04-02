package com.example.scribeai.data

import androidx.room.TypeConverter
// No explicit import for NoteType needed if in the same package, but added for clarity if issues persist
// import com.example.scribeai.data.NoteType 

class Converters {
    // Converter for List<String> (for tags)
    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String? {
        return list?.joinToString(",")
    }

    // Converter for NoteType enum
    @TypeConverter
    fun fromNoteType(value: String?): NoteType? {
        return value?.let { NoteType.valueOf(it) }
    }

    @TypeConverter
    fun toNoteType(noteType: NoteType?): String? {
        return noteType?.name
    }
}
