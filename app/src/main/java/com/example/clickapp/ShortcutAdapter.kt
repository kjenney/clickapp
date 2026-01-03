package com.example.clickapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView

class ShortcutAdapter(
    private val context: Context,
    private var shortcuts: List<ClickShortcut>,
    private val onExecute: (ClickShortcut) -> Unit,
    private val onDelete: (ClickShortcut) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = shortcuts.size

    override fun getItem(position: Int): ClickShortcut = shortcuts[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_shortcut, parent, false)

        val shortcut = getItem(position)

        val nameText = view.findViewById<TextView>(R.id.shortcutName)
        val descriptionText = view.findViewById<TextView>(R.id.shortcutDescription)
        val detailsText = view.findViewById<TextView>(R.id.shortcutDetails)
        val scheduleStatus = view.findViewById<TextView>(R.id.scheduleStatus)
        val executeButton = view.findViewById<Button>(R.id.executeButton)
        val deleteButton = view.findViewById<Button>(R.id.deleteButton)

        nameText.text = shortcut.name
        descriptionText.text = shortcut.getDescription()

        val details = buildString {
            if (shortcut.doubleClickEnabled) {
                append("Double click (${shortcut.doubleClickDelayMs}ms)")
            } else {
                append("Single click")
            }
        }
        detailsText.text = details

        // Show scheduling status
        if (shortcut.schedulingEnabled && shortcut.scheduleInterval != ScheduleInterval.NONE) {
            scheduleStatus.visibility = View.VISIBLE
            scheduleStatus.text = "⏰ Scheduled: ${shortcut.scheduleInterval.displayName}"
        } else {
            scheduleStatus.visibility = View.GONE
        }

        executeButton.setOnClickListener {
            onExecute(shortcut)
        }

        deleteButton.setOnClickListener {
            onDelete(shortcut)
        }

        return view
    }

    fun updateShortcuts(newShortcuts: List<ClickShortcut>) {
        shortcuts = newShortcuts
        notifyDataSetChanged()
    }
}
