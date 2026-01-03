package com.example.clickapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class CoordinatePickerOverlay(
    context: Context,
    private val onCoordinatesPicked: (x: Int, y: Int) -> Unit,
    private val onDismiss: () -> Unit
) : View(context) {

    private var currentX: Float = -1f
    private var currentY: Float = -1f

    private val overlayPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val crosshairPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val instructionPaint = Paint().apply {
        color = Color.WHITE
        textSize = 56f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw semi-transparent overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Draw instruction at top
        canvas.drawText(
            "Tap anywhere to pick coordinates",
            width / 2f,
            150f,
            instructionPaint
        )

        // Draw crosshair at touch location
        if (currentX >= 0 && currentY >= 0) {
            // Horizontal line
            canvas.drawLine(0f, currentY, width.toFloat(), currentY, crosshairPaint)
            // Vertical line
            canvas.drawLine(currentX, 0f, currentX, height.toFloat(), crosshairPaint)
            // Coordinate text
            canvas.drawText(
                "(${currentX.toInt()}, ${currentY.toInt()})",
                currentX,
                currentY - 30f,
                textPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                currentX = event.rawX
                currentY = event.rawY
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                onCoordinatesPicked(event.rawX.toInt(), event.rawY.toInt())
                onDismiss()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
