package com.example.clickapp

enum class ScrollDirection(val displayName: String) {
    NONE("No scroll"),
    TOP("Scroll to top"),
    BOTTOM("Scroll to bottom");

    companion object {
        fun fromString(value: String): ScrollDirection {
            return values().find { it.name == value } ?: NONE
        }
    }
}
