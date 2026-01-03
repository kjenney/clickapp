package com.example.clickapp

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ShortcutStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "click_shortcuts"
        private const val KEY_SHORTCUTS = "shortcuts"
    }

    fun saveShortcut(shortcut: ClickShortcut) {
        val shortcuts = getAllShortcuts().toMutableList()
        shortcuts.add(shortcut)
        saveAll(shortcuts)
    }

    fun updateShortcut(shortcut: ClickShortcut) {
        val shortcuts = getAllShortcuts().toMutableList()
        val index = shortcuts.indexOfFirst { it.id == shortcut.id }
        if (index != -1) {
            shortcuts[index] = shortcut
            saveAll(shortcuts)
        }
    }

    fun deleteShortcut(id: String) {
        val shortcuts = getAllShortcuts().toMutableList()
        shortcuts.removeAll { it.id == id }
        saveAll(shortcuts)
    }

    fun getAllShortcuts(): List<ClickShortcut> {
        val json = prefs.getString(KEY_SHORTCUTS, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(json)
            val shortcuts = mutableListOf<ClickShortcut>()

            for (i in 0 until jsonArray.length()) {
                val shortcutJson = jsonArray.getJSONObject(i)
                shortcuts.add(ClickShortcut.fromJson(shortcutJson))
            }

            shortcuts
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getShortcut(id: String): ClickShortcut? {
        return getAllShortcuts().firstOrNull { it.id == id }
    }

    private fun saveAll(shortcuts: List<ClickShortcut>) {
        val jsonArray = JSONArray()
        shortcuts.forEach { shortcut ->
            jsonArray.put(shortcut.toJson())
        }

        prefs.edit()
            .putString(KEY_SHORTCUTS, jsonArray.toString())
            .apply()
    }
}
