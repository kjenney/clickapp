package com.example.clickapp

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class ShortcutExecutionWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val shortcutId = inputData.getString(KEY_SHORTCUT_ID) ?: return Result.failure()

        Log.d(TAG, "Executing scheduled shortcut: $shortcutId")

        val storage = ShortcutStorage(applicationContext)
        val shortcut = storage.getShortcut(shortcutId) ?: run {
            Log.e(TAG, "Shortcut not found: $shortcutId")
            return Result.failure()
        }

        // Check if scheduling is still enabled
        if (!shortcut.schedulingEnabled) {
            Log.d(TAG, "Scheduling disabled for shortcut: ${shortcut.name}")
            return Result.success()
        }

        try {
            // Configure the accessibility service
            ClickAccessibilityService.configureClick(
                packageName = shortcut.packageName,
                useCoordinates = shortcut.useCoordinates,
                targetText = shortcut.targetText,
                clickX = shortcut.clickX,
                clickY = shortcut.clickY,
                doubleClickEnabled = shortcut.doubleClickEnabled,
                doubleClickDelayMs = shortcut.doubleClickDelayMs
            )

            // Launch the target app
            val launchIntent = applicationContext.packageManager
                .getLaunchIntentForPackage(shortcut.packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(launchIntent)
                Log.d(TAG, "Launched app: ${shortcut.appName}")
                return Result.success()
            } else {
                Log.e(TAG, "Could not launch app: ${shortcut.appName}")
                return Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing shortcut: ${e.message}", e)
            return Result.failure()
        }
    }

    companion object {
        const val TAG = "ShortcutExecutionWorker"
        const val KEY_SHORTCUT_ID = "shortcut_id"
    }
}
