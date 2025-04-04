package com.example.scribeai.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.collections.ArrayList

class DrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPath = Path()
    private val paths = ArrayList<Pair<Path, Paint>>() // Store paths and their paints
    private val paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        strokeWidth = 8f // Default stroke width
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var motionTouchEventX = 0f
    private var motionTouchEventY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw all completed paths
        for ((path, paint) in paths) {
            canvas.drawPath(path, paint)
        }
        // Draw the current path being drawn
        canvas.drawPath(currentPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        motionTouchEventX = event.x
        motionTouchEventY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> touchStart()
            MotionEvent.ACTION_MOVE -> touchMove()
            MotionEvent.ACTION_UP -> touchUp()
            else -> return false // Ignore other actions
        }
        invalidate() // Redraw the view
        return true
    }

    private fun touchStart() {
        currentPath.reset()
        currentPath.moveTo(motionTouchEventX, motionTouchEventY)
    }

    private fun touchMove() {
        currentPath.lineTo(motionTouchEventX, motionTouchEventY)
    }

    private fun touchUp() {
        // Create a new Paint object for this path to capture current settings
        val pathPaint = Paint(paint)
        // Add the completed path and its paint to the list
        paths.add(Pair(Path(currentPath), pathPaint))
        // Reset the current path for the next stroke
        currentPath.reset()
    }

    // Function to clear the canvas
    fun clearCanvas() {
        paths.clear()
        currentPath.reset()
        invalidate()
    }

    // Function to get the current drawing as a Bitmap
    fun getBitmap(): Bitmap {
        // Create a bitmap with the same dimensions as the view
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Create a canvas associated with the bitmap
        val canvas = Canvas(bitmap)
        // Optional: Fill background if needed (e.g., if view background is transparent)
        // canvas.drawColor(Color.WHITE) // Example: White background
        // Draw the view's content (all paths) onto the bitmap's canvas
        draw(canvas)
        return bitmap
    }

    // TODO: Add functions to change paint color, stroke width, etc.
}
