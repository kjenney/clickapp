package com.example.clickapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView

class EventGroupAdapter(
    private val context: Context,
    private var groups: List<EventGroup>,
    private val storage: ShortcutStorage,
    private val onExecute: (EventGroup) -> Unit,
    private val onDelete: (EventGroup) -> Unit,
    private val onClick: (EventGroup) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = groups.size

    override fun getItem(position: Int): EventGroup = groups[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_event_group, parent, false)

        val group = getItem(position)

        val nameText = view.findViewById<TextView>(R.id.tvGroupName)
        val eventCountText = view.findViewById<TextView>(R.id.tvEventCount)
        val scheduleStatus = view.findViewById<TextView>(R.id.tvScheduleStatus)
        val executeButton = view.findViewById<Button>(R.id.btnExecute)
        val deleteButton = view.findViewById<Button>(R.id.btnDelete)

        nameText.text = group.name

        val eventCount = storage.getEventsForGroup(group.id).size
        eventCountText.text = "$eventCount event${if (eventCount != 1) "s" else ""}"

        if (group.schedulingEnabled && group.scheduleInterval != ScheduleInterval.NONE) {
            scheduleStatus.text = "Scheduled: ${group.scheduleInterval.displayName}"
        } else {
            scheduleStatus.text = "Not scheduled"
        }

        executeButton.setOnClickListener {
            onExecute(group)
        }

        deleteButton.setOnClickListener {
            onDelete(group)
        }

        view.setOnClickListener {
            onClick(group)
        }

        return view
    }

    fun updateGroups(newGroups: List<EventGroup>) {
        groups = newGroups
        notifyDataSetChanged()
    }
}
