package com.mounsokdara.zyphvert.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) {
    System("System default"),
    Light("Light"),
    Dark("Dark"),
    Amoled("AMOLED black"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val haptics: Boolean = true,
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
        val saved = stringSetPreferencesKey("saved_commands")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "") }.getOrDefault(ThemeMode.System),
            dynamicColor = p[Keys.dynamic] ?: true,
            haptics = p[Keys.haptics] ?: true,
        )
    }

    val savedCommands: Flow<List<String>> = context.dataStore.data.map { p ->
        (p[Keys.saved] ?: emptySet()).sorted()
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.dynamic] = on }
    suspend fun setHaptics(on: Boolean) = context.dataStore.edit { it[Keys.haptics] = on }

    suspend fun saveCommand(command: String) = context.dataStore.edit {
        it[Keys.saved] = (it[Keys.saved] ?: emptySet()) + command
    }

    suspend fun deleteCommand(command: String) = context.dataStore.edit {
        it[Keys.saved] = (it[Keys.saved] ?: emptySet()) - command
    }

    suspend fun clearCommands() = context.dataStore.edit { it.remove(Keys.saved) }
}
