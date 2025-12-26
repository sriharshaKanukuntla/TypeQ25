package it.srik.TypeQ25.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BlackBerryBlue,
    secondary = BlackBerryGray,
    tertiary = BlackBerrySilver,
    background = BlackBerryBlack,
    surface = BlackBerryDarkGray,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = BlackBerryBlack,
    onBackground = BlackBerrySilver,
    onSurface = BlackBerrySilver
)

private val LightColorScheme = lightColorScheme(
    primary = BlackBerryBlue,
    secondary = BlackBerryGray,
    tertiary = BlackBerryDarkGray,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = BlackBerryBlack,
    onSurface = BlackBerryBlack
)

@Composable
fun TypeQ25Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to use BlackBerry theme
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}