package com.example.clickapp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class ClickShortcut(
    val id: String,
    val name: String,
    val appName: String,
    val packageName: String,
    val useCoordinates: Boolean,
    val targetText: String = "",
    val clickX: Int = -1,
    val clickY: Int = -1,
    val doubleClickEnabled: Boolean = false,
    val doubleClickDelayMs: Long = 2000,
    val schedulingEnabled: Boolean = false,
    val scheduleInterval: ScheduleInterval = ScheduleInterval.NONE,
    val groupId: String? = null,
    val orderInGroup: Int = 0,
    val delayAfterMs: Long = 0,
    // Anchor-based positioning for dynamic canvases
    val useAnchor: Boolean = false,
    val anchorText: String = "",
    val anchorContentDescription: String = "",
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    // Scroll before click
    val scrollDirection: ScrollDirection = ScrollDirection.NONE
) : Parcelable {

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("appName", appName)
            put("packageName", packageName)
            put("useCoordinates", useCoordinates)
            put("targetText", targetText)
            put("clickX", clickX)
            put("clickY", clickY)
            put("doubleClickEnabled", doubleClickEnabled)
            put("doubleClickDelayMs", doubleClickDelayMs)
            put("schedulingEnabled", schedulingEnabled)
            put("scheduleInterval", scheduleInterval.name)
            put("groupId", groupId ?: JSONObject.NULL)
            put("orderInGroup", orderInGroup)
            put("delayAfterMs", delayAfterMs)
            put("useAnchor", useAnchor)
            put("anchorText", anchorText)
            put("anchorContentDescription", anchorContentDescription)
            put("offsetX", offsetX)
            put("offsetY", offsetY)
            put("scrollDirection", scrollDirection.name)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ClickShortcut {
            return ClickShortcut(
                id = json.getString("id"),
                name = json.getString("name"),
                appName = json.getString("appName"),
                packageName = json.getString("packageName"),
                useCoordinates = json.getBoolean("useCoordinates"),
                targetText = json.optString("targetText", ""),
                clickX = json.optInt("clickX", -1),
                clickY = json.optInt("clickY", -1),
                doubleClickEnabled = json.optBoolean("doubleClickEnabled", false),
                doubleClickDelayMs = json.optLong("doubleClickDelayMs", 2000),
                schedulingEnabled = json.optBoolean("schedulingEnabled", false),
                scheduleInterval = ScheduleInterval.fromString(json.optString("scheduleInterval", "NONE")),
                groupId = json.optString("groupId", null).takeIf { it != "null" && it.isNotEmpty() },
                orderInGroup = json.optInt("orderInGroup", 0),
                delayAfterMs = json.optLong("delayAfterMs", 0),
                useAnchor = json.optBoolean("useAnchor", false),
                anchorText = json.optString("anchorText", ""),
                anchorContentDescription = json.optString("anchorContentDescription", ""),
                offsetX = json.optInt("offsetX", 0),
                offsetY = json.optInt("offsetY", 0),
                scrollDirection = ScrollDirection.fromString(json.optString("scrollDirection", "NONE"))
            )
        }
    }

    fun getDescription(): String {
        val scrollPrefix = if (scrollDirection != ScrollDirection.NONE) {
            "[${scrollDirection.displayName}] "
        } else {
            ""
        }

        val clickDescription = when {
            useAnchor -> {
                val anchor = anchorText.ifEmpty { anchorContentDescription }
                "${scrollPrefix}Tap offset ($offsetX, $offsetY) from \"$anchor\" in $appName"
            }
            useCoordinates -> "${scrollPrefix}Tap at ($clickX, $clickY) in $appName"
            else -> "${scrollPrefix}Tap \"$targetText\" in $appName"
        }

        val scheduleDescription = if (schedulingEnabled && scheduleInterval != ScheduleInterval.NONE) {
            " • ${scheduleInterval.displayName}"
        } else {
            ""
        }

        return clickDescription + scheduleDescription
    }
}
