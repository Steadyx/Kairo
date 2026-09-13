@file:Suppress("MagicNumber")

package com.kairo.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.kairo.reader.core.model.ReaderTheme

val LightBackground = Color(0xFFF7F4ED)
val LightOnBackground = Color(0xFF25231F)
val LightPrimary = Color(0xFF5F5A12)
val LightSecondary = Color(0xFF5D6E58)
val LightLink = Color(0xFF4D638C)
val LightSurfaceVariant = Color(0xFFE9E3D6)
val LightOnSurfaceVariant = Color(0xFF5C5850)
val LightPrimaryContainer = Color(0xFFE5DF9B)

val LinenBackground = Color(0xFFF4EFE4)
val LinenOnBackground = Color(0xFF29241D)
val LinenPrimary = Color(0xFF6C5530)
val LinenSecondary = Color(0xFF626B50)
val LinenLink = Color(0xFF52657A)
val LinenSurfaceVariant = Color(0xFFE7DECE)
val LinenOnSurfaceVariant = Color(0xFF5E5548)
val LinenPrimaryContainer = Color(0xFFE6D2AE)

val MistBackground = Color(0xFFEEF4F2)
val MistOnBackground = Color(0xFF202827)
val MistPrimary = Color(0xFF376B69)
val MistSecondary = Color(0xFF5B6D74)
val MistLink = Color(0xFF59628B)
val MistSurfaceVariant = Color(0xFFDDE8E5)
val MistOnSurfaceVariant = Color(0xFF53615F)
val MistPrimaryContainer = Color(0xFFBFD9D5)

val SageBackground = Color(0xFFF0F2E8)
val SageOnBackground = Color(0xFF23281F)
val SagePrimary = Color(0xFF4F6A3D)
val SageSecondary = Color(0xFF6B654A)
val SageLink = Color(0xFF52666E)
val SageSurfaceVariant = Color(0xFFE0E5D5)
val SageOnSurfaceVariant = Color(0xFF56604F)
val SagePrimaryContainer = Color(0xFFC9D8B8)

val SepiaBackground = Color(0xFFF2E6D2)
val SepiaOnBackground = Color(0xFF2B2118)
val SepiaPrimary = Color(0xFF7A4B1F)
val SepiaSecondary = Color(0xFF6C6046)
val SepiaLink = Color(0xFF5A6386)
val SepiaSurfaceVariant = Color(0xFFE2D3BA)
val SepiaOnSurfaceVariant = Color(0xFF625643)
val SepiaPrimaryContainer = Color(0xFFE8C99E)

val DarkBackground = Color(0xFF111318)
val DarkOnBackground = Color(0xFFE2E2DD)
val DarkPrimary = Color(0xFFB7C7E9)
val DarkSecondary = Color(0xFFC1C0B0)
val DarkLink = Color(0xFFC3CEB0)
val DarkSurfaceVariant = Color(0xFF1D2027)
val DarkOnSurfaceVariant = Color(0xFFC6C5BE)
val DarkPrimaryContainer = Color(0xFF2A354C)

val InkBackground = Color(0xFF0F1216)
val InkOnBackground = Color(0xFFE1E5E5)
val InkPrimary = Color(0xFFAFC7D6)
val InkSecondary = Color(0xFFBDC6C0)
val InkLink = Color(0xFFC8C0D2)
val InkSurfaceVariant = Color(0xFF1A1E24)
val InkOnSurfaceVariant = Color(0xFFC2C9C8)
val InkPrimaryContainer = Color(0xFF253847)

val PlumBackground = Color(0xFF17121A)
val PlumOnBackground = Color(0xFFE8E0E6)
val PlumPrimary = Color(0xFFD5B6C9)
val PlumSecondary = Color(0xFFCBBEC5)
val PlumLink = Color(0xFFC3CCB8)
val PlumSurfaceVariant = Color(0xFF251D28)
val PlumOnSurfaceVariant = Color(0xFFCEC2CA)
val PlumPrimaryContainer = Color(0xFF3D2B38)

val EmberBackground = Color(0xFF17130F)
val EmberOnBackground = Color(0xFFE8E0D6)
val EmberPrimary = Color(0xFFD8B58F)
val EmberSecondary = Color(0xFFC9C1A7)
val EmberLink = Color(0xFFB8CFBE)
val EmberSurfaceVariant = Color(0xFF251E18)
val EmberOnSurfaceVariant = Color(0xFFCFC4B8)
val EmberPrimaryContainer = Color(0xFF3F2F20)

