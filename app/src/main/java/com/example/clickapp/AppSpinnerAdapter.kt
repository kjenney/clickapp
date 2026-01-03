package com.example.clickapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class AppSpinnerAdapter(
    context: Context,
    private val apps: List<AppInfo>
) : ArrayAdapter<AppInfo>(context, R.layout.spinner_app_item, apps) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.spinner_app_item, parent, false)

        val app = apps[position]

        val appIcon = view.findViewById<ImageView>(R.id.appIcon)
        val appName = view.findViewById<TextView>(R.id.appName)
        val packageName = view.findViewById<TextView>(R.id.packageName)

        appIcon.setImageDrawable(app.icon)
        appName.text = app.appName
        packageName.text = app.packageName

        return view
    }
}
