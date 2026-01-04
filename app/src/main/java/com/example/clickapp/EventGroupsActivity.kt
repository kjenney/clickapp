package com.example.clickapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class EventGroupsActivity : AppCompatActivity() {

    private lateinit var storage: ShortcutStorage
    private lateinit var schedulerManager: SchedulerManager
    private lateinit var groupAdapter: EventGroupAdapter
    private lateinit var groupsList: ListView
    private lateinit var emptyText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_groups)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Event Groups"

        storage = ShortcutStorage(this)
        schedulerManager = SchedulerManager(this)
        groupsList = findViewById(R.id.groupsList)
        emptyText = findViewById(R.id.emptyText)

        setupGroupsList()
        setupCreateButton()
        setupVersionFooter()
    }

    override fun onResume() {
        super.onResume()
        loadGroups()
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

    private fun setupGroupsList() {
        groupAdapter = EventGroupAdapter(
            context = this,
            groups = emptyList(),
            storage = storage,
            onExecute = { group -> executeGroup(group) },
            onDelete = { group -> confirmDeleteGroup(group) },
            onClick = { group -> openGroupDetail(group) }
        )
        groupsList.adapter = groupAdapter
    }

    private fun setupCreateButton() {
        findViewById<Button>(R.id.btnCreateGroup).setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun loadGroups() {
        val groups = storage.getAllGroups()
        groupAdapter.updateGroups(groups)

        if (groups.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            groupsList.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            groupsList.visibility = View.VISIBLE
        }
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this)
        input.hint = "Group name"

        AlertDialog.Builder(this)
            .setTitle("Create Event Group")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val group = EventGroup(
                    id = UUID.randomUUID().toString(),
                    name = name
                )
                storage.saveGroup(group)
                loadGroups()
                Toast.makeText(this, "Group created: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeGroup(group: EventGroup) {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val events = storage.getEventsForGroup(group.id)
        if (events.isEmpty()) {
            Toast.makeText(this, "No events in this group", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Executing group: ${group.name} (${events.size} events)", Toast.LENGTH_SHORT).show()
        executeEventsSequentially(events)
    }

    private fun executeEventsSequentially(events: List<ClickShortcut>) {
        var totalDelay = 0L

        events.forEachIndexed { index, event ->
            handler.postDelayed({
                executeEvent(event)
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
        ClickAccessibilityService.targetPackage = event.packageName
        ClickAccessibilityService.targetText = event.targetText
        ClickAccessibilityService.clickX = event.clickX
        ClickAccessibilityService.clickY = event.clickY
        ClickAccessibilityService.useCoordinates = event.useCoordinates
        ClickAccessibilityService.doubleClickEnabled = event.doubleClickEnabled
        ClickAccessibilityService.doubleClickDelayMs = event.doubleClickDelayMs
        ClickAccessibilityService.pendingAction = true

        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(event.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            ClickAccessibilityService.pendingAction = false
        }
    }

    private fun confirmDeleteGroup(group: EventGroup) {
        val eventCount = storage.getEventsForGroup(group.id).size

        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete \"${group.name}\"?\n\nThis group has $eventCount event(s). The events will be kept but removed from this group.")
            .setPositiveButton("Delete") { _, _ ->
                if (group.schedulingEnabled) {
                    schedulerManager.cancelGroupSchedule(group.id)
                }
                storage.deleteGroup(group.id, deleteEvents = false)
                loadGroups()
                Toast.makeText(this, "Deleted: ${group.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openGroupDetail(group: EventGroup) {
        val intent = Intent(this, GroupDetailActivity::class.java)
        intent.putExtra("group_id", group.id)
        startActivity(intent)
    }
}
