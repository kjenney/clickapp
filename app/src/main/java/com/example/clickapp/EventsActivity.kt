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

class EventsActivity : AppCompatActivity() {

    private lateinit var shortcutStorage: ShortcutStorage
    private lateinit var schedulerManager: SchedulerManager
    private lateinit var shortcutAdapter: ShortcutAdapter
    private lateinit var groupAdapter: EventGroupAdapter
    private lateinit var shortcutsList: ListView
    private lateinit var groupsList: ListView
    private lateinit var emptyText: TextView
    private lateinit var groupsSection: LinearLayout
    private lateinit var eventsSection: LinearLayout
    private lateinit var sectionDivider: View
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Saved Events"

        shortcutStorage = ShortcutStorage(this)
        schedulerManager = SchedulerManager(this)

        shortcutsList = findViewById(R.id.shortcutsList)
        groupsList = findViewById(R.id.groupsList)
        emptyText = findViewById(R.id.emptyText)
        groupsSection = findViewById(R.id.groupsSection)
        eventsSection = findViewById(R.id.eventsSection)
        sectionDivider = findViewById(R.id.sectionDivider)

        setupGroupsList()
        setupShortcutsList()
        setupCreateGroupButton()
        setupVersionFooter()
    }

    override fun onResume() {
        super.onResume()
        loadData()
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
            storage = shortcutStorage,
            onExecute = { group -> executeGroup(group) },
            onDelete = { group -> confirmDeleteGroup(group) },
            onClick = { group -> openGroupDetail(group) }
        )
        groupsList.adapter = groupAdapter
    }

    private fun setupShortcutsList() {
        shortcutAdapter = ShortcutAdapter(
            context = this,
            shortcuts = emptyList(),
            onExecute = { shortcut -> executeShortcut(shortcut) },
            onEdit = { shortcut -> editShortcut(shortcut) },
            onDelete = { shortcut -> confirmDeleteShortcut(shortcut) }
        )
        shortcutsList.adapter = shortcutAdapter
    }

    private fun setupCreateGroupButton() {
        findViewById<Button>(R.id.btnCreateGroup).setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun loadData() {
        val groups = shortcutStorage.getAllGroups()
        val standaloneEvents = shortcutStorage.getStandaloneEvents()

        groupAdapter.updateGroups(groups)
        shortcutAdapter.updateShortcuts(standaloneEvents)

        // Update ListView heights for nested scrolling
        setListViewHeightBasedOnChildren(groupsList)
        setListViewHeightBasedOnChildren(shortcutsList)

        // Show/hide sections based on data
        val hasGroups = groups.isNotEmpty()
        val hasEvents = standaloneEvents.isNotEmpty()

        groupsSection.visibility = if (hasGroups || true) View.VISIBLE else View.GONE // Always show for "New Group" button
        eventsSection.visibility = if (hasEvents) View.VISIBLE else View.GONE
        sectionDivider.visibility = if (hasGroups && hasEvents) View.VISIBLE else View.GONE
        emptyText.visibility = if (!hasGroups && !hasEvents) View.VISIBLE else View.GONE

        // Always show groups section for the "New Group" button
        groupsSection.visibility = View.VISIBLE
        if (!hasGroups) {
            groupsList.visibility = View.GONE
        } else {
            groupsList.visibility = View.VISIBLE
        }
    }

    private fun setListViewHeightBasedOnChildren(listView: ListView) {
        val adapter = listView.adapter ?: return

        var totalHeight = 0
        for (i in 0 until adapter.count) {
            val listItem = adapter.getView(i, null, listView)
            listItem.measure(
                View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            totalHeight += listItem.measuredHeight
        }

        val params = listView.layoutParams
        params.height = totalHeight + (listView.dividerHeight * (adapter.count - 1))
        listView.layoutParams = params
        listView.requestLayout()
    }

    // ==================== Group Operations ====================

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
                shortcutStorage.saveGroup(group)
                loadData()
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

        val events = shortcutStorage.getEventsForGroup(group.id)
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
                executeShortcut(event)
            }, totalDelay)

            totalDelay += 1500 + event.delayAfterMs
            if (event.doubleClickEnabled) {
                totalDelay += event.doubleClickDelayMs
            }
        }
    }

    private fun confirmDeleteGroup(group: EventGroup) {
        val eventCount = shortcutStorage.getEventsForGroup(group.id).size

        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete \"${group.name}\"?\n\nThis group has $eventCount event(s). The events will be kept but removed from this group.")
            .setPositiveButton("Delete") { _, _ ->
                if (group.schedulingEnabled) {
                    schedulerManager.cancelGroupSchedule(group.id)
                }
                shortcutStorage.deleteGroup(group.id, deleteEvents = false)
                loadData()
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

    // ==================== Shortcut/Event Operations ====================

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
                Toast.makeText(this, "Executing: ${shortcut.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "App not found: ${shortcut.appName}", Toast.LENGTH_SHORT).show()
                ClickAccessibilityService.pendingAction = false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch app: ${e.message}", Toast.LENGTH_SHORT).show()
            ClickAccessibilityService.pendingAction = false
        }
    }

    private fun editShortcut(shortcut: ClickShortcut) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_shortcut, null)

        val nameInput = dialogView.findViewById<EditText>(R.id.etShortcutName)
        val targetTextInput = dialogView.findViewById<EditText>(R.id.etTargetText)
        val clickXInput = dialogView.findViewById<EditText>(R.id.etClickX)
        val clickYInput = dialogView.findViewById<EditText>(R.id.etClickY)
        val doubleClickCheckbox = dialogView.findViewById<CheckBox>(R.id.cbDoubleClick)
        val delayInput = dialogView.findViewById<EditText>(R.id.etDoubleClickDelay)
        val scheduleCheckbox = dialogView.findViewById<CheckBox>(R.id.cbEnableScheduling)
        val scheduleSpinner = dialogView.findViewById<Spinner>(R.id.spinnerScheduleInterval)

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

        val scheduleIntervals = ScheduleInterval.values().filter { it != ScheduleInterval.NONE }
        val scheduleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            scheduleIntervals.map { it.displayName }
        )
        scheduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scheduleSpinner.adapter = scheduleAdapter

        scheduleCheckbox.isChecked = shortcut.schedulingEnabled
        if (shortcut.scheduleInterval != ScheduleInterval.NONE) {
            val currentIndex = scheduleIntervals.indexOf(shortcut.scheduleInterval)
            if (currentIndex >= 0) {
                scheduleSpinner.setSelection(currentIndex)
            }
        }

        scheduleSpinner.visibility = if (scheduleCheckbox.isChecked) View.VISIBLE else View.GONE
        scheduleCheckbox.setOnCheckedChangeListener { _, isChecked ->
            scheduleSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Event")
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

                shortcutStorage.updateShortcut(updatedShortcut)

                if (shortcut.schedulingEnabled) {
                    schedulerManager.cancelSchedule(shortcut.id)
                }
                if (schedulingEnabled && scheduleInterval != ScheduleInterval.NONE) {
                    schedulerManager.scheduleShortcut(updatedShortcut)
                    Toast.makeText(this, "Event updated and rescheduled: $newName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Event updated: $newName", Toast.LENGTH_SHORT).show()
                }

                loadData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteShortcut(shortcut: ClickShortcut) {
        AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete \"${shortcut.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteShortcut(shortcut)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteShortcut(shortcut: ClickShortcut) {
        if (shortcut.schedulingEnabled) {
            schedulerManager.cancelSchedule(shortcut.id)
        }

        shortcutStorage.deleteShortcut(shortcut.id)
        loadData()
        Toast.makeText(this, "Deleted: ${shortcut.name}", Toast.LENGTH_SHORT).show()
    }
}
