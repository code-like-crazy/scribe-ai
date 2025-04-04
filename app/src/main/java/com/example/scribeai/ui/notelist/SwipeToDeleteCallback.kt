package com.example.scribeai.ui.notelist

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.scribeai.data.Note

/**
 * A reusable ItemTouchHelper Callback for implementing swipe-to-delete functionality.
 *
 * @param adapter The NotesAdapter instance to get the item position.
 * @param onSwipedAction A lambda function to be executed when an item is swiped.
 * ```
 *                       It receives the Note object that was swiped.
 * ```
 */
class SwipeToDeleteCallback(
        private val adapter: NotesAdapter, // Need adapter to get the swiped item
        private val onSwipedAction: (Note) -> Unit
) :
        ItemTouchHelper.SimpleCallback(
                0, // No drag & drop support
                ItemTouchHelper.LEFT // Only allow swiping left
        ) {

    override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
    ): Boolean {
        // Drag and drop not supported
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        // Ensure the position is valid before proceeding
        if (position != RecyclerView.NO_POSITION) {
            val note = adapter.currentList[position]
            // Reset item view visually before triggering action (prevents lingering swipe state)
            adapter.notifyItemChanged(position)
            // Execute the provided action (e.g., show delete confirmation)
            onSwipedAction(note)
        }
    }

    // Optional: You can override onChildDraw to add visual feedback like background color changes
    // during swipe, but keeping it simple for now.
}
