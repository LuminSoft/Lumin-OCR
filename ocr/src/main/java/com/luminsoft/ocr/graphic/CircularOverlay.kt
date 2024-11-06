package com.luminsoft.ocr.graphic
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View


class CircularOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#99000000") // Semi-transparent black background
        style = Paint.Style.FILL
    }

    private val circlePaint = Paint().apply {
        color = Color.WHITE // Default border color
        style = Paint.Style.STROKE
        strokeWidth = 8f // Width of the circle border
        isAntiAlias = true
    }

    private val clearPaint = Paint().apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // Clear mode for cutout
    }

    // Function to update circle border color
    fun updateCircleColor(newColor: Int) {
        circlePaint.color = newColor
        invalidate() // Redraw the view with the new color
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Draw the semi-transparent black overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Calculate the center and radius for the circular cutout
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = 450f // Adjust as needed to fit your design

        // Draw the circular cutout with clear paint
        canvas.drawCircle(centerX, centerY, radius, clearPaint)

        // Draw the circle border
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        canvas.restoreToCount(saveCount)
    }
}
