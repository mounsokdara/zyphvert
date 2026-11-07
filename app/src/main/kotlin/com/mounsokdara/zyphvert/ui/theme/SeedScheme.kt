package com.mounsokdara.zyphvert.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** The app's own green. With this seed (and dynamic color off) the hand-tuned Zyphvert palette is used. */
const val BrandSeed: Int = 0xFF00A86B.toInt()

/**
 * Builds a full Material 3 color scheme from one color, like "Material You color" in the Video Player.
 * Tones are picked in OKLCH, which keeps lightness steps even across hues: the same tone numbers
 * (primary 40/80, containers 90/30, surfaces 98/6 ...) as the Material 3 baseline scheme.
 */
fun seedColorScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val seed = argbToOklch(seedArgb)
    val hue = seed.h
    val chroma = seed.c.coerceIn(0.09, 0.17)
    fun primary(t: Double) = toneColor(hue, chroma, t)
    fun secondary(t: Double) = toneColor(hue, chroma * 0.33, t)
    fun tertiary(t: Double) = toneColor((hue + 60) % 360, chroma * 0.6, t)
    fun neutral(t: Double) = toneColor(hue, 0.008, t)
    fun variant(t: Double) = toneColor(hue, 0.02, t)
    return if (!dark) lightColorScheme(
        primary = primary(40.0), onPrimary = primary(100.0), primaryContainer = primary(90.0), onPrimaryContainer = primary(10.0),
        secondary = secondary(40.0), onSecondary = secondary(100.0), secondaryContainer = secondary(90.0), onSecondaryContainer = secondary(10.0),
        tertiary = tertiary(40.0), onTertiary = tertiary(100.0), tertiaryContainer = tertiary(90.0), onTertiaryContainer = tertiary(10.0),
        background = neutral(98.0), onBackground = neutral(10.0), surface = neutral(98.0), onSurface = neutral(10.0),
        surfaceVariant = variant(90.0), onSurfaceVariant = variant(30.0), outline = variant(50.0), outlineVariant = variant(80.0),
        surfaceDim = neutral(87.0), surfaceBright = neutral(98.0),
        surfaceContainerLowest = neutral(100.0), surfaceContainerLow = neutral(96.0), surfaceContainer = neutral(94.0),
        surfaceContainerHigh = neutral(92.0), surfaceContainerHighest = neutral(90.0),
        inverseSurface = neutral(20.0), inverseOnSurface = neutral(95.0), inversePrimary = primary(80.0), surfaceTint = primary(40.0),
    ) else darkColorScheme(
        primary = primary(80.0), onPrimary = primary(20.0), primaryContainer = primary(30.0), onPrimaryContainer = primary(90.0),
        secondary = secondary(80.0), onSecondary = secondary(20.0), secondaryContainer = secondary(30.0), onSecondaryContainer = secondary(90.0),
        tertiary = tertiary(80.0), onTertiary = tertiary(20.0), tertiaryContainer = tertiary(30.0), onTertiaryContainer = tertiary(90.0),
        background = neutral(6.0), onBackground = neutral(90.0), surface = neutral(6.0), onSurface = neutral(90.0),
        surfaceVariant = variant(30.0), onSurfaceVariant = variant(80.0), outline = variant(60.0), outlineVariant = variant(30.0),
        surfaceDim = neutral(6.0), surfaceBright = neutral(24.0),
        surfaceContainerLowest = neutral(4.0), surfaceContainerLow = neutral(10.0), surfaceContainer = neutral(12.0),
        surfaceContainerHigh = neutral(17.0), surfaceContainerHighest = neutral(22.0),
        inverseSurface = neutral(90.0), inverseOnSurface = neutral(20.0), inversePrimary = primary(40.0), surfaceTint = primary(80.0),
    )
}

/** One of the colors in the Material You row: the circle is the seed, the plate behind it shows its light tints. */
class SeedChip(val label: String, val circle: Color) {
    val hue: Double = argbToOklch(circle.toArgbInt()).h
    val top: Color get() = toneColor(hue, 0.03, 94.0)
    val bottom: Color get() = toneColor((hue + 16) % 360, 0.07, 80.0)
}

private fun Color.toArgbInt(): Int =
    (0xFF shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()

private fun chip(label: String, hex: Long) = SeedChip(label, Color(0xFF000000 or hex))

/** The same fourteen colors as the Video Player's Material You row. */
val SeedChips: List<SeedChip> = listOf(
    chip("Red", 0x9a3b38), chip("Purple", 0x68509f), chip("Blue", 0x38618d), chip("Teal", 0x146781),
    chip("Green", 0x036a62), chip("Violet", 0x68548e), chip("Magenta", 0x7c4e7e), chip("Burgundy", 0x8c4a61),
    chip("Brown", 0x8e4d33), chip("Amber", 0x84541a), chip("Forest", 0x40693f), chip("Olive", 0x586424),
    chip("Navy", 0x3d4c7a), chip("Coral", 0xc45c4a),
)

// ---- OKLCH helpers (Björn Ottosson's OKLab) ----

class Oklch(val l: Double, val c: Double, val h: Double)

private fun toLinear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
private fun fromLinear(v: Double) = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055

fun argbToOklch(argb: Int): Oklch {
    val r = toLinear(((argb shr 16) and 255) / 255.0)
    val g = toLinear(((argb shr 8) and 255) / 255.0)
    val b = toLinear((argb and 255) / 255.0)
    val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    val hue = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + 360 else it }
    return Oklch(lightness, hypot(a, bb), hue)
}

private fun linearRgb(lightness: Double, chroma: Double, hue: Double): DoubleArray {
    val a = chroma * cos(Math.toRadians(hue))
    val b = chroma * sin(Math.toRadians(hue))
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}

private fun inGamut(lightness: Double, chroma: Double, hue: Double) = linearRgb(lightness, chroma, hue).all { it in -0.0005..1.0005 }

/** The color at a Material "tone" (0 black .. 100 white) with the given hue and chroma, pulled into the sRGB gamut. */
fun toneColor(hue: Double, chroma: Double, tone: Double): Color {
    // Tone is CIE L*; the OKLab lightness of the same grey is its cube root of luminance.
    val lightness = if (tone >= 8.0) (tone + 16.0) / 116.0 else Math.cbrt(tone / 903.3)
    var c = 0.0
    if (inGamut(lightness, chroma, hue)) c = chroma else {
        var hi = chroma
        repeat(18) { val mid = (c + hi) / 2; if (inGamut(lightness, mid, hue)) c = mid else hi = mid }
    }
    val rgb = linearRgb(lightness, c, hue).map { fromLinear(it.coerceIn(0.0, 1.0)).toFloat() }
    return Color(rgb[0], rgb[1], rgb[2])
}
