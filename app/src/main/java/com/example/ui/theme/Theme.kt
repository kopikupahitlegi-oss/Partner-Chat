package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ChatPalette(
    val chatCanvas: Color,
    val chatPattern: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val outgoingText: Color,
    val incomingText: Color,
    val readTick: Color,
    val onlineDot: Color,
    val callEnd: Color,
    val callAccept: Color
)

private val LightChatPalette = ChatPalette(
    chatCanvas = LightChatCanvas,
    chatPattern = LightChatPattern,
    outgoingBubble = LightOutgoingBubble,
    incomingBubble = LightIncomingBubble,
    outgoingText = LightOutgoingOnBubble,
    incomingText = LightIncomingOnBubble,
    readTick = SkyReadTick,
    onlineDot = MintAccent,
    callEnd = CoralCallEnd,
    callAccept = MintVibrant
)

private val DarkChatPalette = ChatPalette(
    chatCanvas = DarkChatCanvas,
    chatPattern = DarkChatPattern,
    outgoingBubble = DarkOutgoingBubble,
    incomingBubble = DarkIncomingBubble,
    outgoingText = DarkOutgoingOnBubble,
    incomingText = DarkIncomingOnBubble,
    readTick = SkyReadTick,
    onlineDot = MintAccent,
    callEnd = CoralCallEnd,
    callAccept = MintVibrant
)

val LocalChatPalette = staticCompositionLocalOf { LightChatPalette }

private val SapaChatLightScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = MintVibrant,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = CoralCallEnd,
    onError = Color.White
)

private val SapaChatDarkScheme = darkColorScheme(
    primary = MintVibrant,
    onPrimary = Color(0xFF003828),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldContainer,
    secondary = MintAccent,
    onSecondary = Color(0xFF00391A),
    secondaryContainer = Color(0xFF183D37),
    onSecondaryContainer = TealSecondaryContainer,
    tertiary = SkyReadTick,
    onTertiary = Color(0xFF002F42),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF420000)
)

@Composable
fun SapaChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SapaChatDarkScheme else SapaChatLightScheme
    val chatPalette = if (darkTheme) DarkChatPalette else LightChatPalette

    CompositionLocalProvider(LocalChatPalette provides chatPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SapaChatTypography,
            content = content
        )
    }
}
