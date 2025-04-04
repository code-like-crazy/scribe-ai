package com.example.scribeai.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // Import TypeConverters

@Database(
        entities = [Note::class],
        version = 1,
        exportSchema = false
) // Added exportSchema = false for simplicity
@TypeConverters(Converters::class) // Add this line to link the converters
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "scribeai_database" // Changed DB name slightly
                                        )
                                        // In a real app, implement proper migrations.
                                        // For this example, we'll use destructive migration.
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
