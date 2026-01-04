package com.example.clickapp

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class SchedulerManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule a periodic execution for the given shortcut
     */
    fun scheduleShortcut(shortcut: ClickShortcut) {
        if (!shortcut.schedulingEnabled || shortcut.scheduleInterval == ScheduleInterval.NONE) {
            Log.d(TAG, "Scheduling not enabled for shortcut: ${shortcut.name}")
            return
        }

        // Cancel any existing scheduled work for this shortcut
        cancelSchedule(shortcut.id)

        val intervalMinutes = shortcut.scheduleInterval.intervalMinutes
        if (intervalMinutes <= 0) {
            Log.w(TAG, "Invalid interval for shortcut: ${shortcut.name}")
            return
        }

        val workTag = getWorkTag(shortcut.id)
        val inputData = workDataOf(
            ShortcutExecutionWorker.KEY_SHORTCUT_ID to shortcut.id
        )

        val workRequest = PeriodicWorkRequestBuilder<ShortcutExecutionWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .addTag(workTag)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            workTag,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "Scheduled shortcut '${shortcut.name}' with interval: ${shortcut.scheduleInterval.displayName}")
    }

    /**
     * Cancel the scheduled execution for the given shortcut ID
     */
    fun cancelSchedule(shortcutId: String) {
        val workTag = getWorkTag(shortcutId)
        workManager.cancelAllWorkByTag(workTag)
        Log.d(TAG, "Cancelled schedule for shortcut: $shortcutId")
    }

    /**
     * Reschedule all shortcuts that have scheduling enabled
     * This should be called when the app starts to restore schedules
     */
    fun rescheduleAllShortcuts() {
        val storage = ShortcutStorage(context)
        val shortcuts = storage.getAllShortcuts()

        var scheduledCount = 0
        shortcuts.forEach { shortcut ->
            if (shortcut.schedulingEnabled && shortcut.scheduleInterval != ScheduleInterval.NONE) {
                scheduleShortcut(shortcut)
                scheduledCount++
            }
        }

        Log.d(TAG, "Rescheduled $scheduledCount shortcuts")
    }

    /**
     * Check if a shortcut is currently scheduled
     */
    fun isScheduled(shortcutId: String): Boolean {
        val workTag = getWorkTag(shortcutId)
        val workInfos = workManager.getWorkInfosByTag(workTag).get()
        return workInfos.any { !it.state.isFinished }
    }

    private fun getWorkTag(shortcutId: String): String {
        return "shortcut_$shortcutId"
    }

    private fun getGroupWorkTag(groupId: String): String {
        return "group_$groupId"
    }

    // ==================== Event Group Scheduling ====================

    /**
     * Schedule a periodic execution for the given event group
     */
    fun scheduleGroup(group: EventGroup) {
        if (!group.schedulingEnabled || group.scheduleInterval == ScheduleInterval.NONE) {
            Log.d(TAG, "Scheduling not enabled for group: ${group.name}")
            return
        }

        cancelGroupSchedule(group.id)

        val intervalMinutes = group.scheduleInterval.intervalMinutes
        if (intervalMinutes <= 0) {
            Log.w(TAG, "Invalid interval for group: ${group.name}")
            return
        }

        val workTag = getGroupWorkTag(group.id)
        val inputData = workDataOf(
            GroupExecutionWorker.KEY_GROUP_ID to group.id
        )

        val workRequest = PeriodicWorkRequestBuilder<GroupExecutionWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setInputData(inputData)
            .addTag(workTag)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            workTag,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "Scheduled group '${group.name}' with interval: ${group.scheduleInterval.displayName}")
    }

    /**
     * Cancel the scheduled execution for the given group ID
     */
    fun cancelGroupSchedule(groupId: String) {
        val workTag = getGroupWorkTag(groupId)
        workManager.cancelAllWorkByTag(workTag)
        Log.d(TAG, "Cancelled schedule for group: $groupId")
    }

    /**
     * Reschedule all groups that have scheduling enabled
     */
    fun rescheduleAllGroups() {
        val storage = ShortcutStorage(context)
        val groups = storage.getAllGroups()

        var scheduledCount = 0
        groups.forEach { group ->
            if (group.schedulingEnabled && group.scheduleInterval != ScheduleInterval.NONE) {
                scheduleGroup(group)
                scheduledCount++
            }
        }

        Log.d(TAG, "Rescheduled $scheduledCount groups")
    }

    /**
     * Check if a group is currently scheduled
     */
    fun isGroupScheduled(groupId: String): Boolean {
        val workTag = getGroupWorkTag(groupId)
        val workInfos = workManager.getWorkInfosByTag(workTag).get()
        return workInfos.any { !it.state.isFinished }
    }

    companion object {
        private const val TAG = "SchedulerManager"
    }
}
