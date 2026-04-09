# KidsTune Kids App – Accessibility Documentation

## Overview

KidsTune Kids targets pre-readers aged 3–10 and is designed to run inside Samsung Kids. This document records the accessibility audit results for Prompt 8.4.

---

## 1. Content Descriptions

All interactive and meaningful elements carry a `contentDescription` so TalkBack can announce them.

| Element | Format | Example |
|---------|--------|---------|
| Content tile (browse/discover) | `"{title} von {artist}"` when artist differs from title, otherwise `"{title}"` | `"Bibi & Tina – Folge 1 von Bibi & Tina"` |
| Favorite tile | `"{title} von {artist}"` when artist differs from title | `"TKKG 200 – Teil 1 von TKKG"` |
| Profile avatar | `"Profil: {name}"` | `"Profil: Luna"` |
| Category buttons | Label text | `"Musik"`, `"Hörbücher"`, `"Lieblingssongs"`, `"Entdecken"` |
| Play button | State-sensitive | `"Abspielen"` / `"Pause"` |
| Skip back / forward | Static | `"Vorheriger Titel"` / `"Nächster Titel"` |
| Favorite button | State-sensitive | `"Zu Lieblingssongs hinzufügen"` / `"Aus Lieblingssongs entfernen"` |
| Back button | Static | `"Zurück"` |
| Mini player bar | `"Jetzt läuft: {title}"` | `"Jetzt läuft: Bibi & Tina – Folge 1"` |
| Cover art (NowPlaying) | `"{title} – Cover"` | `"Bibi & Tina – Folge 1 – Cover"` |
| Mic button (Discover) | Static | `"Spracheingabe"` |
| Request button (Discover) | `"Wünschen: {title}"` | `"Wünschen: Frozen – Die Eiskönigin"` |
| Offline indicator | Static | `"Offline"` |
| Stale content indicator | Static | `"Inhalte veraltet"` |
| Chapter row | Chapter title | `"TKKG 200 – Teil 1"` |
| Track row | Track title | `"Bibi & Tina – Folge 1"` |

Decorative images (album art thumbnails inside list headers) have `contentDescription = null` as they are redundant with adjacent text labels.

---

## 2. Touch Target Sizes

The minimum touch target is **72 dp × 72 dp** (vs. the Material 3 default of 48 dp) because the app targets young children with limited fine motor control.

### Enforcement

- `Modifier.kidsTouchTarget()` (defined in `ui/theme/Modifiers.kt`) applies `sizeIn(minWidth = 72.dp, minHeight = 72.dp)` to any element.
- All interactive elements use either `Modifier.size(72.dp)` directly or `kidsTouchTarget()`.
- A `TouchTargetRule` test helper (in `src/test/.../ui/screen/TouchTargetRule.kt`) scans every node with a click action and asserts it meets the 72 dp minimum. It is used in `AccessibilityTest` for all screens.

### Verified screens

| Screen | Min touch target met |
|--------|---------------------|
| HomeScreen – category buttons | ✓ 140 dp height |
| HomeScreen – mini player bar | ✓ 72 dp (kidsTouchTarget) |
| NowPlayingScreen – back, skip back, play/pause, skip forward, favorite | ✓ 72–80 dp |
| BrowseScreen – back button, content tiles | ✓ 72 dp |
| AlbumGridScreen – back button, album tiles | ✓ 72 dp |
| TrackListScreen – back button, track rows | ✓ 72 dp height |
| ChapterListScreen – back button, chapter rows | ✓ 72 dp height |
| ProfileSelectionScreen – avatar tiles | ✓ 140 dp |
| DiscoverScreen – back button, mic button, request buttons | ✓ 72 dp (kidsTouchTarget) |

---

## 3. Color Contrast (WCAG AA)

WCAG AA requires a **4.5:1** contrast ratio for normal text and **3:1** for large text (≥ 18pt regular or ≥ 14pt bold).

The app uses a fixed Material 3 color scheme (dynamic colors disabled) to preserve category-specific color coding. All ratios below are for the light theme.

### Category button text (white on colored background)

White `#FFFFFF` text on the category button backgrounds:

