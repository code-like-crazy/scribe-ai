package com.example.scribeai.ui.notelist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scribeai.R
import com.example.scribeai.data.Note
import com.example.scribeai.databinding.NoteItemBinding
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(
        private val onItemClicked: (Note) -> Unit,
        private val onDeleteClicked: (Note) -> Unit // Add callback for delete
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    // Date formatter
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    /**
     * ViewHolder class that holds references to the views for each note item. Uses ViewBinding for
     * type-safe access to views.
     */
    class NoteViewHolder(
            private val binding: NoteItemBinding,
            private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note, onItemClicked: (Note) -> Unit, onDeleteClicked: (Note) -> Unit) {
            binding.textViewNoteTitle.text =
                    note.title.ifBlank {
                        itemView.context.getString(R.string.title_new_note)
                    } // Use string resource
            binding.textViewNoteContentSnippet.text = note.content
            binding.textViewNoteAuthor.text =
                    itemView.context.getString(R.string.note_author_placeholder) // Placeholder
            binding.textViewNoteDate.text = dateFormat.format(Date(note.createdAt))

            // Set click listener for the entire item view
            binding.root.setOnClickListener { onItemClicked(note) }

            // Set click listener for the delete button
            binding.buttonDeleteNote.setOnClickListener { onDeleteClicked(note) }
        }
    }

    /** Called when RecyclerView needs a new ViewHolder of the given type to represent an item. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = NoteItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Pass the date formatter to the ViewHolder
        return NoteViewHolder(binding, dateFormat)
    }

    /** Called by RecyclerView to display the data at the specified position. */
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val currentNote = getItem(position)
        // Pass both click listeners to the bind method
        holder.bind(currentNote, onItemClicked, onDeleteClicked)
    }

    /** DiffUtil.ItemCallback implementation to efficiently calculate list updates. */
    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id // Check if items represent the same entity
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem // Check if item contents are identical
        }
    }
}
