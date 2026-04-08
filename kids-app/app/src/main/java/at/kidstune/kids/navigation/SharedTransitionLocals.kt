package at.kidstune.kids.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Provides the [SharedTransitionScope] created in [KidstuneNavHost] to any
 * composable in the tree without threading it through every function signature.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Provides the per-destination [AnimatedVisibilityScope] (supplied by NavHost's
 * AnimatedContent) so deep composables can attach shared-element modifiers.
 */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
