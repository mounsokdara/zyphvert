@file:OptIn(ExperimentalMaterial3Api::class)

package com.mounsokdara.zyphvert.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.data.ThemeMode
import com.mounsokdara.zyphvert.ui.components.SubScreenScaffold
import com.mounsokdara.zyphvert.ui.theme.SeedChip
import com.mounsokdara.zyphvert.ui.theme.SeedChips

private val SegmentLabels = mapOf(ThemeMode.Light to "Light", ThemeMode.Dark to "Dark", ThemeMode.System to "System")
private val SegmentOrder = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System)

/** The Theme page, laid out like the one in the Video Player: Appearance, Dynamic color, Material You color, Custom color. */
@Composable
fun ThemeScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicOn = s.dynamicColor && supportsDynamic
    var picker by remember { mutableStateOf(false) }

    SubScreenScaffold("Theme", onBack) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            Text("Appearance", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentOrder.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.themeMode == mode, onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, SegmentOrder.size),
                        icon = {},
                        label = { Text(SegmentLabels.getValue(mode), maxLines = 1) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            ListItem(
                modifier = Modifier.clickable { vm.setExtraDark(!s.extraDark) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Extra dark") },
                supportingContent = { Text("Pure black backgrounds in the dark theme") },
                trailingContent = { Switch(checked = s.extraDark, onCheckedChange = vm::setExtraDark) },
            )

            Spacer(Modifier.height(8.dp))
            ListItem(
                modifier = Modifier.clickable(enabled = supportsDynamic) { vm.setDynamicColor(!s.dynamicColor) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Dynamic color") },
                supportingContent = {
                    Text(if (supportsDynamic) "Use wallpaper colors when the device supports Material You" else "Needs Android 12 or newer")
                },
                trailingContent = { Switch(checked = dynamicOn, onCheckedChange = vm::setDynamicColor, enabled = supportsDynamic) },
            )

            Spacer(Modifier.height(8.dp))
            // The color choices only matter when dynamic color is off.
            Column(Modifier.alpha(if (dynamicOn) 0.38f else 1f)) {
                Text("Material You color", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp))
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeedChips.forEach { chip ->
                        Plate(chip, selected = !dynamicOn && (s.seedColor and 0xFFFFFF) == (chip.circle.toArgb() and 0xFFFFFF), enabled = !dynamicOn) {
                            vm.setSeedColor(chip.circle.toArgb())
                            vm.setDynamicColor(false)
                        }
                    }
                }
                val isCustom = !dynamicOn && SeedChips.none { (it.circle.toArgb() and 0xFFFFFF) == (s.seedColor and 0xFFFFFF) }
                val shape = RoundedCornerShape(16.dp)
                ListItem(
                    modifier = Modifier.clip(shape)
                        .then(if (isCustom) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                        .clickable(enabled = !dynamicOn) { picker = true },
                    colors = ListItemDefaults.colors(containerColor = if (isCustom) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent),
                    leadingContent = { Box(Modifier.size(40.dp).clip(CircleShape).background(Color(s.seedColor))) },
                    headlineContent = { Text("Custom color") },
                    supportingContent = { Text("#" + "%06X".format(s.seedColor and 0xFFFFFF)) },
                    trailingContent = { Icon(if (isCustom) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                )
            }
        }
    }

    if (picker) ColorPickerSheet(Color(s.seedColor), onDismiss = { picker = false }) { picked ->
        vm.setSeedColor(picked.toArgb())
        vm.setDynamicColor(false)
        picker = false
    }
}

/** A color plate: the light tints of the color behind a round swatch of the color itself. */
@Composable
private fun Plate(chip: SeedChip, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(64.dp).clip(shape).background(chip.top)
            .border(3.dp, if (selected) chip.circle else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(32.dp).background(chip.bottom))
        Box(Modifier.align(Alignment.Center).size(27.dp).clip(CircleShape).background(chip.circle))
    }
}

/** Pick any color: a hex field plus hue, saturation and brightness sliders. */
@Composable
private fun ColorPickerSheet(initial: Color, onDismiss: () -> Unit, onApply: (Color) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var bri by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri)))
    var hex by remember { mutableStateOf("%06X".format(color.toArgb() and 0xFFFFFF)) }
    fun syncHex() { hex = "%06X".format(color.toArgb() and 0xFFFFFF) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text("Custom color", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(color).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)))
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        val raw = v.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hex = raw
                        if (raw.length == 6) {
                            val tmp = FloatArray(3)
                            android.graphics.Color.colorToHSV(0xFF000000.toInt() or raw.toInt(16), tmp)
                            hue = tmp[0]; sat = tmp[1]; bri = tmp[2]
                        }
                    },
                    prefix = { Text("#") }, label = { Text("Hex") }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            LabeledSlider("Hue", hue, 0f, 360f) { hue = it; syncHex() }
            LabeledSlider("Saturation", sat, 0f, 1f) { sat = it; syncHex() }
            LabeledSlider("Brightness", bri, 0f, 1f) { bri = it; syncHex() }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(color) }) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
