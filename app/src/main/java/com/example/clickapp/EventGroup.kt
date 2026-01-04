package com.example.clickapp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class EventGroup(
    val id: String,
    val name: String,
    val schedulingEnabled: Boolean = false,
    val scheduleInterval: ScheduleInterval = ScheduleInterval.NONE,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("schedulingEnabled", schedulingEnabled)
            put("scheduleInterval", scheduleInterval.name)
            put("createdAt", createdAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): EventGroup {
            return EventGroup(
                id = json.getString("id"),
                name = json.getString("name"),
                schedulingEnabled = json.optBoolean("schedulingEnabled", false),
                scheduleInterval = ScheduleInterval.fromString(
                    json.optString("scheduleInterval", "NONE")
                ),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