val NordBackground = Color(0xFF101820)
val NordOnBackground = Color(0xFFDDE6EA)
val NordPrimary = Color(0xFF9FCBD3)
val NordSecondary = Color(0xFFB7C4D4)
val NordLink = Color(0xFFC7C5A6)
val NordSurfaceVariant = Color(0xFF1A2630)
val NordOnSurfaceVariant = Color(0xFFBDCBD2)
val NordPrimaryContainer = Color(0xFF233E48)

val CyberpunkBackground = Color(0xFF15131D)
val CyberpunkOnBackground = Color(0xFFE8E1E8)
val CyberpunkPrimary = Color(0xFFD5B8E8)
val CyberpunkSecondary = Color(0xFFCBBAC7)
val CyberpunkLink = Color(0xFFBFD1CF)
val CyberpunkSurfaceVariant = Color(0xFF24202C)
val CyberpunkOnSurfaceVariant = Color(0xFFCCC0CB)
val CyberpunkPrimaryContainer = Color(0xFF3A2942)

val ForestBackground = Color(0xFF10170F)
val ForestOnBackground = Color(0xFFE2E8DE)
val ForestPrimary = Color(0xFFA9D39D)
val ForestSecondary = Color(0xFFBAC8A9)
val ForestLink = Color(0xFFB8D3BF)
val ForestSurfaceVariant = Color(0xFF1B2519)
val ForestOnSurfaceVariant = Color(0xFFC4CEBA)
val ForestPrimaryContainer = Color(0xFF253A22)

internal data class ReaderThemePalette(
    val isDark: Boolean,
    val background: Color,
    val onBackground: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
) {
    val surface: Color = background
    val surfaceContainerLowest: Color = background
    val surfaceContainerLow: Color =
        surfaceBlend(if (isDark) DARK_SURFACE_LOW_ALPHA else LIGHT_SURFACE_LOW_ALPHA)
    val surfaceContainer: Color =
        surfaceBlend(if (isDark) DARK_SURFACE_ALPHA else LIGHT_SURFACE_ALPHA)
    val surfaceContainerHigh: Color =
        surfaceBlend(if (isDark) DARK_SURFACE_HIGH_ALPHA else LIGHT_SURFACE_HIGH_ALPHA)
    val surfaceContainerHighest: Color = surfaceVariant
    val surfaceDim: Color = if (isDark) background else surfaceContainerHigh
    val surfaceBright: Color = if (isDark) surfaceContainerHighest else background
    val inversePrimary: Color = primaryContainer
    val primaryFixed: Color = primaryContainer
    val primaryFixedDim: Color = primaryContainer
    val onPrimaryFixed: Color = onPrimaryContainer
    val onPrimaryFixedVariant: Color = primary
    val secondaryFixed: Color = surfaceContainerHighest
    val secondaryFixedDim: Color = surfaceContainerHigh
    val onSecondaryFixed: Color = onBackground
    val onSecondaryFixedVariant: Color = onSurfaceVariant
    val tertiaryFixed: Color = surfaceContainerHighest
    val tertiaryFixedDim: Color = surfaceContainerHigh
    val onTertiaryFixed: Color = onBackground
    val onTertiaryFixedVariant: Color = onSurfaceVariant
    val error: Color = if (isDark) DarkError else LightError
    val onError: Color = if (isDark) DarkOnError else LightOnError
    val errorContainer: Color = if (isDark) DarkErrorContainer else LightErrorContainer
    val onErrorContainer: Color = if (isDark) DarkOnErrorContainer else LightOnErrorContainer

    private fun surfaceBlend(alpha: Float): Color =
        surfaceVariant.copy(alpha = alpha).compositeOver(background)

    private companion object {
        const val LIGHT_SURFACE_LOW_ALPHA = 0.28f
        const val LIGHT_SURFACE_ALPHA = 0.48f
        const val LIGHT_SURFACE_HIGH_ALPHA = 0.68f
        const val DARK_SURFACE_LOW_ALPHA = 0.34f
        const val DARK_SURFACE_ALPHA = 0.54f
        const val DARK_SURFACE_HIGH_ALPHA = 0.76f
    }
}

private val LightError = Color(0xFF8B3A32)
private val LightOnError = Color(0xFFFFF7F4)
private val LightErrorContainer = Color(0xFFF2D4CE)
private val LightOnErrorContainer = Color(0xFF3B0B07)
private val DarkError = Color(0xFFE8A29A)
private val DarkOnError = Color(0xFF3A100B)
private val DarkErrorContainer = Color(0xFF5A211B)
private val DarkOnErrorContainer = Color(0xFFFFDAD4)

