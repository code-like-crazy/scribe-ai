package com.example.scribeai.features.notelist

// Removed: import androidx.fragment.app.activityViewModels
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.scribeai.databinding.DialogFilterNotesBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.lang.ClassCastException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FilterNotesDialogFragment : DialogFragment() {

    // Interface for communication back to the Activity
    interface FilterDialogListener {
        fun onFiltersApplied(selectedTags: Set<String>)
        fun onFiltersCleared()
        // Function to get necessary data from Activity/ViewModel
        suspend fun getAllAvailableTags(): List<String>
        fun getCurrentSelectedTags(): Set<String>
    }

    private var listener: FilterDialogListener? = null

    // View Binding instance
    private var _binding: DialogFilterNotesBinding? = null
    private val binding
        get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Attach the listener
        try {
            listener = context as FilterDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement FilterDialogListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogFilterNotesBinding.inflate(LayoutInflater.from(context))

        // Get initial state from the listener (Activity)
        val currentSelectedTags = listener?.getCurrentSelectedTags() ?: emptySet()

        // Define predefined tags and colors (Consider moving this)
        val predefinedTags =
                listOf(
                        "CS 101",
                        "MATH 203",
                        "PHYS 110",
                        "CHEM 305",
                        "ECON 101",
                        "Lecture Notes",
                        "Study Guide",
                        "Lab Report",
                        "Assignment",
                        "Project Ideas",
                        "Exam Prep",
                        "Reading Summary"
                )
        val tagColors =
                listOf(
                        Pair(0xFFE1F5FE.toInt(), 0xFF01579B.toInt()),
                        Pair(0xFFE8F5E9.toInt(), 0xFF1B5E20.toInt()),
                        Pair(0xFFFFFDE7.toInt(), 0xFFF57F17.toInt()),
                        Pair(0xFFFCE4EC.toInt(), 0xFF880E4F.toInt()),
                        Pair(0xFFF3E5F5.toInt(), 0xFF4A148C.toInt()),
                        Pair(0xFFE0F2F1.toInt(), 0xFF004D40.toInt()),
                        Pair(0xFFFFF8E1.toInt(), 0xFFFF6F00.toInt()),
                        Pair(0xFFEDE7F6.toInt(), 0xFF311B92.toInt()),
                        Pair(0xFFFBE9E7.toInt(), 0xFFBF360C.toInt()),
                        Pair(0xFFE3F2FD.toInt(), 0xFF0D47A1.toInt()),
                        Pair(0xFFFFEBEE.toInt(), 0xFFB71C1C.toInt()),
                        Pair(0xFFE8EAF6.toInt(), 0xFF1A237E.toInt())
                )

        // Fetch all unique tags via listener and populate chips
        lifecycleScope.launch {
            val allAvailableTags = listener?.getAllAvailableTags() ?: emptyList()
            populateChips(
                    binding.chipGroupTags,
                    predefinedTags,
                    allAvailableTags,
                    tagColors,
                    currentSelectedTags
            )
        }

        binding.buttonClearFilters.setOnClickListener {
            listener?.onFiltersCleared() // Call listener method
            dismiss()
        }

        binding.buttonApplyFilters.setOnClickListener {
            val newSelectedTags = mutableSetOf<String>()
            binding.chipGroupTags.checkedChipIds.forEach { chipId ->
                val chip = binding.chipGroupTags.findViewById<Chip>(chipId)
                chip?.text?.toString()?.let { tag -> newSelectedTags.add(tag) }
            }
            listener?.onFiltersApplied(newSelectedTags) // Call listener method
            dismiss()
        }

        return AlertDialog.Builder(requireActivity()).setView(binding.root).create()
    }

    // Helper function to populate chips
    private fun populateChips(
            chipGroup: ChipGroup,
            predefinedTags: List<String>,
            allAvailableTags: List<String>,
            tagColors: List<Pair<Int, Int>>,
            currentSelectedTags: Set<String>
    ) {
        val displayTags = (predefinedTags + allAvailableTags).distinct().sorted()
        chipGroup.removeAllViews()
        displayTags.forEachIndexed { index, tag ->
            val chip =
                    Chip(context).apply {
                        text = tag
                        isCheckable = true
                        isChecked = currentSelectedTags.contains(tag)
                        val colors = tagColors[index % tagColors.size]
                        chipBackgroundColor =
                                android.content.res.ColorStateList.valueOf(colors.first)
                        setTextColor(colors.second)
                    }
            chipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null // Clean up listener
    }

    companion object {
        const val TAG = "FilterNotesDialog"
    }
}
