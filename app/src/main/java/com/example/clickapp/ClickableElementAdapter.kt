package com.example.clickapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class ClickableElementAdapter(
    context: Context,
    private val elements: MutableList<ClickableElement>,
    private val onElementClick: (ClickableElement) -> Unit
) : ArrayAdapter<ClickableElement>(context, R.layout.list_item_element, elements) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_element, parent, false)

        val element = elements[position]

        val tvElementText = view.findViewById<TextView>(R.id.tvElementText)
        val tvElementInfo = view.findViewById<TextView>(R.id.tvElementInfo)

        tvElementText.text = element.text
        tvElementInfo.text = "${element.className.substringAfterLast(".")} @ (${element.x}, ${element.y})"

        view.setOnClickListener {
            onElementClick(element)
        }

        return view
    }

    fun updateElements(newElements: List<ClickableElement>) {
        elements.clear()
        elements.addAll(newElements)
        notifyDataSetChanged()
    }
}
