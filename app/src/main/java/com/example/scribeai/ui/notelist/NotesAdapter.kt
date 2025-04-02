package com.example.scribeai.ui.notelist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scribeai.data.Note
import com.example.scribeai.databinding.NoteItemBinding // Ensure correct databinding path

class NotesAdapter(private val onItemClicked: (Note) -> Unit) :
    ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    /**
     * ViewHolder class that holds references to the views for each note item.
     * Uses ViewBinding for type-safe access to views.
     */
    class NoteViewHolder(private val binding: NoteItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note, onItemClicked: (Note) -> Unit) {
            // Use correct IDs from note_item.xml via ViewBinding
            binding.textViewNoteTitle.text = note.title.ifBlank { "Untitled Note" }
            binding.textViewNoteContentSnippet.text = note.content
            // TODO: Set note type icon based on note.noteType

            // Set click listener for the entire item view
            binding.root.setOnClickListener {
                onItemClicked(note)
            }
        }
    }

    /**
     * Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = NoteItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     */
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val currentNote = getItem(position)
        holder.bind(currentNote, onItemClicked)
    }

    /**
     * DiffUtil.ItemCallback implementation to efficiently calculate list updates.
     */
    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id // Check if items represent the same entity
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem // Check if item contents are identical
        }
    }
}
