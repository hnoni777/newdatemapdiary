package io.github.hnoni777.newdatemapdiary

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paths = mutableListOf<Pair<Path, Paint>>()
    private var currentPath = Path()
    private var currentPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var isDrawingEnabled = false

    init {
        isClickable = true
        isFocusable = true
    }

    fun setDrawingEnabled(enabled: Boolean) {
        this.isDrawingEnabled = enabled
    }

    fun setStrokeColor(color: Int) {
        val newPaint = Paint(currentPaint)
        newPaint.color = color
        currentPaint = newPaint
    }

    fun setStrokeWidth(width: Float) {
        val newPaint = Paint(currentPaint)
        newPaint.strokeWidth = width
        currentPaint = newPaint
    }

    fun clear() {
        paths.clear()
        currentPath = Path()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw historical paths
        for (pair in paths) {
            canvas.drawPath(pair.first, pair.second)
        }
        
        // Draw current active path
        if (isDrawingEnabled && !currentPath.isEmpty) {
            canvas.drawPath(currentPath, currentPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawingEnabled) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                currentPath = Path()
                currentPath.moveTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                currentPath.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // Save the finished path with its snapshot of paint
                val pathPaint = Paint(currentPaint)
                paths.add(currentPath to pathPaint)
                
                currentPath = Path()
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
