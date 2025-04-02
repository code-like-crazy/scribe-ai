package com.example.scribeai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scribeai.data.AppDatabase
import com.example.scribeai.data.NoteDao
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var noteDao: NoteDao
    private lateinit var notesAdapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize database
        noteDao = AppDatabase.getDatabase(this).noteDao()

        // Setup RecyclerView
        notesAdapter = NotesAdapter()
        findViewById<RecyclerView>(R.id.notesRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = notesAdapter
        }

        // Load notes
        lifecycleScope.launch {
            noteDao.getAllNotes().collect { notes ->
                notesAdapter.updateData(notes)
            }
        }
    }
}