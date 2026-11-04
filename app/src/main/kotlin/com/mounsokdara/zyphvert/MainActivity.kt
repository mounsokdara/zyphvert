package com.mounsokdara.zyphvert

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mounsokdara.zyphvert.data.ThemeMode
import com.mounsokdara.zyphvert.ui.LocalHaptics
import com.mounsokdara.zyphvert.ui.ZyphvertShell
import com.mounsokdara.zyphvert.ui.settings.SettingsViewModel
import com.mounsokdara.zyphvert.ui.theme.ZyphvertTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsVm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by settingsVm.settings.collectAsState()
            val dark = when (s.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark, ThemeMode.Amoled -> true
            }
            // Keep status/navigation bar icons readable when the app theme differs from the system theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                    ) { dark },
                )
                onDispose { }
            }
            ZyphvertTheme(darkTheme = dark, dynamicColor = s.dynamicColor, amoled = s.themeMode == ThemeMode.Amoled) {
                CompositionLocalProvider(LocalHaptics provides s.haptics) {
                    ZyphvertShell(settingsVm = settingsVm, hapticsEnabled = s.haptics)
                }
            }
        }
    }
}
