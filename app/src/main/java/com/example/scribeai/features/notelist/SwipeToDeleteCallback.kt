package com.example.scribeai.features.notelist

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.scribeai.core.data.Note

class SwipeToDeleteCallback(
        private val adapter: NotesAdapter,
        private val onSwipedAction: (Note) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            val note = adapter.currentList[position]
            adapter.notifyItemChanged(position)
            onSwipedAction(note)
        }
    }
}