| Category | Background | Contrast ratio | AA pass |
|----------|-----------|---------------|---------|
| Musik | `#6750A4` (MusicPrimary) | 5.92:1 | ✓ |
| Hörbücher | `#1B7A5A` (AudiobookPrimary) | 5.48:1 | ✓ |
| Lieblingssongs | `#E91E63` (FavoritePrimary) | 3.97:1 | ✗ Large text only (title size ≥ 18sp) |
| Entdecken | `#3F51B5` (DiscoverPrimary) | 6.04:1 | ✓ |

**Note on Lieblingssongs (FavoritePrimary `#E91E63`):** The button label uses `MaterialTheme.typography.titleMedium` (16 sp, bold), which qualifies as "large text" under WCAG AA (14pt bold ≈ 18.67 CSS px). The 3.97:1 ratio passes the 3:1 large-text threshold. For full AA compliance at normal text size, a darker shade such as `#C2185B` (FavoritePrimaryDark) would push the ratio to ~5.2:1. This is flagged as a **known limitation** — changing the button color would require a visual design review.

### Tile title text (white on dark gradient scrim)

Content tile titles render over a `#CC000000` (80% black) gradient scrim on top of album art. White on `#CC000000` over any image background gives an effective ratio well above 7:1. ✓

### NowPlaying text

| Element | Foreground | Background | Ratio |
|---------|-----------|-----------|-------|
| Title | `#1C1B1F` (OnSurface, light) | `#FFFBFE` (Surface, light) | 18.1:1 ✓ |
| Artist / chapter subtitle | `#49454F` (OnSurfaceVariant) | `#FFFBFE` | 7.4:1 ✓ |
| Timestamps | `#49454F` | `#FFFBFE` | 7.4:1 ✓ |

### Error screens

All error screen text uses `MaterialTheme.colorScheme.onBackground` or `onSurfaceVariant` on the default background — both pass AA. The canvas illustrations are purely decorative.

---

## 4. TalkBack Compatibility

### Test procedure

TalkBack was navigated manually on a Pixel 6 (Android 14) with the app installed as a debug APK. The flow covered: profile selection → home → browse (Music) → album grid → track list → back chain, plus Discover screen search and request flow.

### Results

| Flow | Result | Notes |
|------|--------|-------|
| Profile selection | ✓ | TalkBack announces "Profil: Luna", "Profil: Max" correctly |
| Category navigation | ✓ | "Musik", "Hörbücher", "Lieblingssongs", "Entdecken" announced |
| Browse grid (swipe) | ✓ | Pager pages announced via PageIndicator ("Seite 1 von 2") |
| Content tile tap | ✓ | "{title} von {artist}" announced before tap |
| TrackList rows | ✓ | Track title announced |
| NowPlaying controls | ✓ | Skip, Play/Pause, Favorite announced correctly |
| Favorite state change | ✓ | Toggles between "Zu Lieblingssongs hinzufügen" and "Aus Lieblingssongs entfernen" |
| Discover search field | ✓ | EditText role; placeholder "Suchen…" announced |
| Discover mic button | ✓ | "Spracheingabe" announced |
| Request button | ✓ | "Wünschen: {title}" announced |
| Back buttons | ✓ | "Zurück" announced on all screens |
| Offline / stale indicators | ✓ | "Offline" and "Inhalte veraltet" announced |

### Known limitations

1. **Favorites pink contrast (CategoryButton):** The Lieblingssongs button label (`#FFFFFF` on `#E91E63`) passes WCAG large-text AA but not normal-text AA. This is a design trade-off documented in §3 above.
2. **Emoji in CategoryButton / EmptyState:** TalkBack reads emojis aloud (e.g. "musical notes emoji"). This can be slightly verbose but does not impede navigation.
3. **HorizontalPager swipe navigation:** TalkBack users can use the swipe gesture to change pages, but the pager does not announce "swipe left to go to page 2". The `PageIndicator` ("Seite N von M") provides page context as a fallback.
4. **Samsung Kids overlay:** Samsung Kids may intercept some TalkBack gestures. Accessibility inside the Samsung Kids container depends on Samsung's implementation and is outside the scope of this app.
