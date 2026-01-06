package com.example.clickapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class GroupDetailActivity : AppCompatActivity() {

    private lateinit var storage: ShortcutStorage
    private lateinit var schedulerManager: SchedulerManager
    private lateinit var eventAdapter: GroupEventAdapter
    private lateinit var eventsList: ListView
    private lateinit var emptyText: TextView
    private lateinit var tvGroupName: TextView
    private lateinit var tvScheduleStatus: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var groupId: String = ""
    private var group: EventGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_detail)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        groupId = intent.getStringExtra("group_id") ?: run {
            finish()
            return
        }

        storage = ShortcutStorage(this)
        schedulerManager = SchedulerManager(this)

        tvGroupName = findViewById(R.id.tvGroupName)
        tvScheduleStatus = findViewById(R.id.tvScheduleStatus)
        eventsList = findViewById(R.id.eventsList)
        emptyText = findViewById(R.id.emptyText)

        setupEventsList()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        loadGroup()
        loadEvents()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadGroup() {
        group = storage.getGroup(groupId)
        group?.let { g ->
            tvGroupName.text = g.name
            supportActionBar?.title = g.name

            if (g.schedulingEnabled && g.scheduleInterval != ScheduleInterval.NONE) {
                tvScheduleStatus.text = "Scheduled: ${g.scheduleInterval.displayName}"
            } else {
                tvScheduleStatus.text = "Not scheduled"
            }
        } ?: run {
            finish()
        }
    }

    private fun setupEventsList() {
        eventAdapter = GroupEventAdapter(
            context = this,
            events = emptyList(),
            onConfigureDelay = { event -> showDelayDialog(event) },
            onCopy = { event -> showCopyEventDialog(event) },
            onRemove = { event -> confirmRemoveEvent(event) }
        )
        eventsList.adapter = eventAdapter
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnAddEvent).setOnClickListener {
            showAddEventDialog()
        }

        findViewById<Button>(R.id.btnConfigureSchedule).setOnClickListener {
            showScheduleDialog()
        }

        findViewById<Button>(R.id.btnExecuteGroup).setOnClickListener {
            executeGroup()
        }
    }

    private fun loadEvents() {
        val events = storage.getEventsForGroup(groupId)
        eventAdapter.updateEvents(events)

        if (events.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            eventsList.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            eventsList.visibility = View.VISIBLE
        }
    }

    private fun showAddEventDialog() {
        val standaloneEvents = storage.getStandaloneEvents()

        if (standaloneEvents.isEmpty()) {
            Toast.makeText(this, "No standalone events available. Create events first from the main screen.", Toast.LENGTH_LONG).show()
            return
        }

        val eventNames = standaloneEvents.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Add Event to Group")
            .setItems(eventNames) { _, which ->
                val selectedEvent = standaloneEvents[which]
                storage.addEventToGroup(selectedEvent.id, groupId)
                loadEvents()
                Toast.makeText(this, "Added: ${selectedEvent.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDelayDialog(event: ClickShortcut) {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Delay in milliseconds"
        input.setText(event.delayAfterMs.toString())

        AlertDialog.Builder(this)
            .setTitle("Delay Before Next Event")
            .setMessage("How long to wait after \"${event.name}\" before executing the next event?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val delayMs = input.text.toString().toLongOrNull() ?: 0
                val updated = event.copy(delayAfterMs = delayMs)
                storage.updateShortcut(updated)
                loadEvents()
                Toast.makeText(this, "Delay set to ${delayMs}ms", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRemoveEvent(event: ClickShortcut) {
        AlertDialog.Builder(this)
            .setTitle("Remove Event")
            .setMessage("Remove \"${event.name}\" from this group? The event will still exist but won't be in this group.")
            .setPositiveButton("Remove") { _, _ ->
                storage.removeEventFromGroup(event.id)
                loadEvents()
                Toast.makeText(this, "Removed: ${event.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showScheduleDialog() {
        val currentGroup = group ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_group_schedule, null)

        val scheduleCheckbox = dialogView.findViewById<CheckBox>(R.id.cbEnableScheduling)
        val scheduleSpinner = dialogView.findViewById<Spinner>(R.id.spinnerScheduleInterval)

        // Setup schedule interval spinner
        val scheduleIntervals = ScheduleInterval.values().filter { it != ScheduleInterval.NONE }
        val scheduleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            scheduleIntervals.map { it.displayName }
        )
        scheduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scheduleSpinner.adapter = scheduleAdapter

        // Set current values
        scheduleCheckbox.isChecked = currentGroup.schedulingEnabled
        if (currentGroup.scheduleInterval != ScheduleInterval.NONE) {
            val currentIndex = scheduleIntervals.indexOf(currentGroup.scheduleInterval)
            if (currentIndex >= 0) {
                scheduleSpinner.setSelection(currentIndex)
            }
        }

        scheduleSpinner.visibility = if (scheduleCheckbox.isChecked) View.VISIBLE else View.GONE
        scheduleCheckbox.setOnCheckedChangeListener { _, isChecked ->
            scheduleSpinner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Schedule Group: ${currentGroup.name}")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val schedulingEnabled = scheduleCheckbox.isChecked
                val scheduleInterval = if (schedulingEnabled) {
                    scheduleIntervals[scheduleSpinner.selectedItemPosition]
                } else {
                    ScheduleInterval.NONE
                }

                val updatedGroup = currentGroup.copy(
                    schedulingEnabled = schedulingEnabled,
                    scheduleInterval = scheduleInterval
                )

                storage.updateGroup(updatedGroup)

                // Handle scheduling changes
                if (currentGroup.schedulingEnabled) {
                    schedulerManager.cancelGroupSchedule(currentGroup.id)
                }
                if (schedulingEnabled && scheduleInterval != ScheduleInterval.NONE) {
                    schedulerManager.scheduleGroup(updatedGroup)
                    Toast.makeText(this, "Group scheduled: ${scheduleInterval.displayName}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Schedule updated", Toast.LENGTH_SHORT).show()
                }

                loadGroup()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeGroup() {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val events = storage.getEventsForGroup(groupId)
        if (events.isEmpty()) {
            Toast.makeText(this, "No events in this group", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Executing ${events.size} events...", Toast.LENGTH_SHORT).show()
        executeEventsSequentially(events)
    }

    private fun executeEventsSequentially(events: List<ClickShortcut>) {
        var totalDelay = 0L

        events.forEachIndexed { index, event ->
            handler.postDelayed({
                executeEvent(event)
            }, totalDelay)

            totalDelay += 1500 + event.delayAfterMs

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
        // Anchor mode settings
        ClickAccessibilityService.useAnchor = event.useAnchor
        ClickAccessibilityService.anchorText = event.anchorText
        ClickAccessibilityService.anchorContentDescription = event.anchorContentDescription
        ClickAccessibilityService.offsetX = event.offsetX
        ClickAccessibilityService.offsetY = event.offsetY
        ClickAccessibilityService.scrollDirection = event.scrollDirection
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

    private fun showCopyEventDialog(event: ClickShortcut) {
        val groups = storage.getAllGroups()
        val options = mutableListOf("Copy as Standalone Event")
        options.addAll(groups.map { "Copy to Group: ${it.name}" })

        AlertDialog.Builder(this)
            .setTitle("Copy Event: ${event.name}")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    copyEventAsStandalone(event)
                } else {
                    val selectedGroup = groups[which - 1]
                    copyEventToGroup(event, selectedGroup)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyEventAsStandalone(event: ClickShortcut) {
        val copiedEvent = event.copy(
            id = UUID.randomUUID().toString(),
            name = "${event.name} (Copy)",
            groupId = null,
            orderInGroup = 0,
            schedulingEnabled = false,
            scheduleInterval = ScheduleInterval.NONE
        )
        storage.saveShortcut(copiedEvent)
        Toast.makeText(this, "Copied as standalone: ${copiedEvent.name}", Toast.LENGTH_SHORT).show()
    }

    private fun copyEventToGroup(event: ClickShortcut, targetGroup: EventGroup) {
        val eventsInGroup = storage.getEventsForGroup(targetGroup.id)
        val copiedEvent = event.copy(
            id = UUID.randomUUID().toString(),
            name = "${event.name} (Copy)",
            groupId = targetGroup.id,
            orderInGroup = eventsInGroup.size,
            schedulingEnabled = false,
            scheduleInterval = ScheduleInterval.NONE
        )
        storage.saveShortcut(copiedEvent)

        // Reload if copied to current group
        if (targetGroup.id == groupId) {
            loadEvents()
        }
        Toast.makeText(this, "Copied to group: ${targetGroup.name}", Toast.LENGTH_SHORT).show()
    }
}
