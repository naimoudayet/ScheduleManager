package com.example.schedulemanager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.ThemeMode
import com.example.schedulemanager.ui.manage.ManageItemsScreen
import com.example.schedulemanager.ui.schedule.ScheduleScreen
import com.example.schedulemanager.ui.settings.SettingsScreen
import com.example.schedulemanager.ui.theme.ScheduleManagerTheme

private object Routes {
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val MANAGE = "manage/{kind}"

    fun manage(kind: ItemKind) = "manage/${kind.name}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as ScheduleApp).settingsRepository

        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep the status / navigation bar icons readable when the app theme differs from the system's
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose {}
            }

            ScheduleManagerTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.SCHEDULE) {
                    composable(Routes.SCHEDULE) {
                        ScheduleScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onManage = { kind -> navController.navigate(Routes.manage(kind)) },
                        )
                    }
                    composable(Routes.MANAGE) { entry ->
                        val kind = ItemKind.valueOf(entry.arguments?.getString("kind") ?: ItemKind.CLASSROOM.name)
                        ManageItemsScreen(kind = kind, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

// Same scrims enableEdgeToEdge() uses by default for 3-button navigation
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
