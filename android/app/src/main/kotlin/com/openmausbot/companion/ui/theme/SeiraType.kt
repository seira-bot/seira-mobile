package com.openmausbot.companion.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.readingFromContent

object SeiraType {
    val Geist = FontFamily(
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
        Font(R.font.geist_semibold, FontWeight.SemiBold),
    )

    private fun style(size: Int, line: Int, weight: FontWeight, spacing: Float = 0f) = TextStyle(
        fontFamily = Geist,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = spacing.em,
    ).readingFromContent()

    val title1 = style(28, 34, FontWeight.SemiBold, -0.02f)
    val title2 = style(22, 28, FontWeight.SemiBold)
    val title3 = style(17, 22, FontWeight.SemiBold)
    val headline = style(15, 20, FontWeight.SemiBold)
    val body = style(15, 22, FontWeight.Normal)
    val bodyMedium = style(15, 22, FontWeight.Medium)
    val callout = style(13, 18, FontWeight.Normal)
    val caption = style(12, 16, FontWeight.Normal)
    val micro = style(11, 14, FontWeight.Normal)
}

internal val SeiraTypography: androidx.compose.material3.Typography = androidx.compose.material3.Typography(
    displayLarge = SeiraType.title1,
    displayMedium = SeiraType.title1,
    displaySmall = SeiraType.title1,
    headlineLarge = SeiraType.title1,
    headlineMedium = SeiraType.title2,
    headlineSmall = SeiraType.title3,
    titleLarge = SeiraType.title2,
    titleMedium = SeiraType.headline,
    titleSmall = SeiraType.bodyMedium,
    bodyLarge = SeiraType.body,
    bodyMedium = SeiraType.body,
    bodySmall = SeiraType.callout,
    labelLarge = SeiraType.bodyMedium,
    labelMedium = SeiraType.caption.copy(fontWeight = FontWeight.Medium),
    labelSmall = SeiraType.micro,
)
