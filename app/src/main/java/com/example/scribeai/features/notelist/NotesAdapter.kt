package com.example.scribeai.features.notelist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scribeai.R
import com.example.scribeai.core.data.Note
import com.example.scribeai.databinding.NoteItemBinding
import java.text.SimpleDateFormat
import java.util.*

class NotesAdapter(
        private val onItemClicked: (Note) -> Unit,
        private val onDeleteClicked: (Note) -> Unit // Add callback for delete
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    class NoteViewHolder(
            private val binding: NoteItemBinding,
            private val dateFormat: SimpleDateFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note, onItemClicked: (Note) -> Unit) {
            binding.textViewNoteTitle.text =
                    note.title.ifBlank { itemView.context.getString(R.string.title_new_note) }
            binding.textViewNoteContentSnippet.text = note.content
            binding.textViewNoteAuthor.text =
                    itemView.context.getString(R.string.note_author_placeholder)
            binding.textViewNoteDate.text = dateFormat.format(Date(note.createdAt))

            binding.root.setOnClickListener { onItemClicked(note) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = NoteItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding, dateFormat)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val currentNote = getItem(position)
        holder.bind(currentNote, onItemClicked)
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}
