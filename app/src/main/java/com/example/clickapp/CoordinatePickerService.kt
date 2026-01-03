package com.example.clickapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager

class CoordinatePickerService : Service() {

    companion object {
        const val ACTION_COORDINATES_PICKED = "com.example.clickapp.COORDINATES_PICKED"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: CoordinatePickerOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = CoordinatePickerOverlay(
            context = this,
            onCoordinatesPicked = { x, y ->
                broadcastCoordinates(x, y)
            },
            onDismiss = {
                removeOverlay()
                stopSelf()
            }
        )

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(overlayView, layoutParams)
    }

    private fun broadcastCoordinates(x: Int, y: Int) {
        val intent = Intent(ACTION_COORDINATES_PICKED).apply {
            setPackage(packageName)
            putExtra(EXTRA_X, x)
            putExtra(EXTRA_Y, y)
        }
        sendBroadcast(intent)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}
