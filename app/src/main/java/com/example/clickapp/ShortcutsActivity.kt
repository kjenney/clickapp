package com.example.clickapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
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
        supportActionBar?.title = "Click Shortcuts"

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
