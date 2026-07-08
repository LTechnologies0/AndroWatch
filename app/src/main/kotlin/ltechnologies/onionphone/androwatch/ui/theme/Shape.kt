package ltechnologies.onionphone.androwatch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Material 3 corner-radius shape scale for AndroWatch (synced from `docs/m3-tokens.xml`). */
// ponytail: synced from docs/m3-tokens.xml corner radii
val AndroWatchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
