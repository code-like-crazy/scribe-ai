package com.example.scribeai.features.noteedit

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

object NoteEditKeyboardUtil {

    fun hideKeyboard(activity: Activity) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = activity.currentFocus
        if (view == null) {
            // If no view currently has focus, create a new one to get a window token
            view = View(activity)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun setupKeyboardDismissalOnTouch(view: View, activity: Activity) {
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard(activity)
                v.clearFocus() // Clear focus from the touched view itself
                activity.currentFocus?.let { focusedView ->
                    if (focusedView is EditText) {
                        focusedView.clearFocus()
                    }
                }
            }
            false
        }
    }
}
