package com.example.clickapp

enum class ScheduleInterval(val displayName: String, val intervalMinutes: Long) {
    NONE("No scheduling", 0),
    EVERY_MINUTE("Every minute", 1),
    EVERY_5_MINUTES("Every 5 minutes", 5),
    EVERY_15_MINUTES("Every 15 minutes", 15),
    EVERY_30_MINUTES("Every 30 minutes", 30),
    EVERY_HOUR("Every hour", 60),
    EVERY_6_HOURS("Every 6 hours", 360),
    EVERY_12_HOURS("Every 12 hours", 720),
    EVERY_DAY("Every day", 1440);

    companion object {
        fun fromString(value: String): ScheduleInterval {
            return values().find { it.name == value } ?: NONE
        }
    }
}
