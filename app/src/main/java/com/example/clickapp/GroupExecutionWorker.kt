package com.example.clickapp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class GroupExecutionWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID) ?: return Result.failure()

        Log.d(TAG, "Executing scheduled group: $groupId")

        val storage = ShortcutStorage(applicationContext)
        val group = storage.getGroup(groupId) ?: run {
            Log.e(TAG, "Group not found: $groupId")
            return Result.failure()
        }

        if (!group.schedulingEnabled) {
            Log.d(TAG, "Scheduling disabled for group: ${group.name}")
            return Result.success()
        }

        val events = storage.getEventsForGroup(groupId)
        if (events.isEmpty()) {
            Log.d(TAG, "No events in group: ${group.name}")
            return Result.success()
        }

        // Execute events sequentially with delays
        executeEventsSequentially(events)

        return Result.success()
    }

    private fun executeEventsSequentially(events: List<ClickShortcut>) {
        var totalDelay = 0L
        val handler = Handler(Looper.getMainLooper())

        events.forEachIndexed { index, event ->
            handler.postDelayed({
                executeEvent(event)
                Log.d(TAG, "Executed event ${index + 1}/${events.size}: ${event.name}")
            }, totalDelay)

            // Add delay for next event: 1500ms for app load + configured delay
            totalDelay += 1500 + event.delayAfterMs

            // If double-click enabled, add that delay too
            if (event.doubleClickEnabled) {
                totalDelay += event.doubleClickDelayMs
            }
        }
    }

    private fun executeEvent(event: ClickShortcut) {
        try {
            ClickAccessibilityService.configureClick(
                packageName = event.packageName,
                useCoordinates = event.useCoordinates,
                targetText = event.targetText,
                clickX = event.clickX,
                clickY = event.clickY,
                doubleClickEnabled = event.doubleClickEnabled,
                doubleClickDelayMs = event.doubleClickDelayMs
            )

            val launchIntent = applicationContext.packageManager
                .getLaunchIntentForPackage(event.packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(launchIntent)
                Log.d(TAG, "Launched app for event: ${event.name}")
            } else {
                Log.e(TAG, "Could not launch app for event: ${event.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing event: ${e.message}", e)
        }
    }

    companion object {
        const val TAG = "GroupExecutionWorker"
        const val KEY_GROUP_ID = "group_id"
    }
}
