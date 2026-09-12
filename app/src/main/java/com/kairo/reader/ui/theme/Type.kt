package com.kairo.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private val ExpressiveTypeDefaults = Typography()

val Typography =
    ExpressiveTypeDefaults.copy(
        displayLarge = ExpressiveTypeDefaults.displayLarge.expressive(FontWeight.Bold),
        displayMedium = ExpressiveTypeDefaults.displayMedium.expressive(FontWeight.Bold),
        displaySmall = ExpressiveTypeDefaults.displaySmall.expressive(FontWeight.SemiBold),
        headlineLarge = ExpressiveTypeDefaults.headlineLarge.expressive(FontWeight.Bold),
        headlineMedium = ExpressiveTypeDefaults.headlineMedium.expressive(FontWeight.Bold),
        headlineSmall = ExpressiveTypeDefaults.headlineSmall.expressive(FontWeight.SemiBold),
        titleLarge = ExpressiveTypeDefaults.titleLarge.expressive(FontWeight.Bold),
        titleMedium = ExpressiveTypeDefaults.titleMedium.expressive(FontWeight.SemiBold),
        titleSmall = ExpressiveTypeDefaults.titleSmall.expressive(FontWeight.SemiBold),
        displayLargeEmphasized = ExpressiveTypeDefaults.displayLargeEmphasized.expressive(FontWeight.Black),
        displayMediumEmphasized = ExpressiveTypeDefaults.displayMediumEmphasized.expressive(FontWeight.Black),
        displaySmallEmphasized = ExpressiveTypeDefaults.displaySmallEmphasized.expressive(FontWeight.Bold),
        headlineLargeEmphasized = ExpressiveTypeDefaults.headlineLargeEmphasized.expressive(FontWeight.Black),
        headlineMediumEmphasized = ExpressiveTypeDefaults.headlineMediumEmphasized.expressive(FontWeight.ExtraBold),
        headlineSmallEmphasized = ExpressiveTypeDefaults.headlineSmallEmphasized.expressive(FontWeight.Bold),
        titleLargeEmphasized = ExpressiveTypeDefaults.titleLargeEmphasized.expressive(FontWeight.ExtraBold),
        titleMediumEmphasized = ExpressiveTypeDefaults.titleMediumEmphasized.expressive(FontWeight.Bold),
        titleSmallEmphasized = ExpressiveTypeDefaults.titleSmallEmphasized.expressive(FontWeight.Bold),
        bodyLargeEmphasized = ExpressiveTypeDefaults.bodyLargeEmphasized.expressive(FontWeight.SemiBold),
        bodyMediumEmphasized = ExpressiveTypeDefaults.bodyMediumEmphasized.expressive(FontWeight.SemiBold),
        bodySmallEmphasized = ExpressiveTypeDefaults.bodySmallEmphasized.expressive(FontWeight.SemiBold),
        labelLargeEmphasized = ExpressiveTypeDefaults.labelLargeEmphasized.expressive(FontWeight.Bold),
        labelMediumEmphasized = ExpressiveTypeDefaults.labelMediumEmphasized.expressive(FontWeight.Bold),
        labelSmallEmphasized = ExpressiveTypeDefaults.labelSmallEmphasized.expressive(FontWeight.Bold),
    )

private fun androidx.compose.ui.text.TextStyle.expressive(weight: FontWeight) =
    copy(fontFamily = FontFamily.SansSerif, fontWeight = weight)

internal val FocusedReadingTypography =
    Typography(
        bodyLarge =
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            textDecoration = TextDecoration.None,
        ),
        titleLarge =
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
        ),
    )

internal fun androidx.compose.material3.Typography.withFont(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
    displayLargeEmphasized = displayLargeEmphasized.copy(fontFamily = family),
    displayMediumEmphasized = displayMediumEmphasized.copy(fontFamily = family),
    displaySmallEmphasized = displaySmallEmphasized.copy(fontFamily = family),
    headlineLargeEmphasized = headlineLargeEmphasized.copy(fontFamily = family),
    headlineMediumEmphasized = headlineMediumEmphasized.copy(fontFamily = family),
    headlineSmallEmphasized = headlineSmallEmphasized.copy(fontFamily = family),
    titleLargeEmphasized = titleLargeEmphasized.copy(fontFamily = family),
    titleMediumEmphasized = titleMediumEmphasized.copy(fontFamily = family),
    titleSmallEmphasized = titleSmallEmphasized.copy(fontFamily = family),
    bodyLargeEmphasized = bodyLargeEmphasized.copy(fontFamily = family),
    bodyMediumEmphasized = bodyMediumEmphasized.copy(fontFamily = family),
    bodySmallEmphasized = bodySmallEmphasized.copy(fontFamily = family),
    labelLargeEmphasized = labelLargeEmphasized.copy(fontFamily = family),
    labelMediumEmphasized = labelMediumEmphasized.copy(fontFamily = family),
    labelSmallEmphasized = labelSmallEmphasized.copy(fontFamily = family),
)
