package com.example.clickapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class ClickIndicatorOverlay(context: Context) : View(context) {

    private var clickX: Float = 0f
    private var clickY: Float = 0f

    private val outerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val innerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ringPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    fun setClickPosition(x: Float, y: Float) {
        clickX = x
        clickY = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw outer ring
        canvas.drawCircle(clickX, clickY, 40f, ringPaint)

        // Draw red dot
        canvas.drawCircle(clickX, clickY, 20f, outerPaint)

        // Draw white center
        canvas.drawCircle(clickX, clickY, 8f, innerPaint)
    }
}
