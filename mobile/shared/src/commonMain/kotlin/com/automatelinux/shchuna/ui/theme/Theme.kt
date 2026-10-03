package com.automatelinux.shchuna.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.automatelinux.shchuna.resources.Res
import com.automatelinux.shchuna.resources.rubik_black
import com.automatelinux.shchuna.resources.rubik_bold
import com.automatelinux.shchuna.resources.rubik_medium
import com.automatelinux.shchuna.resources.rubik_regular
import org.jetbrains.compose.resources.Font

/**
 * Map colours: paper, the deep teal of map ink, and amber for "the neighbourhood
 * you are in" — the same three as the icon. The parking card alone keeps the
 * blue-and-white of an Israeli paid-parking kerb, because that card IS parking.
 */
object Palette {
    val Page = Color(0xFFF7F4EE)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF14211F)
    val Muted = Color(0xFF5E6B69)
    val Hairline = Color(0xFFE6E0D4)
    val Brand = Color(0xFF1E6B6B)
    val BrandDeep = Color(0xFF0E3A40)
    val BrandSoft = Color(0xFFE3EFEC)
    val Highlight = Color(0xFFF2B544)
    val Paper = Color(0xFFF6F1E6)
    // Parking card only.
    val Sign = Color(0xFF1F55B4)
    val SignDeep = Color(0xFF0B2459)
    val SignSoft = Color(0xFFE8EFFB)
    val Kerb = Color(0xFF2463C9)
    val Stop = Color(0xFFC0362C)
    val Live = Color(0xFF14A06B)
}

@Composable
fun rubik(): FontFamily = FontFamily(
    Font(Res.font.rubik_regular, FontWeight.Normal),
    Font(Res.font.rubik_medium, FontWeight.Medium),
    Font(Res.font.rubik_bold, FontWeight.Bold),
    Font(Res.font.rubik_black, FontWeight.Black),
)

private val Colors = lightColorScheme(
    primary = Palette.Brand,
    onPrimary = Color.White,
    secondary = Palette.Highlight,
    background = Palette.Page,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    onBackground = Palette.Ink,
    error = Palette.Stop,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val family = rubik()
    val base = Typography()
    fun TextStyle.rubik() = copy(fontFamily = family)
    val typography = Typography(
        displayLarge = base.displayLarge.rubik(), displayMedium = base.displayMedium.rubik(), displaySmall = base.displaySmall.rubik(),
        headlineLarge = base.headlineLarge.rubik(), headlineMedium = base.headlineMedium.rubik(), headlineSmall = base.headlineSmall.rubik(),
        titleLarge = base.titleLarge.rubik(), titleMedium = base.titleMedium.rubik(), titleSmall = base.titleSmall.rubik(),
        bodyLarge = base.bodyLarge.rubik(), bodyMedium = base.bodyMedium.rubik(), bodySmall = base.bodySmall.rubik(),
        labelLarge = base.labelLarge.rubik(), labelMedium = base.labelMedium.rubik(), labelSmall = base.labelSmall.rubik(),
    )
    MaterialTheme(colorScheme = Colors, typography = typography, content = content)
}
