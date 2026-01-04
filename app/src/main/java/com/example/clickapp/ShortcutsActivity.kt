package com.example.clickapp

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ShortcutsActivity : AppCompatActivity() {

    private lateinit var shortcutStorage: ShortcutStorage
    private lateinit var schedulerManager: SchedulerManager
    private lateinit var shortcutAdapter: ShortcutAdapter
    private lateinit var shortcutsList: ListView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shortcuts)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Saved Events"

        shortcutStorage = ShortcutStorage(this)
        schedulerManager = SchedulerManager(this)
        shortcutsList = findViewById(R.id.shortcutsList)
        emptyText = findViewById(R.id.emptyText)

        setupShortcutsList()
        loadShortcuts()
        setupVersionFooter()
    }

    private fun setupVersionFooter() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val versionTextView = findViewById<TextView>(R.id.tvVersion)
        versionTextView.text = "Version $versionName"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupShortcutsList() {
        shortcutAdapter = ShortcutAdapter(
            context = this,
            shortcuts = emptyList(),
            onExecute = { shortcut ->
                executeShortcut(shortcut)
            },
            onEdit = { shortcut ->
                editShortcut(shortcut)
            },
            onDelete = { shortcut ->
                confirmDeleteShortcut(shortcut)
            }
        )
        shortcutsList.adapter = shortcutAdapter
    }

    private fun loadShortcuts() {
        val shortcuts = shortcutStorage.getAllShortcuts()
        shortcutAdapter.updateShortcuts(shortcuts)

        if (shortcuts.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            shortcutsList.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            shortcutsList.visibility = View.VISIBLE
        }
    }

    private fun executeShortcut(shortcut: ClickShortcut) {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        ClickAccessibilityService.targetPackage = shortcut.packageName
        ClickAccessibilityService.targetText = shortcut.targetText
        ClickAccessibilityService.clickX = shortcut.clickX
        ClickAccessibilityService.clickY = shortcut.clickY
        ClickAccessibilityService.useCoordinates = shortcut.useCoordinates
        ClickAccessibilityService.doubleClickEnabled = shortcut.doubleClickEnabled
        ClickAccessibilityService.doubleClickDelayMs = shortcut.doubleClickDelayMs
        ClickAccessibilityService.pendingAction = true

        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(shortcut.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                Toast.makeText(
                    this,
                    "Executing shortcut: ${shortcut.name}",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "App not found: ${shortcut.appName}",
                    Toast.LENGTH_SHORT
                ).show()
                ClickAccessibilityService.pendingAction = false
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Failed to launch app: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            ClickAccessibilityService.pendingAction = false
        }
    }

    private fun editShortcut(shortcut: ClickShortcut) {
        // Create a custom layout for the edit dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_shortcut, null)

        val nameInput = dialogView.findViewById<EditText>(R.id.etShortcutName)
        val targetTextInput = dialogView.findViewById<EditText>(R.id.etTargetText)
        val clickXInput = dialogView.findViewById<EditText>(R.id.etClickX)
        val clickYInput = dialogView.findViewById<EditText>(R.id.etClickY)
        val doubleClickCheckbox = dialogView.findViewById<CheckBox>(R.id.cbDoubleClick)
        val delayInput = dialogView.findViewById<EditText>(R.id.etDoubleClickDelay)
        val scheduleCheckbox = dialogView.findViewById<CheckBox>(R.id.cbEnableScheduling)
        val scheduleSpinner = dialogView.findViewById<Spinner>(R.id.spinnerScheduleInterval)

        // Pre-fill current values
        nameInput.setText(shortcut.name)

        if (shortcut.useCoordinates) {
            targetTextInput.isEnabled = false
            targetTextInput.hint = "Using coordinates mode"
            clickXInput.setText(shortcut.clickX.toString())
            clickYInput.setText(shortcut.clickY.toString())
        } else {
            targetTextInput.setText(shortcut.targetText)
            clickXInput.isEnabled = false
            clickYInput.isEnabled = false
            clickXInput.hint = "Using text mode"
            clickYInput.hint = "Using text mode"
        }

        doubleClickCheckbox.isChecked = shortcut.doubleClickEnabled
        delayInput.setText((shortcut.doubleClickDelayMs / 1000.0).toString())

        // Setup schedule interval spinner
        val scheduleIntervals = ScheduleInterval.values().filter { it != ScheduleInterval.NONE }
        val scheduleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            scheduleIntervals.map { it.displayName }
        )
        scheduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scheduleSpinner.adapter = scheduleAdapter

        // Set current schedule
        scheduleCheckbox.isChecked = shortcut.schedulingEnabled
        if (shortcut.scheduleInterval != ScheduleInterval.NONE) {
            val currentIndex = scheduleIntervals.indexOf(shortcut.scheduleInterval)
            if (currentIndex >= 0) {
                scheduleSpinner.setSelection(currentIndex)
            }
        }

        // Show/hide schedule spinner based on checkbox
        scheduleSpinner.visibility = if (scheduleCheckbox.isChecked) View.VISIBLE else View.GONE
        scheduleCheckbox.setOnCheckedChangeListener { _, isChecked ->
            scheduleSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Shortcut")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameInput.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val delayStr = delayInput.text.toString().trim()
                val delaySeconds = delayStr.toFloatOrNull() ?: 2f

                val schedulingEnabled = scheduleCheckbox.isChecked
                val scheduleInterval = if (schedulingEnabled) {
                    scheduleIntervals[scheduleSpinner.selectedItemPosition]
                } else {
                    ScheduleInterval.NONE
                }

                // Create updated shortcut
                val updatedShortcut = if (shortcut.useCoordinates) {
                    val xStr = clickXInput.text.toString().trim()
                    val yStr = clickYInput.text.toString().trim()
                    val x = xStr.toIntOrNull() ?: shortcut.clickX
                    val y = yStr.toIntOrNull() ?: shortcut.clickY

                    shortcut.copy(
                        name = newName,
                        clickX = x,
                        clickY = y,
                        doubleClickEnabled = doubleClickCheckbox.isChecked,
                        doubleClickDelayMs = (delaySeconds * 1000).toLong(),
                        schedulingEnabled = schedulingEnabled,
                        scheduleInterval = scheduleInterval
                    )
                } else {
                    val newTargetText = targetTextInput.text.toString().trim()
                    if (newTargetText.isEmpty()) {
                        Toast.makeText(this, "Please enter target text", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    shortcut.copy(
                        name = newName,
                        targetText = newTargetText,
                        doubleClickEnabled = doubleClickCheckbox.isChecked,
                        doubleClickDelayMs = (delaySeconds * 1000).toLong(),
                        schedulingEnabled = schedulingEnabled,
                        scheduleInterval = scheduleInterval
                    )
                }

                // Update in storage
                shortcutStorage.updateShortcut(updatedShortcut)

                // Handle scheduling changes
                if (shortcut.schedulingEnabled) {
                    schedulerManager.cancelSchedule(shortcut.id)
                }
                if (schedulingEnabled && scheduleInterval != ScheduleInterval.NONE) {
                    schedulerManager.scheduleShortcut(updatedShortcut)
                    Toast.makeText(this, "Shortcut updated and rescheduled: $newName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Shortcut updated: $newName", Toast.LENGTH_SHORT).show()
                }

                loadShortcuts()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteShortcut(shortcut: ClickShortcut) {
        AlertDialog.Builder(this)
            .setTitle("Delete Shortcut")
            .setMessage("Are you sure you want to delete \"${shortcut.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteShortcut(shortcut)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteShortcut(shortcut: ClickShortcut) {
        // Cancel any scheduled execution for this shortcut
        if (shortcut.schedulingEnabled) {
            schedulerManager.cancelSchedule(shortcut.id)
        }

        shortcutStorage.deleteShortcut(shortcut.id)
        loadShortcuts()
        Toast.makeText(
            this,
            "Deleted: ${shortcut.name}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
