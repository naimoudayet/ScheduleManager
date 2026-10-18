package com.example.schedulemanager.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/** Whether times are shown in 24-hour format; provided from the user's setting in MainActivity. */
val LocalUse24Hour = staticCompositionLocalOf { true }
