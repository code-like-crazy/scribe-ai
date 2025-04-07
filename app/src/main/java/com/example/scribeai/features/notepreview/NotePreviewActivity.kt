package com.example.scribeai.features.notepreview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.scribeai.R
import com.example.scribeai.core.data.AppDatabase
import com.example.scribeai.core.data.Note
import com.example.scribeai.core.data.NoteRepository
import com.example.scribeai.databinding.ActivityNotePreviewBinding
import com.example.scribeai.features.noteedit.NoteEditActivity
import com.example.scribeai.features.noteedit.NoteEditViewModel
import com.example.scribeai.features.noteedit.NoteEditViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import java.text.SimpleDateFormat
import java.util.*

class NotePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotePreviewBinding
    private var currentNote: Note? = null
    private var noteId: Long = -1L
    private lateinit var markwon: Markwon

    private val noteEditViewModel: NoteEditViewModel by viewModels {
        val repository = NoteRepository(AppDatabase.getDatabase(application).noteDao())
        NoteEditViewModelFactory(repository, if (noteId != -1L) noteId else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Markwon (Theme application removed temporarily to resolve build errors)
        // TODO: Re-add theme application via CorePlugin once dimen errors resolve after build/sync
        // val theme = MarkwonTheme.builderWithDefaults(this)
        //     .codeBlockMargin(resources.getDimensionPixelSize(R.dimen.code_block_margin))
        //
        // .listItemMarginBottom(resources.getDimensionPixelSize(R.dimen.list_item_margin_bottom))
        //     .build()

        markwon = Markwon.builder(this).usePlugin(LinkifyPlugin.create()).build()

        setupToolbar()
        noteId = intent.getLongExtra(NoteEditActivity.EXTRA_NOTE_ID, -1L)

        if (noteId != -1L) {
            noteEditViewModel.note.observe(this) { note ->
                note?.let {
                    currentNote = it
                    updateNoteDisplay(it)
                }
                        ?: run {
                            Toast.makeText(this, R.string.error_loading_note, Toast.LENGTH_SHORT)
                                    .show()
                            finish()
                        }
            }
        } else {
            Toast.makeText(this, R.string.error_invalid_note_id, Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.buttonEditNote.setOnClickListener {
            if (noteId != -1L) {
                val intent =
                        Intent(this, NoteEditActivity::class.java).apply {
                            putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId)
                        }
                startActivityForResult(intent, EDIT_NOTE_REQUEST_CODE)
            }
        }

        binding.buttonDeleteNote.setOnClickListener { showDeleteConfirmationDialog() }
        binding.buttonShareNote.setOnClickListener { showShareDialog() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun updateNoteDisplay(note: Note) {
        supportActionBar?.title = getString(R.string.note_preview_title)

        binding.textViewNoteTitle.text = note.title

        val dateFormat = SimpleDateFormat("MMMM d, yyyy, h:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(note.createdAt))
        binding.textViewNoteDatetime.text = formattedDate

        val processedContent = (note.content ?: "").replace("\n", "  \n")
        markwon.setMarkdown(binding.textViewNoteContent, processedContent)

        note.imageUri?.let { uriString ->
            binding.imageSection.visibility = View.VISIBLE
            Glide.with(this)
                    .load(Uri.parse(uriString))
                    .fitCenter()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_broken_image)
                    .into(binding.imagePreview)
        }
                ?: run { binding.imageSection.visibility = View.GONE }

        if (note.tags.isNotEmpty()) {
            binding.tagsSection.visibility = View.VISIBLE
            binding.chipGroupTags.removeAllViews()
            note.tags.forEach { tag ->
                val chip =
                        Chip(this).apply { // Ensure binding is used
                            text = tag
                        }
                binding.chipGroupTags.addView(chip)
            }
        } else {
            binding.tagsSection.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmationDialog() {
        currentNote?.let { noteToDelete ->
            val customView =
                    LayoutInflater.from(this).inflate(R.layout.dialog_delete_confirmation, null)

            val dialog = AlertDialog.Builder(this).setView(customView).create()

            val cancelButton = customView.findViewById<Button>(R.id.button_cancel)
            val deleteButton = customView.findViewById<Button>(R.id.button_delete)

            cancelButton.setOnClickListener { dialog.dismiss() }

            deleteButton.setOnClickListener {
                noteEditViewModel.deleteNote(noteToDelete)
                Toast.makeText(this, R.string.note_deleted_confirmation, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                dialog.dismiss()
                finish()
            }

            dialog.show()
        }
                ?: run {
                    Toast.makeText(this, R.string.error_loading_note, Toast.LENGTH_SHORT).show()
                }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_NOTE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun showShareDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_share_note, null)
        dialog.setContentView(view)

        view.findViewById<Button>(R.id.button_share_copy_link).setOnClickListener {
            Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.button_share_pdf).setOnClickListener {
            Toast.makeText(this, "PDF sharing not implemented", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.button_share_text).setOnClickListener {
            Toast.makeText(this, "Text sharing not implemented", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        const val EDIT_NOTE_REQUEST_CODE = 1002
    }
}
