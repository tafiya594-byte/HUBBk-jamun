package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class AbidExtendedColors(
    val outgoingBubble: Color,
    val outgoingBubbleText: Color,
    val incomingBubble: Color,
    val incomingBubbleText: Color,
    val chatCanvas: Color,
    val readTick: Color,
    val onlineDot: Color,
    val mutedText: Color
)

val LocalAbidColors = staticCompositionLocalOf {
    AbidExtendedColors(
        outgoingBubble = OutgoingBubbleLight,
        outgoingBubbleText = OutgoingBubbleTextLight,
        incomingBubble = IncomingBubbleLight,
        incomingBubbleText = IncomingBubbleTextLight,
        chatCanvas = ChatCanvasLight,
        readTick = ReadTickBlue,
        onlineDot = OnlineGreen,
        mutedText = LightOnSurfaceMuted
    )
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = MintContainer,
    onPrimaryContainer = EmeraldPrimaryDark,
    secondary = EmeraldVibrant,
    onSecondary = Color.White,
    secondaryContainer = MintContainerStrong,
    onSecondaryContainer = EmeraldPrimaryDark,
    tertiary = EmeraldLight,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldVibrant,
    onPrimary = Color(0xFF002117),
    primaryContainer = Color(0xFF00513B),
    onPrimaryContainer = Color(0xFF8EF7D0),
    secondary = EmeraldLight,
    onSecondary = Color(0xFF002111),
    secondaryContainer = Color(0xFF16382C),
    onSecondaryContainer = Color(0xFFC2EED9),
    tertiary = EmeraldPrimary,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    error = ErrorRedDark,
    onError = Color.Black
)

private val AbidShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ABIDMessengerTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val extended = if (isDark) {
        AbidExtendedColors(
            outgoingBubble = OutgoingBubbleDark,
            outgoingBubbleText = OutgoingBubbleTextDark,
            incomingBubble = IncomingBubbleDark,
            incomingBubbleText = IncomingBubbleTextDark,
            chatCanvas = ChatCanvasDark,
            readTick = ReadTickBlue,
            onlineDot = OnlineGreen,
            mutedText = DarkOnSurfaceMuted
        )
    } else {
        AbidExtendedColors(
            outgoingBubble = OutgoingBubbleLight,
            outgoingBubbleText = OutgoingBubbleTextLight,
            incomingBubble = IncomingBubbleLight,
            incomingBubbleText = IncomingBubbleTextLight,
            chatCanvas = ChatCanvasLight,
            readTick = ReadTickBlue,
            onlineDot = OnlineGreen,
            mutedText = LightOnSurfaceMuted
        )
    }

    CompositionLocalProvider(LocalAbidColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AbidTypography,
            shapes = AbidShapes,
            content = content
        )
    }
}
