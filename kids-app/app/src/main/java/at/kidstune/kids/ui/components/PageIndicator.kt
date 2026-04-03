package at.kidstune.kids.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.kidstune.kids.ui.theme.KidstuneTheme

/**
 * Row of dots indicating the current page in a paginated grid.
 * The active dot is wider and uses the primary color; inactive dots are smaller and muted.
 */
@Composable
fun PageIndicator(
    modifier: Modifier = Modifier,
    pageCount: Int,
    currentPage: Int
) {
    Row(
        modifier            = modifier.semantics {
            contentDescription = "Seite ${currentPage + 1} von $pageCount"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment   = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val dotWidth by animateDpAsState(
                targetValue   = if (isActive) 24.dp else 10.dp,
                animationSpec = spring(),
                label         = "dot-width-$index"
            )
            val dotColor by animateColorAsState(
                targetValue   = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                animationSpec = spring(),
                label         = "dot-color-$index"
            )
            Box(
                modifier = Modifier
                    .width(dotWidth)
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────

@Preview(name = "PageIndicator – page 1 of 4", showBackground = true)
@Composable
private fun PageIndicatorPreview() {
    KidstuneTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageIndicator(pageCount = 4, currentPage = 0)
            PageIndicator(pageCount = 4, currentPage = 2)
            PageIndicator(pageCount = 1, currentPage = 0)
        }
    }
}
