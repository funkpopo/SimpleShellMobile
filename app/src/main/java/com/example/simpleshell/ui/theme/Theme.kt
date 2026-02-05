package com.example.simpleshell.ui.theme

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
import com.example.simpleshell.domain.model.ThemeColor

private val PurpleDarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val PurpleLightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val BlueDarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = BlueGrey80,
    tertiary = LightBlue80
)

private val BlueLightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = BlueGrey40,
    tertiary = LightBlue40
)

private val GreenDarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFF9CF89E),
    secondary = GreenGrey80,
    tertiary = LightGreen80
)

private val GreenLightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF89E),
    onPrimaryContainer = Color(0xFF002204),
    secondary = GreenGrey40,
    tertiary = LightGreen40
)

private val OrangeDarkColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3C00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = OrangeGrey80,
    tertiary = Amber80
)

private val OrangeLightColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = OrangeGrey40,
    tertiary = Amber40
)

private val RedDarkColorScheme = darkColorScheme(
    primary = Red80,
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = RedGrey80,
    tertiary = DeepOrange80
)

private val RedLightColorScheme = lightColorScheme(
    primary = Red40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = RedGrey40,
    tertiary = DeepOrange40
)

private val TealDarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9EF2E4),
    secondary = TealGrey80,
    tertiary = Cyan80
)

private val TealLightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = TealGrey40,
    tertiary = Cyan40
)

private val PinkDarkColorScheme = darkColorScheme(
    primary = Pink80Light,
    onPrimary = Color(0xFF5D1133),
    primaryContainer = Color(0xFF7B294A),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = PinkGrey80,
    tertiary = Rose80
)

private val PinkLightColorScheme = lightColorScheme(
    primary = Pink40Light,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = PinkGrey40,
    tertiary = Rose40
)

@Composable
fun SimpleShellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeColor: ThemeColor = ThemeColor.PURPLE,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> when (themeColor) {
            ThemeColor.PURPLE -> PurpleDarkColorScheme
            ThemeColor.BLUE -> BlueDarkColorScheme
            ThemeColor.GREEN -> GreenDarkColorScheme
            ThemeColor.ORANGE -> OrangeDarkColorScheme
            ThemeColor.RED -> RedDarkColorScheme
            ThemeColor.TEAL -> TealDarkColorScheme
            ThemeColor.PINK -> PinkDarkColorScheme
        }
        else -> when (themeColor) {
            ThemeColor.PURPLE -> PurpleLightColorScheme
            ThemeColor.BLUE -> BlueLightColorScheme
            ThemeColor.GREEN -> GreenLightColorScheme
            ThemeColor.ORANGE -> OrangeLightColorScheme
            ThemeColor.RED -> RedLightColorScheme
            ThemeColor.TEAL -> TealLightColorScheme
            ThemeColor.PINK -> PinkLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}