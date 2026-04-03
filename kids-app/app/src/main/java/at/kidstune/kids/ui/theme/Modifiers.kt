package at.kidstune.kids.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Enforces a minimum touch target of 72dp × 72dp on any composable.
 * All interactive elements in the kids app must use this modifier.
 * Standard Material 3 uses 48dp; we use 72dp for small hands.
 */
fun Modifier.kidsTouchTarget(): Modifier = this.defaultMinSize(minWidth = 72.dp, minHeight = 72.dp)
