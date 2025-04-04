package com.example.scribeai.core.data

import androidx.room.TypeConverter

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
