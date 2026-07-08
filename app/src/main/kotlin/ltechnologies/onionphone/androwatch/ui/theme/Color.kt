package ltechnologies.onionphone.androwatch.ui.theme

import androidx.compose.ui.graphics.Color
import ltechnologies.onionphone.androwatch.model.Sensitivity

/** AndroWatch brand/palette colors and per-tier container colors used by the Material theme. */
val PrimaryBlue = Color(0xFF4F8EF7)
val PrimaryBlueDark = Color(0xFF7CACFF)
val SecondaryTeal = Color(0xFF2EC4B6)
val TertiaryAmber = Color(0xFFFFB703)

val PassiveContainer = Color(0xFF1B3A4B)
val PassiveOnContainer = Color(0xFFB8E0F5)
val PermissionContainer = Color(0xFF3D2C4A)
val PermissionOnContainer = Color(0xFFE8C4FF)
val AdvancedContainer = Color(0xFF3A2F1B)
val AdvancedOnContainer = Color(0xFFFFE0A3)

val SurfaceDark = Color(0xFF121318)
val SurfaceContainerLowestDark = Color(0xFF0D0E12)
val SurfaceContainerLowDark = Color(0xFF181920)
val SurfaceContainerDark = Color(0xFF1E1F26)
val SurfaceContainerHighDark = Color(0xFF282A33)
val SurfaceContainerHighestDark = Color(0xFF32343D)
val OutlineDark = Color(0xFF8E9199)

val SurfaceLight = Color(0xFFF8F9FF)
val SurfaceContainerLowestLight = Color(0xFFF8F9FF)
val SurfaceContainerLowLight = Color(0xFFF3F4FC)
val SurfaceContainerLight = Color(0xFFEEEFF8)
val SurfaceContainerHighLight = Color(0xFFE4E6F0)
val SurfaceContainerHighestLight = Color(0xFFD8DAE8)
val OutlineLight = Color(0xFF74777F)

/**
 * Returns the container/on-container color pair used to style a sensitivity-tier chip.
 *
 * @param tier Sensitivity tier to style.
 * @return `container to onContainer` colors for [tier].
 */
fun tierChipColors(tier: Sensitivity): Pair<Color, Color> = when (tier) {
    Sensitivity.Passive -> PassiveContainer to PassiveOnContainer
    Sensitivity.Permissioned -> PermissionContainer to PermissionOnContainer
    Sensitivity.Advanced -> AdvancedContainer to AdvancedOnContainer
}
