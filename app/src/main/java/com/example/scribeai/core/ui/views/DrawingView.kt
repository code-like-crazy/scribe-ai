package com.example.scribeai.core.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.collections.ArrayList

class DrawingView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    private var backgroundBitmap: Bitmap? = null // To hold the initial drawing
    private var currentPath = Path()
    private val paths = ArrayList<Pair<Path, Paint>>() // Store paths and their paints
    private val paint =
            Paint().apply {
                color = Color.BLACK
                isAntiAlias = true
                strokeWidth = 8f // Default stroke width
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }

    private var motionTouchEventX = 0f
    private var motionTouchEventY = 0f
    private var lastX = 0f // Store the last X coordinate
    private var lastY = 0f // Store the last Y coordinate

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the background bitmap first if it exists
        backgroundBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        // Draw all completed paths on top
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
        lastX = motionTouchEventX // Initialize last points
        lastY = motionTouchEventY
    }

    private fun touchMove() {
        // Calculate midpoint for quadratic Bezier curve
        val midX = (motionTouchEventX + lastX) / 2
        val midY = (motionTouchEventY + lastY) / 2
        // Use quadTo for smoother curves
        currentPath.quadTo(lastX, lastY, midX, midY)
        // Update last points
        lastX = motionTouchEventX
        lastY = motionTouchEventY
    }

    private fun touchUp() {
        // Ensure the final segment is drawn
        currentPath.lineTo(motionTouchEventX, motionTouchEventY)

        // Create a new Paint object for this path to capture current settings
        val pathPaint = Paint(paint)
        // Add a *copy* of the completed path and its paint to the list
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

    // Function to load an existing bitmap as the background
    fun loadBitmap(bitmap: Bitmap) {
        // Ensure the bitmap is mutable if needed, or create a mutable copy
        backgroundBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        // Clear existing paths if loading a new background
        paths.clear()
        currentPath.reset()
        invalidate() // Redraw with the new background
    }

    // Function to get the current drawing as a Bitmap
    fun getBitmap(): Bitmap {
        // Create a bitmap with the same dimensions as the view
        // Consider making background transparent if needed: Bitmap.Config.ARGB_8888
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
