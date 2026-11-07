package com.mounsokdara.zyphvert.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mounsokdara.zyphvert.data.AppSettings
import com.mounsokdara.zyphvert.data.SettingsRepository
import com.mounsokdara.zyphvert.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { repo.setThemeMode(mode) } }
    fun setDynamicColor(on: Boolean) { viewModelScope.launch { repo.setDynamicColor(on) } }
    fun setSeedColor(argb: Int) { viewModelScope.launch { repo.setSeedColor(argb) } }
    fun setHaptics(on: Boolean) { viewModelScope.launch { repo.setHaptics(on) } }
    fun setExtraDark(on: Boolean) { viewModelScope.launch { repo.setExtraDark(on) } }
    fun clearSavedCommands() { viewModelScope.launch { repo.clearCommands() } }
}
