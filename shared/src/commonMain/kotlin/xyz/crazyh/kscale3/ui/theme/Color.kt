package xyz.crazyh.kscale3.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 设计稿「KScale 3 · Material Design 3」色板。
 * M3 ColorScheme 只覆盖标准角色；成功/警示/hero 底色等语义色放在
 * [KScaleExtraColors]，通过 [LocalKScaleExtraColors] 下发。
 */

// ---- Light ----
internal val LightPrimary = Color(0xFF00696B)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFF9CF1F1)
internal val LightOnPrimaryContainer = Color(0xFF002020)
internal val LightSecondary = Color(0xFF4A6363)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFCCE8E7)
internal val LightOnSecondaryContainer = Color(0xFF051F1F)
internal val LightTertiary = Color(0xFF7A5900)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFFDEA6)
internal val LightOnTertiaryContainer = Color(0xFF261900)
internal val LightError = Color(0xFFBA1A1A)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)
internal val LightSurface = Color(0xFFF6FAF9)
internal val LightOnSurface = Color(0xFF191C1C)
internal val LightOnSurfaceVariant = Color(0xFF3F4949)
internal val LightOutline = Color(0xFF6F7979)
internal val LightOutlineVariant = Color(0xFFBEC9C8)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF0F5F4)
internal val LightSurfaceContainer = Color(0xFFEBF0EF)
internal val LightSurfaceContainerHigh = Color(0xFFE5EAE9)
internal val LightSurfaceContainerHighest = Color(0xFFDEE4E3)
internal val LightInverseSurface = Color(0xFF2C3130)
internal val LightInverseOnSurface = Color(0xFFEDF1F0)
internal val LightInversePrimary = Color(0xFF7FD9D8)

// ---- Dark（设计稿建议值）----
internal val DarkPrimary = Color(0xFF7FD9D8)
internal val DarkOnPrimary = Color(0xFF003737)
internal val DarkPrimaryContainer = Color(0xFF004F50)
internal val DarkOnPrimaryContainer = Color(0xFF9CF1F1)
internal val DarkSecondary = Color(0xFFB3CCCB)
internal val DarkOnSecondary = Color(0xFF1B3534)
internal val DarkSecondaryContainer = Color(0xFF314B4A)
internal val DarkOnSecondaryContainer = Color(0xFFCCE8E7)
internal val DarkTertiary = Color(0xFFF7BD48)
internal val DarkOnTertiary = Color(0xFF412D00)
internal val DarkTertiaryContainer = Color(0xFF5D4200)
internal val DarkOnTertiaryContainer = Color(0xFFFFDEA6)
internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
internal val DarkSurface = Color(0xFF0F1413)
internal val DarkOnSurface = Color(0xFFDEE4E2)
internal val DarkOnSurfaceVariant = Color(0xFFBEC9C8)
internal val DarkOutline = Color(0xFF889392)
internal val DarkOutlineVariant = Color(0xFF3F4949)
internal val DarkSurfaceContainerLowest = Color(0xFF0A0F0E)
internal val DarkSurfaceContainerLow = Color(0xFF161D1C)
internal val DarkSurfaceContainer = Color(0xFF1A211F)
internal val DarkSurfaceContainerHigh = Color(0xFF252D2B)
internal val DarkSurfaceContainerHighest = Color(0xFF303837)
internal val DarkInverseSurface = Color(0xFFDEE4E2)
internal val DarkInverseOnSurface = Color(0xFF2C3130)
internal val DarkInversePrimary = Color(0xFF00696B)

/** M3 标准角色之外的语义色。 */
@Immutable
data class KScaleExtraColors(
    val success: Color,
    val successContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    /** 最新测量 / 结果页 hero 卡的平色底 */
    val heroSurface: Color,
    val onHeroSurface: Color,
    /** hero 卡上的次级文字/水印 */
    val onHeroSurfaceVariant: Color,
)

internal val LightExtraColors = KScaleExtraColors(
    success = Color(0xFF1E6B3A),
    successContainer = Color(0xFFDDF2E3),
    amber = Color(0xFF8A5A00),
    amberContainer = Color(0xFFFFE8BE),
    heroSurface = Color(0xFFCFF0EA),
    onHeroSurface = Color(0xFF002020),
    onHeroSurfaceVariant = Color(0xFF1D4B47),
)

internal val DarkExtraColors = KScaleExtraColors(
    success = Color(0xFF7DD59A),
    successContainer = Color(0xFF173B25),
    amber = Color(0xFFF5C05A),
    amberContainer = Color(0xFF3E2E0A),
    heroSurface = Color(0xFF12312F),
    onHeroSurface = Color(0xFF9CF1F1),
    onHeroSurfaceVariant = Color(0xFF7FCBCA),
)

val LocalKScaleExtraColors = staticCompositionLocalOf { LightExtraColors }
