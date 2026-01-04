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
        private const val KEY_GROUPS = "event_groups"
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

    // ==================== Event Group Methods ====================

    fun saveGroup(group: EventGroup) {
        val groups = getAllGroups().toMutableList()
        groups.add(group)
        saveAllGroups(groups)
    }

    fun updateGroup(group: EventGroup) {
        val groups = getAllGroups().toMutableList()
        val index = groups.indexOfFirst { it.id == group.id }
        if (index != -1) {
            groups[index] = group
            saveAllGroups(groups)
        }
    }

    fun deleteGroup(id: String, deleteEvents: Boolean = false) {
        val groups = getAllGroups().toMutableList()
        groups.removeAll { it.id == id }
        saveAllGroups(groups)

        if (deleteEvents) {
            val shortcuts = getAllShortcuts().toMutableList()
            shortcuts.removeAll { it.groupId == id }
            saveAll(shortcuts)
        } else {
            // Detach events from group
            val shortcuts = getAllShortcuts().map { shortcut ->
                if (shortcut.groupId == id) {
                    shortcut.copy(groupId = null, orderInGroup = 0)
                } else shortcut
            }
            saveAll(shortcuts)
        }
    }

    fun getAllGroups(): List<EventGroup> {
        val json = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            val groups = mutableListOf<EventGroup>()
            for (i in 0 until jsonArray.length()) {
                groups.add(EventGroup.fromJson(jsonArray.getJSONObject(i)))
            }
            groups
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getGroup(id: String): EventGroup? {
        return getAllGroups().firstOrNull { it.id == id }
    }

    fun getEventsForGroup(groupId: String): List<ClickShortcut> {
        return getAllShortcuts()
            .filter { it.groupId == groupId }
            .sortedBy { it.orderInGroup }
    }

    fun getStandaloneEvents(): List<ClickShortcut> {
        return getAllShortcuts().filter { it.groupId == null }
    }

    fun addEventToGroup(eventId: String, groupId: String) {
        val existingEvents = getEventsForGroup(groupId)
        val nextOrder = (existingEvents.maxOfOrNull { it.orderInGroup } ?: -1) + 1

        val shortcut = getShortcut(eventId) ?: return
        val updated = shortcut.copy(groupId = groupId, orderInGroup = nextOrder)
        updateShortcut(updated)
    }

    fun removeEventFromGroup(eventId: String) {
        val shortcut = getShortcut(eventId) ?: return
        val updated = shortcut.copy(groupId = null, orderInGroup = 0)
        updateShortcut(updated)
    }

    fun reorderEventsInGroup(groupId: String, orderedEventIds: List<String>) {
        val shortcuts = getAllShortcuts().toMutableList()
        orderedEventIds.forEachIndexed { index, eventId ->
            val shortcutIndex = shortcuts.indexOfFirst { it.id == eventId }
            if (shortcutIndex != -1) {
                shortcuts[shortcutIndex] = shortcuts[shortcutIndex].copy(orderInGroup = index)
            }
        }
        saveAll(shortcuts)
    }

    private fun saveAllGroups(groups: List<EventGroup>) {
        val jsonArray = JSONArray()
        groups.forEach { group -> jsonArray.put(group.toJson()) }
        prefs.edit().putString(KEY_GROUPS, jsonArray.toString()).apply()
    }
}
