package com.example.clickapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView

class GroupEventAdapter(
    private val context: Context,
    private var events: List<ClickShortcut>,
    private val onConfigureDelay: (ClickShortcut) -> Unit,
    private val onRemove: (ClickShortcut) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = events.size

    override fun getItem(position: Int): ClickShortcut = events[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_group_event, parent, false)

        val event = getItem(position)

        val orderText = view.findViewById<TextView>(R.id.tvOrder)
        val nameText = view.findViewById<TextView>(R.id.tvEventName)
        val descriptionText = view.findViewById<TextView>(R.id.tvEventDescription)
        val delayText = view.findViewById<TextView>(R.id.tvDelayAfter)
        val delayButton = view.findViewById<Button>(R.id.btnDelay)
        val removeButton = view.findViewById<Button>(R.id.btnRemove)

        orderText.text = "${position + 1}."
        nameText.text = event.name
        descriptionText.text = event.getDescription()

        if (event.delayAfterMs > 0) {
            delayText.text = "Wait ${event.delayAfterMs}ms before next"
        } else {
            delayText.text = "No delay before next"
        }

        delayButton.setOnClickListener {
            onConfigureDelay(event)
        }

        removeButton.setOnClickListener {
            onRemove(event)
        }

        return view
    }

    fun updateEvents(newEvents: List<ClickShortcut>) {
        events = newEvents
        notifyDataSetChanged()
    }

    fun getEvents(): List<ClickShortcut> = events
}
