package com.example.schedulemanager.model

/** Appearance: light / dark / follow the system. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The app's three visual themes. Each has a light and a dark version. */
enum class AppStyle(val label: String, val description: String) {
    TIMELINE("Timeline", "Your day on a time ruler, with a live now line"),
    TODAY("Today first", "What's on now and what's next, up front"),
    NIGHT("Night bold", "A big clock and bold, high-contrast cards"),
}
