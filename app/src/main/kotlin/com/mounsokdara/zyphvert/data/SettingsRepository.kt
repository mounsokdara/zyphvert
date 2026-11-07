package com.mounsokdara.zyphvert.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) {
    System("System default"),
    Light("Light"),
    Dark("Dark"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val haptics: Boolean = true,
    /** The picked "Material You color", used when dynamic color is off. */
    val seedColor: Int = 0xFF00A86B.toInt(),
    /** Pure black backgrounds while the dark theme is on. */
    val extraDark: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val haptics = booleanPreferencesKey("haptics")
        val seed = androidx.datastore.preferences.core.intPreferencesKey("seed_color")
        val extraDark = booleanPreferencesKey("extra_dark")
        val saved = stringSetPreferencesKey("saved_commands")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = when (val saved = p[Keys.theme]) {
                "Amoled" -> ThemeMode.Dark
                else -> runCatching { ThemeMode.valueOf(saved ?: "") }.getOrDefault(ThemeMode.System)
            },
            dynamicColor = p[Keys.dynamic] ?: true,
            haptics = p[Keys.haptics] ?: true,
            seedColor = p[Keys.seed] ?: 0xFF00A86B.toInt(),
            // The old "AMOLED black" theme became Dark plus this switch.
            extraDark = p[Keys.extraDark] ?: (p[Keys.theme] == "Amoled"),
        )
    }

    val savedCommands: Flow<List<String>> = context.dataStore.data.map { p ->
        (p[Keys.saved] ?: emptySet()).sorted()
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.dynamic] = on }
    suspend fun setHaptics(on: Boolean) = context.dataStore.edit { it[Keys.haptics] = on }
    suspend fun setSeedColor(argb: Int) = context.dataStore.edit { it[Keys.seed] = argb }
    suspend fun setExtraDark(on: Boolean) = context.dataStore.edit { it[Keys.extraDark] = on }

    suspend fun saveCommand(command: String) = context.dataStore.edit {
        it[Keys.saved] = (it[Keys.saved] ?: emptySet()) + command
    }

    suspend fun deleteCommand(command: String) = context.dataStore.edit {
        it[Keys.saved] = (it[Keys.saved] ?: emptySet()) - command
    }

    suspend fun clearCommands() = context.dataStore.edit { it.remove(Keys.saved) }

    /** Remembered options for a tool ("Use the same settings next time"). Empty if none. */
    suspend fun toolSettings(toolId: String): Map<String, String> {
        val raw = context.dataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("tool_$toolId")]
            ?: return emptyMap()
        return runCatching {
            val o = org.json.JSONObject(raw)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    suspend fun saveToolSettings(toolId: String, values: Map<String, String>?) {
        val key = androidx.datastore.preferences.core.stringPreferencesKey("tool_$toolId")
        context.dataStore.edit {
            if (values == null) it.remove(key) else it[key] = org.json.JSONObject(values as Map<*, *>).toString()
        }
    }
}
