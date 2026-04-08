package at.kidstune.kids.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared layout for all kid-facing error screens.
 *
 * Design rules:
 * - No action buttons – parent intervention required
 * - Large illustration (Canvas-drawn, no image files)
 * - Simple German text, no technical jargon
 * - Consistent padding and vertical centring
 *
 * The [contentDescriptionTag] is used as the accessibility semantic on the root node
 * so UI tests can find the screen by its identity.
 */
@Composable
fun KidsErrorScreen(
    modifier: Modifier = Modifier,
    contentDescriptionTag: String,
    illustration: @Composable () -> Unit,
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics { contentDescription = contentDescriptionTag },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        illustration()

        Spacer(Modifier.height(32.dp))

        Text(
            text      = title,
            style     = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text      = subtitle,
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
