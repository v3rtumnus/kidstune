package at.kidstune.kids.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary          = MusicPrimary,
    onPrimary        = SurfaceLight,
    primaryContainer = MusicContainer,
    onPrimaryContainer = MusicOnContainer,
    secondary        = AudiobookPrimary,
    onSecondary      = SurfaceLight,
    secondaryContainer = AudiobookContainer,
    onSecondaryContainer = AudiobookOnContainer,
    tertiary         = FavoritePrimary,
    onTertiary       = SurfaceLight,
    tertiaryContainer = FavoriteContainer,
    onTertiaryContainer = FavoriteOnContainer,
    background       = BackgroundLight,
    onBackground     = OnSurfaceLight,
    surface          = SurfaceLight,
    onSurface        = OnSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary          = MusicContainer,
    onPrimary        = MusicOnContainer,
    primaryContainer = MusicPrimaryDark,
    onPrimaryContainer = MusicContainer,
    secondary        = AudiobookContainer,
    onSecondary      = AudiobookOnContainer,
    secondaryContainer = AudiobookPrimaryDark,
    onSecondaryContainer = AudiobookContainer,
    tertiary         = FavoriteContainer,
    onTertiary       = FavoriteOnContainer,
    tertiaryContainer = FavoritePrimaryDark,
    onTertiaryContainer = FavoriteContainer,
    background       = BackgroundDark,
    onBackground     = OnSurfaceDark,
    surface          = SurfaceDark,
    onSurface        = OnSurfaceDark,
)

@Composable
fun KidstuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled: the kids app relies on specific category colors,
    // and dynamic colors would break the Music=blue, Audiobooks=green, Favorites=pink coding.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = KidstuneTypography,
        shapes      = KidstuneShapes,
        content     = content
    )
}