// This exhaustive theme-to-token table is data expressed as Kotlin, not procedural control flow.
@Suppress("LongMethod")
internal fun ReaderTheme.readerThemePalette(): ReaderThemePalette =
    when (this) {
        ReaderTheme.CUSTOM -> error("Custom themes resolve through CustomTheme.materialColorScheme")
        ReaderTheme.LIGHT ->
            ReaderThemePalette(
                isDark = false,
                background = LightBackground,
                onBackground = LightOnBackground,
                surfaceVariant = LightSurfaceVariant,
                onSurfaceVariant = LightOnSurfaceVariant,
                primary = LightPrimary,
                onPrimary = Color(0xFFFFF9E9),
                primaryContainer = LightPrimaryContainer,
                onPrimaryContainer = LightOnBackground,
                secondary = LightSecondary,
                tertiary = LightLink,
                outline = Color(0xFF767068),
                outlineVariant = Color(0xFFD7D0C3),
                inverseSurface = Color(0xFF302E2A),
                inverseOnSurface = Color(0xFFF4EFE4),
            )

        ReaderTheme.LINEN ->
            ReaderThemePalette(
                isDark = false,
                background = LinenBackground,
                onBackground = LinenOnBackground,
                surfaceVariant = LinenSurfaceVariant,
                onSurfaceVariant = LinenOnSurfaceVariant,
                primary = LinenPrimary,
                onPrimary = Color(0xFFFFF7EA),
                primaryContainer = LinenPrimaryContainer,
                onPrimaryContainer = LinenOnBackground,
                secondary = LinenSecondary,
                tertiary = LinenLink,
                outline = Color(0xFF786F61),
                outlineVariant = Color(0xFFD6CCBA),
                inverseSurface = Color(0xFF332D25),
                inverseOnSurface = Color(0xFFF6EFE3),
            )

        ReaderTheme.MIST ->
            ReaderThemePalette(
                isDark = false,
                background = MistBackground,
                onBackground = MistOnBackground,
                surfaceVariant = MistSurfaceVariant,
                onSurfaceVariant = MistOnSurfaceVariant,
                primary = MistPrimary,
                onPrimary = Color(0xFFF2FFFC),
                primaryContainer = MistPrimaryContainer,
                onPrimaryContainer = MistOnBackground,
                secondary = MistSecondary,
                tertiary = MistLink,
                outline = Color(0xFF6E7B79),
                outlineVariant = Color(0xFFC8D5D2),
                inverseSurface = Color(0xFF293231),
                inverseOnSurface = Color(0xFFEEF4F2),
            )

        ReaderTheme.SAGE ->
            ReaderThemePalette(
                isDark = false,
                background = SageBackground,
                onBackground = SageOnBackground,
                surfaceVariant = SageSurfaceVariant,
                onSurfaceVariant = SageOnSurfaceVariant,
                primary = SagePrimary,
                onPrimary = Color(0xFFF7FFE9),
                primaryContainer = SagePrimaryContainer,
                onPrimaryContainer = SageOnBackground,
                secondary = SageSecondary,
                tertiary = SageLink,
                outline = Color(0xFF717A67),
                outlineVariant = Color(0xFFCDD4C3),
                inverseSurface = Color(0xFF2C3329),
                inverseOnSurface = Color(0xFFF0F2E8),
            )

        ReaderTheme.SEPIA ->
            ReaderThemePalette(
                isDark = false,
                background = SepiaBackground,
                onBackground = SepiaOnBackground,
                surfaceVariant = SepiaSurfaceVariant,
                onSurfaceVariant = SepiaOnSurfaceVariant,
                primary = SepiaPrimary,
                onPrimary = Color(0xFFFFF6EA),
                primaryContainer = SepiaPrimaryContainer,
                onPrimaryContainer = SepiaOnBackground,
                secondary = SepiaSecondary,
                tertiary = SepiaLink,
                outline = Color(0xFF7C705D),
                outlineVariant = Color(0xFFD4C4AA),
                inverseSurface = Color(0xFF352A20),
                inverseOnSurface = Color(0xFFF7EAD6),
            )

        ReaderTheme.DARK ->
            ReaderThemePalette(
                isDark = true,
                background = DarkBackground,
                onBackground = DarkOnBackground,
                surfaceVariant = DarkSurfaceVariant,
                onSurfaceVariant = DarkOnSurfaceVariant,
                primary = DarkPrimary,
                onPrimary = Color(0xFF172033),
                primaryContainer = DarkPrimaryContainer,
                onPrimaryContainer = DarkOnBackground,
                secondary = DarkSecondary,
                tertiary = DarkLink,
                outline = Color(0xFF8D918E),
                outlineVariant = Color(0xFF30343B),
                inverseSurface = Color(0xFFE2E2DD),
                inverseOnSurface = Color(0xFF2E3035),
            )

        ReaderTheme.INK ->
            ReaderThemePalette(
                isDark = true,
                background = InkBackground,
                onBackground = InkOnBackground,
                surfaceVariant = InkSurfaceVariant,
                onSurfaceVariant = InkOnSurfaceVariant,
                primary = InkPrimary,
                onPrimary = Color(0xFF13232C),
                primaryContainer = InkPrimaryContainer,
                onPrimaryContainer = InkOnBackground,
                secondary = InkSecondary,
                tertiary = InkLink,
                outline = Color(0xFF899295),
                outlineVariant = Color(0xFF303842),
                inverseSurface = Color(0xFFE1E5E5),
                inverseOnSurface = Color(0xFF2A3034),
            )

        ReaderTheme.PLUM ->
            ReaderThemePalette(
                isDark = true,
                background = PlumBackground,
                onBackground = PlumOnBackground,
                surfaceVariant = PlumSurfaceVariant,
                onSurfaceVariant = PlumOnSurfaceVariant,
                primary = PlumPrimary,
                onPrimary = Color(0xFF291824),
                primaryContainer = PlumPrimaryContainer,
                onPrimaryContainer = PlumOnBackground,
                secondary = PlumSecondary,
                tertiary = PlumLink,
                outline = Color(0xFF938894),
                outlineVariant = Color(0xFF413545),
                inverseSurface = Color(0xFFE8E0E6),
                inverseOnSurface = Color(0xFF302932),
            )

        ReaderTheme.EMBER ->
            ReaderThemePalette(
                isDark = true,
                background = EmberBackground,
                onBackground = EmberOnBackground,
                surfaceVariant = EmberSurfaceVariant,
                onSurfaceVariant = EmberOnSurfaceVariant,
                primary = EmberPrimary,
                onPrimary = Color(0xFF2A1A0D),
                primaryContainer = EmberPrimaryContainer,
                onPrimaryContainer = EmberOnBackground,
                secondary = EmberSecondary,
                tertiary = EmberLink,
                outline = Color(0xFF978D80),
                outlineVariant = Color(0xFF43382E),
                inverseSurface = Color(0xFFE8E0D6),
                inverseOnSurface = Color(0xFF312A23),
            )

        ReaderTheme.NORD ->
            ReaderThemePalette(
                isDark = true,
                background = NordBackground,
                onBackground = NordOnBackground,
                surfaceVariant = NordSurfaceVariant,
                onSurfaceVariant = NordOnSurfaceVariant,
                primary = NordPrimary,
                onPrimary = Color(0xFF10242B),
                primaryContainer = NordPrimaryContainer,
                onPrimaryContainer = NordOnBackground,
                secondary = NordSecondary,
                tertiary = NordLink,
                outline = Color(0xFF86969F),
                outlineVariant = Color(0xFF2F404A),
                inverseSurface = Color(0xFFDDE6EA),
                inverseOnSurface = Color(0xFF263139),
            )

        ReaderTheme.CYBERPUNK ->
            ReaderThemePalette(
                isDark = true,
                background = CyberpunkBackground,
                onBackground = CyberpunkOnBackground,
                surfaceVariant = CyberpunkSurfaceVariant,
                onSurfaceVariant = CyberpunkOnSurfaceVariant,
                primary = CyberpunkPrimary,
                onPrimary = Color(0xFF27172F),
                primaryContainer = CyberpunkPrimaryContainer,
                onPrimaryContainer = CyberpunkOnBackground,
                secondary = CyberpunkSecondary,
                tertiary = CyberpunkLink,
                outline = Color(0xFF978D99),
                outlineVariant = Color(0xFF403847),
                inverseSurface = Color(0xFFE8E1E8),
                inverseOnSurface = Color(0xFF302A35),
            )

        ReaderTheme.FOREST ->
            ReaderThemePalette(
                isDark = true,
                background = ForestBackground,
                onBackground = ForestOnBackground,
                surfaceVariant = ForestSurfaceVariant,
                onSurfaceVariant = ForestOnSurfaceVariant,
                primary = ForestPrimary,
                onPrimary = Color(0xFF162415),
                primaryContainer = ForestPrimaryContainer,
                onPrimaryContainer = ForestOnBackground,
                secondary = ForestSecondary,
                tertiary = ForestLink,
                outline = Color(0xFF8D9987),
                outlineVariant = Color(0xFF344031),
                inverseSurface = Color(0xFFE2E8DE),
                inverseOnSurface = Color(0xFF2B3229),
            )
    }
