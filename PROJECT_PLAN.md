# KidsTune – Project Plan

## 1. Executive Summary

**KidsTune** is a system that gives children a safe, controlled Spotify listening experience while providing parents full control over allowed content. Children use a dedicated Android app running inside Samsung Kids on repurposed smartphones; parents manage everything via a responsive web dashboard served by the backend.

| Component | Description |
|-----------|-------------|
| **KidsTune Kids** | Child-facing audio player with large visual tiles, profile selection, favorites |
| **KidsTune Web Dashboard** | Browser-based admin UI for content curation, approval requests, device management (served by the backend, accessible from any device) |
| **KidsTune Backend** | Self-hosted Spring Boot 4 API (Docker) serving both the REST API and the web dashboard |

**Key Design Principle:** Children interact with audio only – never video, never unrestricted browsing. The UI is designed for pre-readers (large images, minimal text, color-coded categories).

---

## 2. Architecture Overview

```
┌──────────────────────┐     ┌──────────────────────┐
│   KidsTune Kids      │     │   KidsTune Web        │
│   (Old Smartphone    │     │   Dashboard           │
│    in Samsung Kids)  │     │   (Any Browser)       │
│                      │     │                       │
│  - Profile selector  │     │  - Content curation   │
│  - Audio playback    │     │  - Approval queue     │
│  - Favorites         │     │  - Profile management │
│  - Content requests  │     │  - Device management  │
│                      │     │  - Email notifications│
│  Spotify App Remote  │     │    on new requests    │
│  SDK (playback)      │     │                       │
└──────────┬───────────┘     └──────────┬────────────┘
           │                            │
           │       REST + WebSocket     │  (served by backend)
           └────────────┬───────────────┘
                        │
              ┌─────────▼──────────┐
              │  KidsTune Backend  │
              │  (Docker/Homeserver)│
              │                    │
              │  - Spring Boot 4   │
              │  - MariaDB         │
              │    (existing)      │
              │  - Thymeleaf/HTMX  │
              │    Web Dashboard   │
              │  - Spring Mail     │
              │    (SMTP)          │
              │  - WebSocket hub   │
              │  - Spotify token   │
              │    management      │
              └────────────────────┘
```

### 2.1 Why a Backend?

A self-hosted backend (rather than direct device-to-device sync) provides:

- **Single source of truth** for allowed content, profiles, and favorites
- **Spotify token management** – OAuth tokens are refreshed server-side; kids' devices never handle auth
- **Approval workflow** – content requests persist even when parent app is closed
- **Push notifications** via WebSocket – instant delivery without Firebase/Google dependency
- **Multi-device support** – multiple kids' devices, multiple parent devices, all in sync
- **Offline resilience** – kids' app caches allowed content locally, syncs when back online

### 2.2 Spotify Account Model

Each person in a KidsTune household has their **own individual Spotify account**. There is no shared family account.

| Account | Owned By | Stored In | Used For |
|---------|----------|-----------|----------|
| Parent Spotify account | Parent(s) | `Family.spotify_refresh_token` | Web dashboard OAuth login; content search and metadata resolution via Spotify Web API |
| Child Spotify account | Each child | `ChildProfile.spotify_refresh_token` | Playback via Spotify App Remote SDK on the child's device (SDK controls the Spotify app logged in with the child's own account); initial listening history import |

**Key implications:**
- The Spotify app on a child's device is logged in with the **child's own Spotify account**, not the parent's.
- `SpotifyTokenService` manages tokens at two levels: per-family (parent) and per-profile (child).
- The backend import wizard fetches each child's **own** listening history, playlists, and top artists using the child's token.
- Content search/resolution from the web dashboard uses the parent's token.

### 2.3 Spotify API Integration Strategy

| API | Used By | Token Level | Purpose |
|-----|---------|-------------|---------|
| **Spotify Web API** | Backend (parent context) | Family | Dashboard content search, metadata resolution, artist/album/playlist lookup |
| **Spotify Web API** | Backend (import context) | Per-profile (child) | Fetch child's listening history, playlists, top artists for import wizard |
| **Spotify App Remote SDK** | Kids App | Child's on-device Spotify session | Playback control (play, pause, skip, seek) |

**Important constraint:** The Spotify App Remote SDK controls the Spotify app running on the same device. The kids' phone must have Spotify installed, but children never open it directly – Samsung Kids only shows KidsTune Kids as an allowed app. Spotify runs as a background process exclusively.

**Spotify Developer App Registration:**
- Register at developer.spotify.com (free)
- Development mode: up to 25 users (more than sufficient for family use)
- No formal Spotify review process required at this scale
- Required scopes for parent account: `user-read-playback-state`, `user-modify-playback-state`, `user-library-read`, `user-read-recently-played`, `playlist-read-private`, `streaming`
- Required scopes for child accounts (import + favorites sync): `user-library-read`, `user-library-modify`, `user-read-recently-played`, `user-top-read`, `playlist-read-private`
  - `user-library-read`: read child's Liked Songs for import pre-population
  - `user-library-modify`: mirror KidsTune heart taps back to Spotify Liked Songs ("Lieblingssongs")

### 2.4 Authentication Flow

Dashboard auth (email/password) is **fully decoupled from Spotify**. The parent logs into the dashboard with their own KidsTune credentials. Spotify tokens are stored separately and used purely for Spotify API calls — not for identity.

```
┌─────────────────────────────────────────────────────────────────┐
│                    INITIAL SETUP (one-time)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Parent opens web dashboard, registers with email + password │
│     └─→ Family row created (email, password_hash)               │
│     └─→ Session cookie issued                                   │
│                                                                 │
│  1b. Parent connects their Spotify account (in Settings):       │
│     └─→ "Connect Spotify Account" → Spotify OAuth PKCE          │
│         └─→ Backend stores family.spotify_refresh_token         │
│         (Required for content search and metadata resolution)   │
│                                                                 │
│  2. Parent creates child profiles via web dashboard             │
│     └─→ Backend stores profiles with avatars + age groups       │
│                                                                 │
│  2b. For each child profile: parent clicks "Link Spotify"       │
│     └─→ Spotify OAuth PKCE for CHILD's Spotify account          │
│         └─→ Backend stores child_profile.spotify_refresh_token  │
│         (Required for listening history import)                 │
│                                                                 │
│  3. Parent clicks "Pair New Device" in web dashboard            │
│     └─→ Backend generates 6-digit pairing code (5 min expiry)  │
│                                                                 │
│  4. Parent enters pairing code on Kids App (on kids' device)    │
│     └─→ Kids App sends code to backend                         │
│         └─→ Backend verifies, issues a KidsTune device token   │
│             └─→ Kids App stores device token in encrypted prefs│
│                                                                 │
│  5. Parent assigns a child profile to the paired device         │
│     (via web dashboard → Devices)                               │
│                                                                 │
│  Note: The Spotify app on the kids' device must be logged in    │
│  with the child's own Spotify account (done once outside        │
│  Samsung Kids as part of device setup). The App Remote SDK      │
│  controls this already-logged-in Spotify session.               │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                    ONGOING OPERATION                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Dashboard access: email + password → session cookie            │
│  Kids App: device token (JWT) for all backend calls             │
│  Backend uses stored family Spotify token for content API calls │
│  Spotify App Remote SDK on kids' device handles local playback  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

This means:
- Dashboard login is independent of Spotify — a lapsed Spotify subscription doesn't lock parents out
- Multiple parents can have dashboard access without needing Spotify accounts
- Children never see Spotify credentials
- Token refresh happens server-side automatically
- If Spotify token expires, backend handles re-auth transparently
- Device token is a long-lived JWT scoped to a single device + family

---

## 3. Tech Stack

### 3.1 Kids App (Android)

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Language | **Kotlin 2.x** | Modern, concise, null-safe, official Android language |
| UI | **Jetpack Compose** | Declarative UI, excellent for large visual layouts, state-of-the-art |
| Architecture | **MVI (Model-View-Intent)** | Unidirectional data flow, highly testable |
| DI | **Hilt** | Standard DI for Android, integrates with ViewModel |
| Networking | **Ktor Client** | Kotlin-native, coroutine-based, multiplatform-ready |
| Local DB | **Room** | SQLite abstraction, offline cache for allowed content |
| Image Loading | **Coil 3** | Kotlin-first, Compose-native, lightweight |
| Navigation | **Compose Navigation** | Type-safe, single-activity architecture |
| Spotify | **Spotify App Remote SDK** (playback control) | See §2.2 |
| Testing | **JUnit 5 + Turbine + MockK** | Modern test stack for coroutines/flows |
| UI Testing | **Compose Test** + **Robolectric** | Fast UI tests without emulator |

### 3.2 Backend

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| Framework | **Spring Boot 4.0.4 / Java 21** | Latest major release (Spring Framework 7), enhanced observability, GraalVM-ready |
| API | **Spring WebFlux** (REST + WebSocket) | Reactive for real-time approval notifications |
| Web UI | **Thymeleaf** (reactive) + **HTMX 2.x** + **Bootstrap 5** | Server-rendered dashboard with dynamic partial updates, zero JS framework |
| Email | **Spring Mail** (configurable SMTP) | Email notifications for content requests with one-click approve links |
| Database | **MariaDB** (existing instance) | Already running on homeserver – no additional container needed |
| Migration | **Liquibase** | Existing knowledge, XML/YAML changelog format |
| Auth | **Spring Security 7** + custom device tokens (JWT) + session cookies (web) | Dual auth: JWT for API, session for web dashboard |
| Spotify Client | **Spring WebClient** | Async HTTP for Spotify Web API calls |
| Caching | **Caffeine** | In-memory cache for Spotify metadata (album art, track lists) |
| Build | **Gradle (Kotlin DSL)** | Consistent with Android projects, single build tool across monorepo |
| Testing | **JUnit 5 + Testcontainers + MockWebServer** | Integration tests with real MariaDB |

### 3.3 Infrastructure

| Component | Technology |
|-----------|-----------|
| Container Runtime | Docker + Docker Compose |
| Reverse Proxy | Traefik (existing) with TLS |
| CI/CD | Jenkins (existing) with path-based pipeline triggers |
| Monitoring | Grafana Loki (existing) for log aggregation |

---

## 4. Data Model

### 4.1 Core Entities

```
┌─────────────────┐       ┌──────────────────────┐
│ Family           │       │ ChildProfile              │
├─────────────────┤       ├───────────────────────────┤
│ id (PK)         │──┐    │ id (PK)                   │
│ email (UNIQUE)  │  │    │ family_id (FK)            │
│ password_hash   │  │    │ name                      │
│ spotify_user_id │  │    │ avatar_icon               │
│  (nullable)     │  │    │ avatar_color              │
│ spotify_refresh_ │  │    │ age_group (ENUM:          │
│   token (encr., │  │    │   TODDLER 0-3,            │
│   nullable)     │  │    │   PRESCHOOL 4-6,          │
│ notification_   │  │    │   SCHOOL 7-12)            │
│   emails (TEXT) │  │    │ spotify_user_id (nullable)│  ← child's own Spotify account
│ created_at      │  │    │ spotify_refresh_token     │  ← child's OAuth token (encrypted)
└─────────────────┘  │    │   (TEXT, nullable)        │
                     │    │   SCHOOL 7-12)            │
                     │    │ spotify_user_id (nullable)│  ← child's own Spotify account
                     │    │ spotify_refresh_token     │  ← child's OAuth token (encrypted)
                     │    │   (TEXT, nullable)        │
                     │    │ created_at                │
                     └───>│ updated_at                │
                          └───────────────────────────┘
                               │
                     ┌─────────┼──────────────┐
                     ▼         ▼              ▼
          ┌──────────────────┐ │   ┌──────────────────────┐
          │ AllowedContent   │ │   │ Favorite             │
          ├──────────────────┤ │   ├──────────────────────┤
          │ id (PK)          │ │   │ id (PK)              │
          │ profile_id (FK)  │ │   │ profile_id (FK)      │
          │ spotify_uri      │ │   │ spotify_track_uri    │
          │ content_type     │ │   │ track_title          │
          │  (MUSIC/         │ │   │ track_image_url      │
          │   AUDIOBOOK/     │ │   │ artist_name          │
          │   MIXED)         │ │   │ added_at             │
          │ scope (TRACK/    │ │   └──────────────────────┘
          │   ALBUM/PLAYLIST/│ │
          │   ARTIST)        │ │   ┌──────────────────────┐
          │ title            │ │   │ ContentRequest       │
          │ image_url        │ │   ├──────────────────────┤
          │ artist_name      │ │   │ id (PK)              │
          │ cached_metadata  │ └──>│ profile_id (FK)      │
          │  (JSON)          │     │ spotify_uri          │
          │ added_by         │     │ content_type         │
          │ created_at       │     │ title                │
          └──────────────────┘     │ image_url            │
                                   │ artist_name          │
          ┌──────────────────┐     │ status (ENUM:        │
          │ PairedDevice     │     │   PENDING/APPROVED/  │
          ├──────────────────┤     │   REJECTED)          │
          │ id (PK)          │     │ requested_at         │
          │ family_id (FK)   │     │ resolved_at          │
          │ device_token_hash│     │ resolved_by          │
          │ device_name      │     │ parent_note          │
          │ device_type      │     │ approve_token (UUID) │  ← one-time email link token
          │  (KIDS/PARENT)   │     │ digest_sent_at       │
          │ profile_id (FK)  │     └──────────────────────┘
          │  (KIDS/PARENT)   │
          │ profile_id (FK)  │
          │  (null for PARENT│
          │   devices)       │
          │ last_seen_at     │
          │ created_at       │
          └──────────────────┘
```

**Key design decision: AllowedContent is per-profile, not per-family.** Each child has their own whitelist. This enables age-appropriate content separation – a 7-year-old can listen to "Die drei ???" while a 3-year-old only has "Peppa Pig". When a parent adds content, they explicitly choose which profile(s) to add it to. A convenience "add to all profiles" option avoids repetitive work for shared content.

**Note on MariaDB and JSON:** MariaDB supports the `JSON` column type (stored as `LONGTEXT` with validation). The `cached_metadata` field uses this for flexible storage of Spotify API responses (track lists, genres, etc.) that don't warrant dedicated columns.

**Backend-side resolved content tables:** In addition to the core entities above, the backend maintains pre-resolved content for efficient sync delivery:

```
          ┌──────────────────┐
          │ AllowedContent   │
          │ (core entity)    │
          └──────────┬───────┘
                     │ 1:N (populated by background resolver)
                     ▼
          ┌──────────────────────┐
          │ ResolvedAlbum        │
          ├──────────────────────┤
          │ id (PK)              │
          │ allowed_content_id   │
          │ spotify_album_uri    │
          │ title                │
          │ image_url            │
          │ release_date         │
          │ total_tracks         │
          │ content_type         │
          │ resolved_at          │
          └──────────┬───────────┘
                     │ 1:N
                     ▼
          ┌──────────────────────┐
          │ ResolvedTrack        │
          ├──────────────────────┤
          │ id (PK)              │
          │ resolved_album_id    │
          │ spotify_track_uri    │
          │ title                │
          │ artist_name          │
          │ duration_ms          │
          │ track_number         │
          │ disc_number          │
          │ image_url            │
          └──────────────────────┘
```

These tables are populated asynchronously by a background job whenever content is added or during daily re-resolution (see §6.1 Sync endpoints). The sync endpoint reads from these tables to build the complete content tree delivered to kids' devices.

### 4.2 Content Scope System

The `AllowedContent.scope` field is the key to the "allow Bibi & Tina" requirement:

| Scope | spotify_uri example | Behavior |
|-------|-------------------|----------|
| `TRACK` | `spotify:track:abc123` | Single track allowed |
| `ALBUM` | `spotify:album:abc123` | All tracks in album allowed |
| `PLAYLIST` | `spotify:playlist:abc123` | All tracks in playlist allowed (re-synced periodically) |
| `ARTIST` | `spotify:artist:abc123` | **All content by this artist** allowed (albums, singles, appears-on, future releases) |

**Resolution algorithm** – when the Kids App (via backend) checks whether a track is playable for a specific child profile:

```
isAllowed(trackUri, profileId):
  1. SELECT * FROM allowed_content WHERE profile_id = ? AND spotify_uri = trackUri
     → if found: ALLOWED (direct track match)

  2. Resolve track's album URI via Spotify API (cached)
     SELECT * FROM allowed_content WHERE profile_id = ? AND spotify_uri = albumUri
     → if found: ALLOWED (album scope)

  3. Resolve track's artist URIs via Spotify API (cached)
     SELECT * FROM allowed_content WHERE profile_id = ? AND spotify_uri IN (artistUris)
     → if found: ALLOWED (artist scope)

  4. For each allowed playlist (scope = PLAYLIST) for this profile:
     Check if track is in playlist's track list (cached, refreshed every 6h)
     → if found: ALLOWED (playlist scope)

  5. → DENIED
```

**Performance note:** Steps 2-4 involve Spotify API calls, but all responses are cached in Caffeine (in-memory) with TTLs:
- Track → album/artist mapping: cached 24h (rarely changes)
- Artist → album list: cached 6h (new releases appear within a day)
- Playlist → track list: cached 6h (playlists are mutable)
- Search results: cached 1h

### 4.3 Content Type Classification

| content_type | Description | UI Treatment in Kids App |
|-------------|-------------|--------------------------|
| `MUSIC` | Songs, singles, music albums | Shown in "Music" tab (blue) |
| `AUDIOBOOK` | Audiobooks, Hörspiele, spoken word content | Shown in "Audiobooks" tab (green) |
| `MIXED` | Playlists containing both types | Shown in both tabs |

**Detection heuristic** – applied automatically when content is added, with manual parent override:

```
classifyContent(spotifyItem):
  // Strong signals (high confidence)
  if item.type == "audiobook":           → AUDIOBOOK
  if item.genres contains "hörspiel":    → AUDIOBOOK
  if item.genres contains "audiobook":   → AUDIOBOOK
  if item.genres contains "spoken word": → AUDIOBOOK

  // Medium signals (need corroboration)
  if item.album.total_tracks > 20 AND average_track_duration > 5min:
    → AUDIOBOOK (likely a multi-chapter audiobook)

  if item.album.name matches /Folge \d+|Episode \d+|Teil \d+|Kapitel \d+/:
    → AUDIOBOOK (common German audiobook naming pattern)

  // Children's music signal
  if item.genres contains any of ["children's music", "kindermusik", "kinderlieder"]:
    → MUSIC

  // Default
  → MUSIC (safer default – parent can reclassify)
```

The parent can always override the classification in the Parent App. The override is stored alongside the AllowedContent entry and takes precedence over the heuristic.

---

## 5. App Specifications

### 5.1 KidsTune Kids App

#### 5.1.1 Screen Flow

```
                               FIRST LAUNCH ONLY:
┌─────────────┐    ┌─────────────┐
│   Pairing    │───>│   Profile   │──┐
│   (enter     │    │   Selection │  │
│    code)     │    │   (one-time)│  │
└─────────────┘    │             │  │
                   │  [🐻 Luna]  │  │
                   │  [🦊 Max ]  │  │
                   └─────────────┘  │
                                    │
                   EVERY LAUNCH: ◄──┘
                   ┌──────────────────────────┐    ┌──────────────────┐
                   │   Home                   │───>│  Category View   │
                   │                          │    │  (paginated grid)│
                   │  ┌────────┐ ┌──────────┐ │    │                  │
                   │  │  🎵    │ │  📖      │ │    │ ┌──────┐┌──────┐│
                   │  │ Music  │ │Audiobooks│ │    │ │ img  ││ img  ││
                   │  └────────┘ └──────────┘ │    │ │      ││      ││
                   │      ┌──────────┐        │    │ └──────┘└──────┘│
                   │      │  ❤️      │        │    │ ┌──────┐┌──────┐│
                   │      │Favorites │        │    │ │ img  ││ img  ││
                   │      └──────────┘        │    │ │      ││      ││
                   │                          │    │ └──────┘└──────┘│
                   │  ┌──────────────────┐    │    │                  │
                   │  │ 🔍 Discover*     │    │    │  ◄ Page 1 of 3 ►│
                   │  └──────────────────┘    │    └──────┬───────────┘
                   └──────────────────────────┘           │
                                                          ▼
                                               ┌──────────────────┐
                                               │  Now Playing     │
                                               │                  │
                                               │  ┌────────────┐  │
                                               │  │            │  │
                                               │  │  [large    │  │
                                               │  │   cover    │  │
                                               │  │   art]     │  │
                                               │  │            │  │
                                               │  └────────────┘  │
                                               │                  │
                                               │   ◄◄  ▶/❚❚  ►►  │
                                               │                  │
                                               │  ━━━━━━●━━━━━━━  │
                                               │  1:23    / 3:45  │
                                               │                  │
                                               │  [❤️ Favorite]   │
                                               └──────────────────┘

                                               * Bonus feature (§5.1.6)
```

#### 5.1.2 UI Design Principles

- **Minimum touch target: 72dp** (larger than standard 48dp – designed for small hands on a phone screen)
- **No text-only navigation** – every interactive element has an icon or image
- **Color-coded sections:**
  - Music = blue/purple tones with 🎵 icon
  - Audiobooks = green/teal tones with 📖 icon
  - Favorites = pink/red tones with ❤️ icon
- **Profile avatars:** Simple colored animal icons (bear, fox, bunny, owl, cat, penguin) – no text names needed, each profile gets a unique color + animal combination
- **No infinite scrolling** – paginated grids with large left/right swipe arrows. Each page shows 4-6 items (2x2 or 2x3 grid). Page indicator dots at the bottom.
- **Now Playing always accessible** via persistent mini-player bar at bottom of every screen (shows current cover art thumbnail + play/pause button)
- **No loading spinners** – show cached artwork immediately with a subtle shimmer animation, replace with fresh data when network response arrives
- **Large typography** – minimum 18sp for any visible text, but text is supplementary (never the primary navigation mechanism)
- **Haptic feedback** on button presses – satisfying for kids, confirms interaction registered
- **No confirmation dialogs** – adding a favorite is instant (tap heart), removing requires long-press (prevents accidental removal)

#### 5.1.3 Profile Binding (One-Time Setup)

Each kids' device is permanently bound to a single child profile. The profile is selected once during initial setup and cannot be changed by the child.

```
First App Launch (after pairing)
  │
  ├─ Shows all child profiles as large, colorful avatar tiles
  │   e.g., [🐻 Luna] [🦊 Max]
  │
  ├─ Child (or parent) taps their profile
  │
  ├─ Confirmation: "Is this [🐻 Luna]'s device?" [Yes] [No]
  │
  ├─ Profile binding stored in EncryptedSharedPreferences
  │   AND registered on backend (PairedDevice.profile_id)
  │
  └─ From now on: app launches directly to Home screen
     └─→ No profile selector shown again
     └─→ Profile can only be reassigned via the Web Dashboard
         (Web Dashboard → Devices → [device] → Reassign Profile)
```

**Why one-time binding?**
- Each child has their own device (repurposed old smartphone)
- Content whitelists are per-profile – showing a profile selector would let one child browse another child's content
- Eliminates confusion for younger children
- Simpler UX: pick up device → immediately in your content

**What if you need to reassign?** The Parent App has a "Reassign Profile" option in the device management screen. This clears the local profile binding, favorites cache, and offline queue on the next sync, then prompts for profile selection again on the kids' device.

#### 5.1.4 App Containment via Samsung Kids

The Kids App runs inside **Samsung Kids** on the repurposed Samsung smartphone. Samsung Kids already provides:
- App whitelisting (only KidsTune Kids is visible to the child)
- No access to settings, Play Store, notifications, or other apps
- Parental PIN to exit Samsung Kids
- Daily usage time limits (configurable by parents in Samsung Kids settings)

**Spotify as background service:** Spotify must be installed for the App Remote SDK, but it is NOT added to Samsung Kids' allowed apps list. It runs as a background process only, controlled entirely by KidsTune Kids via the SDK.

**Samsung Kids setup requirements:**
1. Install both Spotify and KidsTune Kids on the device
2. Log in to Spotify once (outside Samsung Kids) for the App Remote SDK auth
3. Open Samsung Kids settings → Add KidsTune Kids as the only allowed app
4. Do NOT add Spotify, Chrome, or any other app
5. Set the Samsung Kids PIN
6. Optionally configure daily time limits

**Known Samsung Kids containment gap – Spotify media notification bypass:**

When Samsung Kids' time limit expires, it shows a lock screen but on many One UI versions does not fully block the notification shade. The Spotify app registers its own media notification (the track player visible when swiping down), and tapping it fires a `PendingIntent` that can open Spotify directly — bypassing Samsung Kids' app whitelist.

KidsTune closes this gap by registering its own `MediaSession` / `MediaBrowserService`:
- KidsTune's MediaSession mirrors Spotify's playback state (track metadata, play/pause, position) by subscribing to `SpotifyAppRemote.PlayerApi.subscribeToPlayerState()`
- Android shows KidsTune's media notification instead of Spotify's
- Tapping the notification opens KidsTune (an allowed app), not Spotify
- When a future usage time limit expires, KidsTune can dismiss its own notification entirely

**Implementation notes:**
- Use `androidx.media3` MediaSession (not the deprecated `MediaSessionCompat`)
- KidsTune does NOT manage audio focus — Spotify still owns playback; KidsTune only mirrors state
- Track artwork must be downloaded as a `Bitmap` asynchronously on track change (use Coil)
- Playback position is calculated locally (`snapshotPosition + elapsedTime`) for smooth seek bar
- On App Remote disconnect: set MediaSession to `STATE_STOPPED` and clear metadata

**Fallback for non-Samsung devices:** The app includes an optional Lock Task Mode implementation that can be activated via ADB for devices without Samsung Kids:
```bash
# One-time setup (requires factory reset):
adb shell dpm set-device-owner com.kidstune.kids/.KidsTuneAdmin
```
This is low-priority but keeps the door open for running on non-Samsung devices.

#### 5.1.5 Offline Support

**Core principle:** The Kids App must be **fully functional without any internet connection**. Children use these devices at home, in the car, at grandparents' houses – connectivity is not guaranteed. Every tap must work offline.

**Pre-resolved content tree:** The backend doesn't just sync "Bibi & Tina is allowed" – it pre-resolves all content into concrete, playable data and sends the complete tree to the Kids App during sync. The local Room DB holds everything needed for browsing AND playback.

**Local Room DB schema (Kids App only – not the backend schema):**

```
┌──────────────────────┐
│ LocalContentEntry    │  ← Top-level tiles (what kids see in the grid)
├──────────────────────┤
│ id (PK)              │
│ profile_id           │
│ spotify_uri          │  ← e.g., spotify:artist:xyz or spotify:album:abc
│ scope                │  ← ARTIST / ALBUM / PLAYLIST / TRACK
│ content_type         │  ← MUSIC / AUDIOBOOK
│ title                │  ← "Bibi & Tina"
│ image_url            │  ← cover art URL (Coil caches the image on disk)
│ artist_name          │
│ last_synced_at       │
└──────────┬───────────┘
           │ 1:N
           ▼
┌──────────────────────┐
│ LocalAlbum           │  ← Albums within an artist or playlist grouping
├──────────────────────┤
│ id (PK)              │
│ content_entry_id (FK)│
│ spotify_album_uri    │
│ title                │  ← "Bibi & Tina - Folge 1"
│ image_url            │
│ release_date         │
│ total_tracks         │
│ content_type         │  ← MUSIC / AUDIOBOOK (per album, may differ from parent)
└──────────┬───────────┘
           │ 1:N
           ▼
┌──────────────────────┐
│ LocalTrack           │  ← Individual playable tracks with Spotify URIs
├──────────────────────┤
│ id (PK)              │
│ album_id (FK)        │
│ spotify_track_uri    │  ← spotify:track:abc123 ← THIS is what the SDK needs
│ title                │  ← "Kapitel 1 - Bibi trifft Tina"
│ artist_name          │
│ duration_ms          │
│ track_number         │
│ disc_number          │
│ image_url            │  ← track-level art (falls back to album art)
└──────────────────────┘

┌──────────────────────┐
│ LocalFavorite        │  ← per-profile favorites, synced bidirectionally
├──────────────────────┤
│ id (PK)              │
│ profile_id           │
│ spotify_track_uri    │
│ title                │
│ artist_name          │
│ image_url            │
│ added_at             │
│ synced (BOOLEAN)     │  ← false = queued for upload on next sync
└──────────────────────┘

┌──────────────────────┐
│ LocalPlaybackPosition│  ← one row per profile; resume where you left off
├──────────────────────┤
│ profile_id (PK)      │  ← single row per profile (upsert on every update)
│ context_uri          │  ← spotify:album:xxx or spotify:artist:xxx (playback context)
│ track_uri            │  ← spotify:track:xxx of the currently/last playing chapter
│ track_index          │  ← 0-based index within the context (for SDK skipToIndex)
│ position_ms          │  ← playback position within that chapter
│ updated_at           │
└──────────────────────┘
```

**What this means in practice:**

When the parent adds "Bibi & Tina" as an ARTIST to Luna's profile, the backend:
1. Calls Spotify Web API to fetch all albums for that artist
2. For each album, fetches all tracks
3. Stores the metadata in its own cache (Caffeine + DB)
4. On next sync, sends the entire resolved tree to Luna's device:
   - 1 ContentEntry: "Bibi & Tina" (artist)
   - 48 Albums: "Folge 1", "Folge 2", ..., "Movie Soundtrack"
   - ~520 Tracks: each with its `spotify:track:...` URI

The Kids App stores all of this in Room. When Luna taps "Bibi & Tina" → "Folge 1" → track plays – **the entire flow uses only local data**. The Spotify App Remote SDK receives the `spotify:track:abc123` URI and plays it from Spotify's own offline cache.

**Data volume estimate:**

| Content | Approx. Size in Room |
|---------|---------------------|
| 1 ContentEntry | ~0.5 KB |
| 1 Album | ~0.3 KB |
| 1 Track | ~0.2 KB |
| 10 artists × 20 albums × 15 tracks | ~65 KB (entries) + ~60 KB (albums) + ~60 KB (tracks) ≈ **185 KB** |
| Cover art images (Coil disk cache) | ~50-200 MB depending on count |

This is negligible storage – even 1000 artists would only be ~18 MB of metadata.

**Complete offline data flow:**

```
┌─────────────────────────────────────────────────────────────────┐
│                     OFFLINE PLAYBACK FLOW                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Kid taps "Bibi & Tina" tile                                    │
│  └─→ Room query: SELECT * FROM LocalAlbum                       │
│      WHERE content_entry_id = ? ORDER BY release_date           │
│      └─→ Shows album grid (art from Coil disk cache)            │
│                                                                 │
│  Kid taps "Folge 1" album                                       │
│  └─→ Room query: SELECT * FROM LocalTrack                       │
│      WHERE album_id = ? ORDER BY disc_number, track_number      │
│      └─→ Shows track list (or starts playing immediately)       │
│                                                                 │
│  Kid taps play (or it auto-plays)                               │
│  └─→ Spotify App Remote SDK: play(spotify:track:abc123)         │
│      └─→ Spotify app plays from its local offline cache         │
│          └─→ No internet needed at any step                     │
│                                                                 │
│  Kid taps ❤️ to favorite                                        │
│  └─→ Room INSERT into LocalFavorite (synced = false)            │
│      └─→ Next time online: WorkManager uploads to backend       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**What happens if Spotify hasn't cached the audio?**

The Spotify App Remote SDK will attempt to stream – if there's no connection, Spotify shows its own "offline" state. To mitigate this:
- Parents should periodically open Spotify on the kids' device (outside Samsung Kids) and download key playlists/albums for offline use
- The Parent App setup guide includes this as a recommended step
- Future enhancement: the backend could generate a Spotify playlist per profile containing all allowed tracks, which parents can then download with one tap in Spotify

**Caching strategy summary:**

| Data | Where | Survives Offline | Update Strategy |
|------|-------|-----------------|-----------------|
| Content tree (entries, albums, tracks) | Room DB | ✅ Yes | Full/delta sync when online |
| Cover art images | Coil disk cache (LRU, 200 MB) | ✅ Yes (if previously viewed) | Cached on first view, evicted by LRU |
| Favorites | Room DB | ✅ Yes (queued for sync) | Bidirectional sync when online |
| Content requests | Room DB | ✅ Yes (queued for upload) | Uploaded when online |
| Audio files | Spotify's internal cache | ✅ Yes (if downloaded/recently played) | Managed by Spotify |
| Playback queue + position | Room DB | ✅ Yes | Local only, not synced |

**Sync strategy:**

```
App Launch (or WorkManager periodic trigger every 15 min)
  │
  ├─→ Online?
  │     ├─→ Yes: Attempt delta sync
  │     │     GET /api/v1/sync/{profileId}/delta?since={lastSyncTimestamp}
  │     │     └─→ Response contains:
  │     │         - added[]: new ContentEntries with full album/track trees
  │     │         - updated[]: entries where track list changed (new album released)
  │     │         - removed[]: entries parent deleted
  │     │         └─→ Apply to Room DB transactionally
  │     │
  │     │   If delta fails or first sync ever:
  │     │     GET /api/v1/sync/{profileId}
  │     │     └─→ Full payload: all entries + albums + tracks
  │     │         └─→ Replace entire Room DB in transaction
  │     │
  │     ├─→ Upload queued favorites (synced = false → POST to backend → set synced = true)
  │     ├─→ Upload queued content requests
  │     └─→ Update lastSyncTimestamp
  │
  └─→ Offline?
        └─→ Use local Room DB as-is, everything works
            └─→ WorkManager will retry sync when connectivity returns
```

#### 5.1.6 Discover & Request Feature (Bonus)

The Discover screen allows children to search the full Spotify catalog, but NOT play anything directly. Instead, they request content and wait for parental approval.

```
┌────────────────────────────────────┐
│  🔍 [Search box with               │
│      voice input button]           │
│                                    │
│  Search results:                   │
│  ┌──────────────────────────┐      │
│  │ [img] Frozen OST         │      │
│  │     [🙏 Request]         │      │
│  └──────────────────────────┘      │
│  ┌──────────────────────────┐      │
│  │ [img] Frozen 2 OST       │      │
│  │     [🙏 Request]         │      │
│  └──────────────────────────┘      │
│                                    │
│  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐  │
│  │  My wishes (2 of 3):        │  │
│  │  ┌──────────────────────┐   │  │
│  │  │ [img] Moana OST      │   │  │
│  │  │   Mama/Papa schauen   │   │  │
│  │  │   sich das an 🕐      │   │  │
│  │  └──────────────────────┘   │  │
│  │  ┌──────────────────────┐   │  │
│  │  │ [img] Cars 3 OST     │   │  │
│  │  │   Gestern gewünscht  │   │  │
│  │  │   🕐                  │   │  │
│  │  └──────────────────────┘   │  │
│  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘  │
│                                    │
│  ┌──────────────────────────┐      │
│  │ [img] Lego Movie OST     │      │
│  │   ❌ Nicht erlaubt        │      │
│  │   (rejected, fades after  │      │
│  │    24h)                   │      │
│  └──────────────────────────┘      │
└────────────────────────────────────┘
```

**Search is intentionally limited:**
- Results are filtered to exclude explicit content (Spotify `explicit` flag)
- Video content is stripped from results
- Maximum 10 results per search to prevent endless browsing
- Search rate-limited to 1 query per 5 seconds (prevents rapid-fire searches)

**Voice search:** The search box includes a microphone button using Android's built-in `SpeechRecognizer`. This is important for pre-readers who cannot type.

**Request limits and queue behavior:**
- Maximum **3 pending requests** per profile at any time. Once the limit is reached, the Request button is disabled with a friendly message: "Du hast schon 3 Wünsche offen – warte bis Mama/Papa geantwortet hat!"
- Approved requests don't count against the limit (they become playable content)
- Rejected requests show briefly with ❌ and a kid-friendly reason (if parent provided a note), then fade away after 24h

**Pending request UX (designed for patience):**
- Pending requests are shown in a **separate "My wishes" section** below search results, not in the main content grid. This prevents the child from constantly being reminded.
- No spinner – instead, a friendly, static clock icon 🕐 with age-appropriate time context:
  - < 1 hour: "Mama/Papa schauen sich das an"
  - 1-24 hours: "Gestern gewünscht"
  - \> 24 hours: "Vor ein paar Tagen gewünscht"
- The Discover screen is **not the home screen** – children navigate to it explicitly. Most of the time they're in the Music/Audiobooks tabs listening to already-approved content.
- When a request is approved, the child sees a **celebration animation** (confetti + sound) the next time they open the app or when the WebSocket push arrives while the app is open. The new content tile pulses with a "NEW" badge for 24h.

**Auto-expiry:**
- Requests that remain `PENDING` for **7 days** are automatically set to `EXPIRED` by a daily backend job
- Expired requests disappear from the kids' UI silently (no sad message – it just goes away)
- Parents see expired requests in their history with an "Expired – not reviewed" tag
- The child can re-request the same content (it creates a new request)

#### 5.1.7 Sequential & Continuous Playback (Audiobooks)

Audiobooks on Spotify are structured as albums with many tracks – one per chapter (e.g., "Bibi & Tina – Folge 1" has 12 chapters, each a separate `spotify:track:...` URI). Children expect the same experience they get in Spotify: finish chapter 1, chapter 2 starts automatically.

**The core rule:** Never play a bare `spotify:track:...` URI. Always play the **album as a context**. The Spotify App Remote SDK plays the whole album in order and handles auto-advance natively.

```
// WRONG – plays only this one chapter, stops after
PlayerApi.play("spotify:track:abc123")

// CORRECT – plays the album from chapter N, auto-advances through all chapters
PlayerApi.play("spotify:album:xyz456")         // starts from chapter 1
PlayerApi.skipToIndex("spotify:album:xyz456", trackIndex)  // resume at chapter N
```

When a child taps a chapter tile, the `PlaybackController` calls `skipToIndex(albumUri, trackIndex)` – this positions playback at the selected chapter within the album context, so skip-forward/back and auto-advance all work correctly.

**Chapter list UI:**

For AUDIOBOOK albums, the browse screen shows a vertical chapter list instead of a 2×2 tile grid:

```
┌──────────────────────────────────────────────────┐
│  [cover art]  Bibi & Tina – Folge 1               │
│               12 Kapitel  •  ca. 58 min            │
├──────────────────────────────────────────────────┤
│  ▶  Kapitel 1 – Bibi trifft Tina       5:12      │  ← resume indicator (▶) on last-played chapter
│     Kapitel 2 – Das Pferd              4:55      │
│     Kapitel 3 – Die Wette              6:01      │
│     ...                                          │
└──────────────────────────────────────────────────┘
```

- Chapters are ordered by `disc_number ASC, track_number ASC`
- The last-played chapter (from `LocalPlaybackPosition`) is highlighted with a ▶ resume indicator and the saved `position_ms` shown as a small progress bar under the title
- Tapping any chapter calls `PlaybackController.playFromChapter(albumUri, trackIndex)` which calls `skipToIndex`
- Tapping the ▶ resume chapter restores exactly to the saved position via `seekTo(position_ms)`
- MUSIC albums continue to show the 2×2 tile grid (no chapter list)

**Playback position persistence:**

`PlaybackController` observes Spotify's `PlayerState` updates and writes to `LocalPlaybackPosition` at most once every 5 seconds (throttled), plus on pause and app background. This allows resuming within a chapter.

On app start, `PlayerViewModel` reads `LocalPlaybackPosition` for the current profile and:
- Shows the mini-player bar populated with the last-played content (even if Spotify is no longer playing it)
- Offers a "Resume" tap target on the content tile and in the chapter list

**NowPlaying screen for chapters:**

When `content_type == AUDIOBOOK`, the NowPlaying screen shows the chapter context instead of generic track info:

```
┌──────────────────────────────────────┐
│  [large cover art]                   │
│                                      │
│  Bibi & Tina – Folge 1               │  ← album title
│  Kapitel 3 von 12                    │  ← chapter N of total (from LocalAlbum.total_tracks)
│  Kapitel 3 – Die Wette               │  ← track title
│  ───────────────────────────────     │
│  [◀◀]  [⏸]  [▶▶]                    │  ← skip goes to prev/next chapter
└──────────────────────────────────────┘
```

The `NowPlayingState` is extended with `chapterIndex: Int?` and `totalChapters: Int?`, populated by looking up the currently playing track URI in the `LocalTrack` Room table to retrieve `track_number` and the parent album's `total_tracks`.

---

### 5.2 KidsTune Web Dashboard

#### 5.2.1 Page Structure

The web dashboard is a responsive Thymeleaf + HTMX + Bootstrap 5 app served at `/web/**` by the Spring Boot backend. It uses session-based auth (email + password, with optional "Remember me" persistent cookie) and is mobile-friendly. Spotify OAuth is a separate "Connect Spotify" step in Settings — not the login mechanism.

```
/web/login            → Email + password login page (with "Remember me" checkbox)
/web/register         → First-time registration page (email, password, confirm password)
/web/dashboard        → Overview: pending count badge, profile cards, recent activity
/web/profiles         → List, create, edit, delete child profiles
/web/profiles/{id}/content → Content per profile (list, search/add, remove)
/web/requests         → Approval queue (PENDING / APPROVED / REJECTED / EXPIRED tabs)
/web/import           → Import wizard (per-profile, uses child's own Spotify history)
/web/devices          → Paired devices list, generate pairing code, reassign profile
/web/approve/{token}  → One-click approve link from email (public, no login required)
/web/settings         → Notification emails; connect/disconnect parent Spotify account
/web/admin/**         → Admin CRUD tables for all entities (operational oversight)
```

Sidebar navigation with links to all sections. Left sidebar collapses to icons on small screens. All destructive actions use HTMX confirmation modals.

```
┌────────────────────────────────────────────────────────────────┐
│  KidsTune Dashboard                          [Logout] [Family] │
├──────────────┬─────────────────────────────────────────────────┤
│  Dashboard   │  🔔 3 pending content requests                  │
│  Profiles    │  ──────────────────────────────────────────────  │
│  Requests    │  Profiles:                                      │
│  Import      │  [🐻 Luna – 12 items]   [🦊 Max – 34 items]    │
│  Devices     │                                                 │
│  Admin ▸     │  Recent activity:                               │
│              │  • Bibi & Tina added for Luna (2 min ago)       │
│              │  • Luna requested Frozen OST (5 min ago)        │
└──────────────┴─────────────────────────────────────────────────┘
```

The approval queue shows pending requests with inline approve/reject buttons (HTMX):

```
┌────────────────────────────────────────────────────────────────┐
│  Pending Requests (3)   [Approve All]                          │
│  ──────────────────────────────────────────────────────────── │
│  [img] Frozen OST               🐻 Luna  •  2 minutes ago     │
│        [✓ Approve for Luna]  [✓ For all children]  [✗ Reject] │
│  ──────────────────────────────────────────────────────────── │
│  [img] Moana OST                🦊 Max   •  15 minutes ago    │
│        [✓ Approve for Max]   [✓ For all children]  [✗ Reject] │
└────────────────────────────────────────────────────────────────┘
```
         │
#### 5.2.2 Initial Import Flow

To ease migration from existing Spotify usage, the web dashboard includes an import wizard. Since each child has their own Spotify account, the import is **per-profile** — each child's own listening history is fetched using their linked Spotify account.

**Prerequisite:** The child's Spotify account must be linked to their profile (step 2b in §2.4) before import can run. Profiles without a linked Spotify account show a "Link Spotify Account first" prompt.

```
1. Parent opens /web/import in browser
2. Parent selects a profile to import for:
   ┌─────────────────────────────────────┐
   │  Import for which profile?          │
   │  ○ 🐻 Luna (Spotify linked ✓)      │
   │  ○ 🦊 Max  (Spotify linked ✓)      │
   │  [Continue →]                       │
   └─────────────────────────────────────┘

   (One profile at a time — each child's own history is separate)

3. Backend calls Spotify Web API using CHILD's profile token:
   - GET /v1/me/player/recently-played (last 50 tracks)
   - GET /v1/me/playlists (all user playlists)
   - GET /v1/me/top/artists?time_range=medium_term (top 50 artists)
   - GET /v1/me/top/artists?time_range=long_term (all-time top artists)

4. Backend groups and deduplicates results, applies children's content heuristic

5. App displays results for the selected profile (e.g., Luna):

   ┌─────────────────────────────────────────────────────────────┐
   │  Import from 🐻 Luna's Listening History                     │
   │                                                              │
   │  Detected children's content:                      [pre-✓]  │
   │  ┌─────────────────────────────────────────────────────┐    │
   │  │ ☑ Bibi & Tina              (23 plays)               │    │
   │  │   → Artist (all content)   [add to Luna ✓]          │    │
   │  │                                                      │    │
   │  │ ☑ Die drei ??? Kids        (15 plays)               │    │
   │  │   → Artist (all content)   [add to Luna ✓]          │    │
   │  │                                                      │    │
   │  │ ☑ Rolf Zuckowski           (8 plays)                │    │
   │  │   → Artist (all content)   [add to Luna ✓]          │    │
   │  └─────────────────────────────────────────────────────┘    │
   │                                                              │
   │  Luna's playlists with kids content:                        │
   │  ┌─────────────────────────────────────────────────────┐    │
   │  │ ☑ "Einschlaflieder" (18 tracks)                      │    │
   │  │ ☐ "Road Trip Mix" (52 tracks)                        │    │
   │  └─────────────────────────────────────────────────────┘    │
   │                                                              │
   │  [Import selected items →]                                   │
   └─────────────────────────────────────────────────────────────┘

6. Selected items are added as AllowedContent per-profile:
   - An AllowedContent row is created for EACH selected profile
   - Artist selections → ARTIST scope (broadest, auto-includes future releases)
   - Playlist selections → PLAYLIST scope
```

**Per-profile import:** Since each profile imports from its own Spotify account, the results naturally reflect that child's actual listening taste. The age-based heuristic is still applied as a soft hint — items below the child's age group are pre-deselected but still visible. Parents run the import once per profile.

**Smart age-based pre-selection:** The heuristic applies `known_children_artists` age ranges. Items well above the profile's age group are pre-deselected (but visible). This is based on the `known_children_artists` config:
```yaml
known_children_artists:
  - name: "Bibi & Tina"
    min_age: 3
  - name: "Bibi Blocksberg"
    min_age: 3
  - name: "Benjamin Blümchen"
    min_age: 2
  - name: "Die drei ??? Kids"
    min_age: 6
  - name: "Die drei ???"
    min_age: 10
  - name: "Rolf Zuckowski"
    min_age: 0
  - name: "Conni"
    min_age: 3
  - name: "Paw Patrol"
    min_age: 2
  - name: "Peppa Pig"
    min_age: 2
  # ... extensible list
```

#### 5.2.3 Notification System for Content Requests

When a child submits a content request, the backend notifies **all configured parents** via email (guaranteed) and real-time browser push (when dashboard is open).

**Parent email configuration:**

Each `Family` stores a comma-separated list of `notification_emails` (configurable via web dashboard → Settings). Every address in this list receives all notification emails, ensuring both parents in a household are notified.

**Channel 1 – Email (guaranteed delivery):**

The backend sends an email via Spring Mail immediately when a `PENDING` request is created. SMTP is fully configurable via `spring.mail.*` in `application.yml` (Gmail, Mailgun, self-hosted Postfix, etc.).

The email is sent to **all `notification_emails`** for the family and contains:
- Child name + avatar emoji + content title and artist
- One-click **[Approve]** button linking to `/web/approve/{approveToken}`
- **[View in Dashboard]** link to `/web/requests`

The `approve_token` is a UUID stored on `ContentRequest`, generated at creation. `/web/approve/{token}` is **public** (no login required), approves the request, triggers content resolution, and redirects to a confirmation page. The token is single-use; first parent to click wins (subsequent clicks show "already approved"). Token expires after 7 days with the request.

```
Kids App         Backend                    All Parents (email)
   │                │                         │         │
   │ POST /request  │                         │         │
   ├───────────────>│                         │         │
   │                │ Spring Mail             │         │
   │                │ → all notification_emails         │
   │                ├────────────────────────>│─────────┤
   │                │                         │         │
   │                │  parent clicks [Approve]│         │
   │                │<────────────────────────┤         │
   │  WS push       │ approveRequest(token)   │         │
   │<───────────────┤                         │         │
```

**Channel 2 – WebSocket browser push (real-time when dashboard is open):**

When any parent has the web dashboard open, the backend pushes `CONTENT_REQUEST` events via WebSocket, causing the pending badge to update in real time without a page reload.

**Daily digest email (catch-all for forgotten requests):**

A backend cron job at 19:00 sends a summary email to all `notification_emails` if there are `PENDING` requests older than 4 hours where `digest_sent_at IS NULL`. The digest lists all open requests with individual approve links.

```java
@Scheduled(cron = "0 0 19 * * *")
public void sendDailyDigest() {
    // For each family with PENDING requests older than 4 hours
    //   where digest_sent_at IS NULL:
    //   1. Build summary email with all pending requests + approve links
    //   2. Send via Spring Mail to all family.notification_emails
    //   3. Set digest_sent_at = now() on affected requests
}
```

**Deduplication:** Individual request emails are only sent once at creation. The daily digest only fires once per request cycle (tracked via `digest_sent_at`). Dashboard badge always reflects current pending count from DB.

---

## 6. Backend API Specification

### 6.1 REST Endpoints

#### Auth & Pairing
```
POST   /api/v1/auth/spotify/callback          # Spotify OAuth callback, stores tokens
POST   /api/v1/auth/pair                      # Generate 6-digit pairing code (5 min TTL)
POST   /api/v1/auth/pair/confirm              # Kids device submits pairing code → receives device token
POST   /api/v1/auth/device/refresh            # Refresh device token (JWT rotation)
GET    /api/v1/auth/status                    # Check auth status + Spotify token validity
```

#### Profiles
```
GET    /api/v1/profiles                       # List child profiles for family
POST   /api/v1/profiles                       # Create new profile
PUT    /api/v1/profiles/{id}                  # Update profile (name, avatar, age group)
DELETE /api/v1/profiles/{id}                  # Delete profile + cascades: content, favorites, requests
```

#### Content Management (per-profile)
```
GET    /api/v1/profiles/{profileId}/content           # List allowed content for profile
                                                      #   ?type=MUSIC|AUDIOBOOK
                                                      #   ?scope=ARTIST|ALBUM|PLAYLIST|TRACK
                                                      #   ?search=query (filters by title/artist)
POST   /api/v1/profiles/{profileId}/content           # Add allowed content for profile
                                                      #   { spotifyUri, scope, contentTypeOverride? }
POST   /api/v1/content/bulk                           # Add content to multiple profiles at once
                                                      #   { spotifyUri, scope, profileIds[] }
DELETE /api/v1/profiles/{profileId}/content/{id}      # Remove allowed content from profile
GET    /api/v1/profiles/{profileId}/content/check/{uri} # Check if URI is allowed for profile
POST   /api/v1/content/import                         # Batch import from listening history
                                                      #   { items: [{ spotifyUri, scope, profileIds[] }] }
```

#### Spotify Proxy (for Kids App – no direct Spotify auth needed)
```
GET    /api/v1/spotify/search                 # Search Spotify (for Discover feature)
                                              #   ?q=query&limit=10
                                              #   Filters out explicit content
                                              #   Returns lightweight results (no full track lists)
```

Note: The Kids App does NOT call browse/resolve endpoints at runtime. All content is pre-resolved and delivered via the sync endpoints (see below). The only Spotify proxy the Kids App uses is search (for the Discover/request feature, which requires internet anyway).

#### Favorites
```
GET    /api/v1/profiles/{id}/favorites        # Get favorites for profile
POST   /api/v1/profiles/{id}/favorites        # Add favorite { spotifyTrackUri, title, imageUrl }
DELETE /api/v1/profiles/{id}/favorites/{uri}   # Remove favorite (URI is URL-encoded)
```

#### Content Requests
```
POST   /api/v1/content-requests               # Child requests content
                                              #   { profileId, spotifyUri, title, imageUrl }
                                              #   Returns 429 if profile has 3+ PENDING requests
GET    /api/v1/content-requests               # List requests
                                              #   ?status=PENDING|APPROVED|REJECTED|EXPIRED
                                              #   ?profileId=uuid (filter by child)
GET    /api/v1/content-requests/pending/count  # Get pending count per profile (for polling)
                                              #   Returns { profiles: [{ id, name, count }], total }
PUT    /api/v1/content-requests/{id}          # Approve/reject { status, note? }
                                              #   On APPROVED: auto-creates AllowedContent +
                                              #   triggers ContentResolver + notifies kids device
PUT    /api/v1/content-requests/bulk          # Bulk approve/reject { ids[], status, note? }
```

**Request lifecycle:**
```
PENDING ──→ APPROVED ──→ (content created, child notified)
   │
   ├──→ REJECTED ──→ (shown to child for 24h, then hidden)
   │
   └──→ EXPIRED ──→ (auto-set after 7 days by backend cron job,
                      hidden from child, visible in parent history)
```

**Backend scheduled jobs for content requests:**
```java
// Expire stale requests (runs daily at 03:00)
@Scheduled(cron = "0 0 3 * * *")
public void expireStaleRequests() {
    // SET status = EXPIRED WHERE status = PENDING AND requested_at < NOW() - 7 days
    // Notify kids devices via WebSocket: REQUEST_EXPIRED
}

// Daily digest notification (runs daily at 19:00)
@Scheduled(cron = "0 0 19 * * *")
public void sendDailyDigest() {
    // For each family with PENDING requests older than 4 hours:
    //   Build DAILY_DIGEST WebSocket message with summary
    //   Set digest_sent_at flag (so Layer 2 polling can detect it)
}
```

#### Sync (pre-resolved content tree)
```
GET    /api/v1/sync/{profileId}               # Full sync – returns COMPLETE content tree:
                                              #   {
                                              #     profile: { name, avatar, ageGroup },
                                              #     favorites: [ { trackUri, title, imageUrl, ... } ],
                                              #     content: [
                                              #       {
                                              #         entry: { uri, scope, type, title, imageUrl },
                                              #         albums: [
                                              #           {
                                              #             albumUri, title, imageUrl, releaseDate,
                                              #             tracks: [
                                              #               { trackUri, title, artist, durationMs,
                                              #                 trackNumber, discNumber, imageUrl }
                                              #             ]
                                              #           }
                                              #         ]
                                              #       }
                                              #     ],
                                              #     syncTimestamp: "2026-03-22T14:30:00Z"
                                              #   }

GET    /api/v1/sync/{profileId}/delta         # Delta sync – only changes since timestamp
                                              #   ?since=2026-03-22T10:00:00Z
                                              #   Returns:
                                              #   {
                                              #     added: [ ...full content trees for new entries ],
                                              #     updated: [ ...entries with changed track lists ],
                                              #     removed: [ ...entry URIs to delete locally ],
                                              #     favoritesAdded: [],
                                              #     favoritesRemoved: [],
                                              #     syncTimestamp: "2026-03-22T14:30:00Z"
                                              #   }
```

**Backend content resolution pipeline:** When a parent adds content, the backend immediately resolves it in the background:

```
Parent adds "Bibi & Tina" (ARTIST scope) for Luna
  │
  ├─→ Backend stores AllowedContent row (profile_id = Luna, scope = ARTIST)
  │
  ├─→ Background job triggers: ContentResolver.resolve(entry)
  │     ├─→ Spotify API: GET /v1/artists/{id}/albums → 48 albums
  │     ├─→ For each album: GET /v1/albums/{id}/tracks → tracks
  │     ├─→ Apply content type heuristic per album
  │     └─→ Store resolved tree in backend DB (ResolvedAlbum, ResolvedTrack tables)
  │
  ├─→ Mark entry as resolved_at = now()
  │
  └─→ Push CONTENT_UPDATED via WebSocket to Luna's device
      └─→ Kids App triggers delta sync → receives full tree → stores in Room
```

**Periodic re-resolution:** A scheduled backend job (daily) re-resolves ARTIST and PLAYLIST scoped entries to pick up new releases. If new albums/tracks are found, the entry's `updated_at` changes and the next delta sync delivers the additions to kids' devices.

### 6.2 WebSocket Endpoints

```
wss://kidstune.altenburger.io/ws/parent/{familyId}
  # Parent receives:
  #   - CONTENT_REQUEST: new request from child (immediate notification trigger)
  #   - CONTENT_ADDED: another parent added content
  #   - DEVICE_ONLINE/OFFLINE: kids device connectivity changes

wss://kidstune.altenburger.io/ws/kids/{deviceId}
  # Kids device receives:
  #   - REQUEST_APPROVED: content request was approved (update UI instantly)
  #   - REQUEST_REJECTED: content request was rejected
  #   - CONTENT_UPDATED: new content added by parent (trigger sync)
  #   - PROFILE_UPDATED: profile settings changed
```

### 6.3 WebSocket Message Format

```json
{
  "type": "CONTENT_REQUEST",
  "timestamp": "2026-03-22T14:30:00Z",
  "payload": {
    "requestId": "uuid-here",
    "profileId": "uuid-here",
    "profileName": "Luna",
    "profileAvatar": "bear",
    "spotifyUri": "spotify:album:abc123",
    "title": "Frozen (Original Motion Picture Soundtrack)",
    "imageUrl": "https://i.scdn.co/image/...",
    "artistName": "Various Artists"
  }
}
```

---

## 7. Module Breakdown & Implementation Plan

### Overview: Phase Milestones

```
Phase 1 ─→ Backend boots, DB migrates, Spotify OAuth works
Phase 2 ─→ Web Dashboard live: search Spotify, manage content per-profile, email notifications
Phase 3 ─→ Kids App renders mock content, full UI navigation testable on device
Phase 4 ─→ Kids App plays real music from Spotify via backend ← USABLE MVP
Phase 5 ─→ Devices pair, sync automatically, profiles bind to devices
Phase 6 ─→ Import from listening history, offline mode, Samsung Kids tested
Phase 7 ─→ Kids can request content, parents get email + browser push notifications
Phase 8 ─→ Production-hardened, admin data tables, documented, ready for daily use
```

---

### Phase 1 – Backend Foundation (Week 1)

**Goal:** A running backend that authenticates with Spotify and manages profiles.

| Module | Scope | Tests |
|--------|-------|-------|
| **Backend: Project Scaffold** | Spring Boot 4.0 project, Docker Compose config (connecting to existing MariaDB), Gradle Kotlin DSL, application.yml with profiles (dev/prod) | Smoke: app starts in Docker |
| **Backend: Liquibase Schema** | Complete initial changelog: Family, ChildProfile, AllowedContent, ResolvedAlbum, ResolvedTrack, Favorite, ContentRequest, PairedDevice tables with all columns, indexes, and foreign keys | Integration: Testcontainers – migrations apply cleanly, rollback works |
| **Backend: Spotify OAuth** | OAuth PKCE flow, token encryption + storage in Family table, automatic refresh scheduling (background job), `/api/v1/auth/status` endpoint | Unit: token refresh scheduling, encryption round-trip. Integration: mock Spotify token endpoint |
| **Backend: Profile CRUD** | Full REST endpoints for ChildProfile (create, read, update, delete with cascade), avatar + age_group enums, input validation | Unit: service layer validation. Integration: full REST round-trip with Testcontainers |
| **Backend: Spring Security** | JWT-based device authentication skeleton, endpoint security rules (public: OAuth callback; device-token: kids endpoints; Spotify-auth: parent endpoints) | Integration: unauthorized request → 401, valid token → 200 |

**Milestone deliverable:** `docker compose up` starts the backend. You can:
- Hit `/actuator/health` → green
- Complete Spotify OAuth in a browser → tokens stored
- CRUD profiles via curl/Postman
- All DB tables exist with correct schema

---

### Phase 2 – Content Management Backend + Web Dashboard (Weeks 2-3)

**Goal:** A functional web dashboard accessible from any browser, where you can search Spotify and manage content per-profile. Parents receive email notifications for content requests.

| Module | Scope | Tests |
|--------|-------|-------|
| **Backend: Content Management** | AllowedContent CRUD per-profile, bulk-add to multiple profiles, scope resolution algorithm (see §4.2), content check endpoint, Caffeine caching for Spotify metadata | Unit: scope resolution logic with all 4 scope types (30+ test cases), multi-profile bulk add. Integration: full scenarios with mock Spotify API |
| **Backend: Content Type Detection** | Heuristic classifier (see §4.3), known children's artists YAML config, manual override support | Unit: genre matching, duration heuristic, name pattern matching, edge cases (50+ test cases) |
| **Backend: Spotify Search Proxy** | Search endpoint wrapping Spotify Web API, result grouping by type (artist/album/playlist), explicit content filtering | Unit: result grouping and filtering. Integration: mock Spotify search responses |
| **Web Dashboard: Foundation** | Thymeleaf + HTMX + Bootstrap 5 added to backend. Dual auth: session cookies for `/web/**`, JWT for `/api/**`. Spotify OAuth web login. Base layout template with sidebar. Dashboard with stats overview. | Integration: OAuth → session → dashboard. Security: unauthenticated → redirect to login |
| **Web Dashboard: Profiles & Content** | Profile CRUD pages. Per-profile content list with filters. Spotify search via HTMX live results. Scope picker + multi-profile selector for adding content. HTMX delete with confirmation. | Integration: create profile via web → visible in REST API. HTMX search returns partial |
| **Web Dashboard: Approval Queue** | Request queue with PENDING/APPROVED/REJECTED/EXPIRED tabs. Approve/reject with optional note. Approve-for-all-children. HTMX card removal on action. | Integration: approve via web → AllowedContent created |
| **Backend: Email Notifications** | Spring Mail configuration. Email on request creation (to all `notification_emails`). Token-based one-click `/web/approve/{token}` (public endpoint). Daily digest at 19:00. | Unit: email content, token generation. Integration: request created → email sent (MockSmtp) |
| **Web Dashboard: Settings** | Family settings page: configure `notification_emails` (comma-separated, one per parent). | UI test: save emails → verified in DB |

**Milestone deliverable:** Browse to `https://kidstune.altenburger.io/web` from any device. You can:
- Log in with Spotify
- See child profiles on the dashboard
- Search "Bibi & Tina" → see grouped results (artists, albums, playlists)
- Add "Bibi & Tina" as artist to Luna's profile → content visible in Luna's list
- Add same artist to Max's profile with one more tap
- Remove content from a single profile
- When a content request arrives: email delivered to all configured parents with one-click approve link

---

### Phase 3 – Kids App UI Shell with Mock Data (Week 4)

**Goal:** Kids App is fully navigable with realistic mock data. No backend connection yet – purely UI validation. Hand device to a child and observe usability.

| Module | Scope | Tests |
|--------|-------|-------|
| **Kids App: Project Setup** | Kotlin/Compose scaffold, Hilt DI, Room DB schema, navigation graph | Smoke: app compiles and launches |
| **Kids App: Theme & Design System** | Kid-friendly Material 3 theme: color palette (blue for music, green for audiobooks, pink for favorites), 72dp minimum touch targets, large rounded corners, playful typography | Visual inspection on target device |
| **Kids App: Home Screen** | Two large category buttons (Music / Audiobooks) + Favorites heart button, profile avatar in corner showing bound profile name, mini-player bar at bottom (static mock) | UI test: navigation to each category |
| **Kids App: Content Grid** | Paginated tile grid (2x2) with large album art from hardcoded mock data, page indicator dots, left/right swipe navigation between pages, shimmer loading placeholder | UI test: tiles render, pagination works, swipe navigates. Unit: pagination logic |
| **Kids App: Now Playing Screen** | Full-screen cover art, play/pause/skip buttons (non-functional), progress bar (static), favorite heart button, artist name + title text below artwork | UI test: screen renders with mock data |
| **Kids App: Favorites View** | Grid of favorited tracks (mock data), heart icon overlay on tiles, empty state with friendly illustration | UI test: favorites grid renders, empty state shown when no favorites |
| **Kids App: One-Time Profile Selection** | Profile selector shown on first launch (mock profiles), confirmation dialog, subsequent launches skip directly to home | UI test: first launch → profile selector → confirm → home. Second launch → straight to home |

**Milestone deliverable:** Install Kids App on old Samsung phone. You can:
- See profile selection on first launch, pick a profile
- Navigate Home → Music → see tiles with album art → tap tile → see Now Playing
- Navigate Home → Audiobooks → different tiles
- Navigate Home → Favorites → see empty state or mock favorites
- Swipe between pages of content
- All interactions feel snappy, touch targets are large enough
- **Hand to your children and observe**: do they understand the navigation? Are buttons big enough? Is anything confusing?

---

### Phase 4 – Kids App Plays Real Music (Weeks 5-6)

**Goal:** Kids App connects to backend, receives pre-resolved content, and plays real Spotify audio. This is the **usable MVP** – children can actually listen to music and audiobooks that parents have whitelisted, **including offline**.

| Module | Scope | Tests |
|--------|-------|-------|
| **Backend: Content Resolver** | Background job that resolves AllowedContent into full album/track trees via Spotify API. Triggered on content add + daily re-resolution for ARTIST/PLAYLIST scopes. Populates ResolvedAlbum + ResolvedTrack tables. | Unit: resolution for each scope type (ARTIST → albums → tracks, ALBUM → tracks, PLAYLIST → tracks, TRACK → single track). Integration: mock Spotify API → verify resolved tables populated |
| **Backend: Sync Endpoint (initial)** | `/api/v1/sync/{profileId}` – full sync delivering complete pre-resolved content tree (entries + albums + tracks + favorites). Used by Kids App on launch. | Integration: add content → resolve → sync → verify full tree returned with correct track URIs |
| **Kids App: Room Schema** | Local DB with `LocalContentEntry`, `LocalAlbum`, `LocalTrack`, `LocalFavorite` tables (see §5.1.5). Migrations, DAOs with queries for browsing (entries by type, albums by entry, tracks by album). | Unit: DAO queries return correct results. Integration: insert tree → query albums → verify ordering |
| **Kids App: Sync Client** | Ktor client calls `/api/v1/sync/{profileId}`, parses response, stores complete tree in Room transactionally. For MVP: sync on app launch only (WorkManager comes in Phase 5). | Integration: mock backend response → verify Room populated correctly |
| **Kids App: Replace Mock Data** | Content grid, album view, and track list now read from Room instead of hardcoded mocks. All navigation works with real data. Cover art loads from Coil (with disk cache for offline). | UI test: tiles show real Spotify artwork, drill-down to albums/tracks works |
| **Kids App: Spotify Playback** | Spotify App Remote SDK integration: connection lifecycle management, play/pause/skip/seek using `spotify:track:...` URIs from local Room DB, track change listener, now-playing state updates, progress bar | Integration: SDK connects, plays a track from local URI, state updates in UI |
| **Kids App: Favorites (Real)** | Heart button persists to `LocalFavorite` in Room (synced=false), favorites tab reads from Room, upload to backend on next sync. **Backend mirrors each favorite add/remove to Spotify Liked Songs ("Lieblingssongs") via `PUT/DELETE /v1/me/tracks` using the child's profile-level token — fire-and-forget, never blocks the KidsTune operation. Gracefully no-ops if child Spotify account is not linked.** | Unit: FavoriteRepository add/remove/list. Unit: SpotifyFavoriteSyncService mirrors add/remove, no-ops on unlinked profile, swallows Spotify errors. Integration: favorite persists across app restart |
| **Kids App: Mini-Player** | Persistent bottom bar showing current cover art thumbnail + play/pause + track title, tappable to expand to full Now Playing screen | UI test: mini-player reflects playback state |

**Milestone deliverable:** ← **THIS IS THE MVP.** You can:
- Parent has added content for Luna's profile via Parent App (Phase 2)
- Backend has resolved all content into albums and tracks (background job)
- Kids App syncs on launch → receives full content tree → stores locally
- Kids App shows Luna's real content (album art from Spotify)
- Child taps "Bibi & Tina" → sees albums → taps "Folge 1" → music plays
- **All of the above works offline** (after initial sync, no internet needed)
- Play/pause/skip works
- Favorites persist across app restarts
- Child can switch between Music and Audiobooks tabs
- **Your kids can actually use this daily from this point on**

---

### Phase 5 – Device Pairing & Sync (Week 7)

**Goal:** Proper device pairing replaces the hardcoded profile assignment. Content changes in Parent App propagate automatically to Kids App.

| Module | Scope | Tests |
|--------|-------|-------|
| **Backend: Device Pairing** | 6-digit pairing code generation (cryptographically random), 5-minute expiry, device token (JWT) issuance, device registration with profile binding | Unit: code generation uniqueness, expiry logic. Integration: full pairing flow end-to-end |
| **Backend: Sync Endpoints (delta)** | Delta sync endpoint (`/api/v1/sync/{profileId}/delta?since=...`) delivering only changed/added/removed pre-resolved content trees since last sync. Full sync already implemented in Phase 4. | Integration: add content via parent → resolve → delta sync returns only new entry with full album/track tree |
| **Kids App: Pairing Flow** | Enter pairing code screen (large number pad, kid-friendly), device registration, JWT storage in EncryptedSharedPreferences, replaces hardcoded profile | UI test: pairing code entry. Integration: end-to-end with backend |
| **Kids App: Sync Manager** | WorkManager-based background sync on app launch + periodic (every 15 min when online). Delta sync preferred, full sync as fallback. Offline queue for favorites and content requests. Room DB updated transactionally. Conflict resolution: server wins for content, merge for favorites. | Unit: merge logic, offline queue, delta application. Integration: offline → online sync recovery, delta correctly applies additions/removals |
| **Web Dashboard: Device Management** | View paired devices with last-seen status, unpair device, reassign profile, generate new pairing code with large display | Integration: generate code via web → enter on kids device → paired |

**Milestone deliverable:** The proper setup flow works:
- Web dashboard generates pairing code → displayed as large numbers
- Kids device shows pairing screen → enter code → device paired to Luna's profile
- Parent adds new album for Luna → within 15 minutes, it appears on Luna's device
- Parent removes content → disappears from Luna's device on next sync

---

### Phase 6 – Import, Offline & Samsung Kids (Week 8)

**Goal:** Complete onboarding experience (import from Spotify history via web dashboard), robust offline behavior, verified Samsung Kids compatibility.

| Module | Scope | Tests |
|--------|-------|-------|
| **Backend: Listening History Import** | Fetch recently played + top artists + playlists from Spotify, apply children's content heuristic with age-based pre-selection, return grouped results. **Also fetches child's Liked Songs (`GET /v1/me/tracks`) and pre-populates KidsTune favorites for any liked track that is already in the child's resolved content — safety filter ensures only parent-approved content becomes a favorite.** | Unit: heuristic detection accuracy, age-range matching, liked-songs-to-favorites cross-reference logic. Integration: mock Spotify history responses |
| **Web Dashboard: Import Wizard** | Import wizard page: select profiles → HTMX-loaded suggestion cards with per-profile checkboxes (age-based pre-selection) → bulk add. **After content import, automatically calls importLikedSongsAsFavorites() per profile and shows imported favorites count on success page ("+ Y Lieblingssongs als Favoriten übernommen").** | Integration: full import flow via web → AllowedContent rows created + Favorite rows pre-populated |
| **Kids App: Offline Hardening** | Stress-test offline behavior: airplane mode from cold start (should show all cached content), Wi-Fi loss mid-playback (should continue), favorites added offline queued correctly, content request queued offline. Visual offline indicator (subtle cloud icon). | Unit: offline queue persistence. Integration: simulate network loss mid-sync → recovery. Manual: airplane mode end-to-end walkthrough |
| **Kids App: Samsung Kids Testing** | Verify: Activity lifecycle inside Samsung Kids, audio focus handling when Samsung Kids pauses/resumes, Spotify background process survives Samsung Kids transitions, correct behavior on Samsung Kids time limit reached | Manual test: documented procedure + verification checklist with screenshots |
| **Documentation: Samsung Kids Setup** | Step-by-step guide with screenshots: install apps, configure Samsung Kids, add KidsTune Kids as allowed app, verify Spotify runs in background | Published as README section |

**Milestone deliverable:** Full end-to-end onboarding works:
- Parent opens web dashboard for the first time → Spotify login → Import from history → Bibi & Tina pre-selected for both kids, Die drei ??? only for Max → import → done in 2 minutes
- Kids devices work reliably inside Samsung Kids
- Kids can listen to cached content even without Wi-Fi
- Complete setup guide exists for reproducibility

---

### Phase 7 – Content Requests & Notifications (Weeks 9-10)

**Goal:** Children can discover and request new content. Parents receive email notifications with one-click approve links, plus real-time browser push when the dashboard is open.

| Module | Scope | Tests |
|--------|-------|-------|
| **Backend: WebSocket Hub** | Spring WebFlux WebSocket handler, connection registry per familyId/deviceId, heartbeat/ping-pong (30s), automatic stale connection cleanup, reconnection support | Integration: connection lifecycle, message delivery after reconnection |
| **Backend: Content Requests** | ContentRequest CRUD with status lifecycle (PENDING → APPROVED/REJECTED/EXPIRED). Max 3 pending per profile (429 on excess). On approval: auto-create AllowedContent + trigger ContentResolver + WebSocket notify to kids device. Reject with optional parent note. Bulk operations. `approve_token` generated on creation. | Integration: full lifecycle, 429 limit, bulk operations |
| **Backend: Email Notifications** | Spring Mail sends email to all `family.notification_emails` on request creation: child name, content title, [Approve] link with `approve_token`, [View Dashboard] link. Public `/web/approve/{token}` endpoint (no auth). `sendDailyDigest()` at 19:00 for pending > 4h. `expireStaleRequests()` at 03:00 (PENDING > 7 days → EXPIRED). | Unit: email content, token expiry. Integration: MockSmtp verifies email sent with correct approve URL. Digest boundary conditions. |
| **Kids App: Discover Screen** | Search with voice input, request button (disabled at 3 pending), "My wishes" section with kid-friendly time context (no spinners), rejected items shown 24h then hidden, expired items silently removed, celebration animation on approval, NEW badge on fresh content | UI test: full flow including limit state. Unit: time context strings |
| **Web Dashboard: Approval Queue** | Already built in Phase 2. In this phase: wire up real-time badge update via WebSocket push (HTMX hx-trigger on SSE or polling fragment). | Integration: WS push → badge updates in open browser |

**Milestone deliverable:** The approval workflow is live with guaranteed delivery:
- Luna searches "Frozen" on her device → taps "Request" (2 of 3 slots used)
- **All parents get email immediately** with [Approve] link – no app needed, works from any device
- **Dashboard is open:** Pending badge updates in real time via WebSocket
- **Forgotten requests:** At 19:00, digest email lists all pending requests with approve links
- Parent clicks [Approve] in email → content added without login → confirmation page shown
- Luna's device shows celebration animation → Frozen Soundtrack now playable
- After 7 days without response: request silently expires, Luna can re-request

---

### Phase 8 – Admin Data Tables, Polish & Documentation (Weeks 11-12)

**Goal:** Admin CRUD tables for operational oversight, production-quality polish, everything documented.

| Module | Scope |
|--------|-------|
| **Web Dashboard: Admin Data Tables** | Paginated, sortable CRUD tables for all entities: Family (read-only), ChildProfile, AllowedContent, ResolvedAlbum/Track (read-only + re-resolve trigger), Favorite, ContentRequest, PairedDevice. HTMX confirmation modals before all destructive operations. |
| **Kids App: Animations** | Shared element transition (tile → Now Playing cover art), button press scale animations, page swipe physics, shimmer loading states, favorite heart bounce animation |
| **Kids App: Edge Cases** | Spotify app not installed → friendly message with setup instructions, Spotify not logged in → prompt to hand device to parent, Spotify premium expired → graceful error, device storage full → warn and suggest clearing Spotify cache |
| **Kids App: Accessibility** | Content descriptions on all images, touch target audit (≥72dp), color contrast check (WCAG AA), test with TalkBack screen reader |
| **Web Dashboard: Error Handling** | Network error states with user-friendly messages, Spotify token expiry → automatic re-auth redirect, empty states for all list pages, input validation with field errors |
| **Backend: Resilience** | Spotify API rate limit handling (429 → exponential backoff with queuing), per-device request throttling (prevent kids from spamming searches), circuit breaker on Spotify API client |
| **Backend: Observability** | Structured JSON logging for Grafana Loki, health check endpoints (DB connectivity, Spotify API reachable, WebSocket hub stats, active connections), Spring Boot Actuator metrics for Prometheus/Grafana |
| **End-to-End Tests** | Maestro YAML test suites for critical journeys: (1) fresh setup → pair → import → play, (2) parent adds content → appears on kids device, (3) kid requests → parent approves from notification → kid can play |
| **Documentation** | Complete README: architecture overview, setup guide with screenshots, Samsung Kids configuration, Docker deployment, Liquibase conventions, Spotify developer app setup, troubleshooting guide, known limitations |

**Milestone deliverable:** Everything is production-ready:
- No unhandled crashes or blank screens
- Graceful behavior in all error scenarios
- Performance is smooth (no jank in scrolling/animations)
- Complete documentation lets you rebuild from scratch
- **Confident enough to hand devices to your kids and walk away**

---

## 8. Test Strategy

### 8.1 Test Pyramid

```
        ╱ ╲
       ╱ E2E ╲           ~10 tests   (full user journeys on device)
      ╱───────╲
     ╱ Integr.  ╲         ~80 tests   (Testcontainers, MockWebServer, Compose Test)
    ╱─────────────╲
   ╱  Unit Tests    ╲      ~250 tests  (JUnit 5, MockK, Turbine)
  ╱───────────────────╲
```

### 8.2 Testing Approach by Layer

| Layer | Tool | What to Test | Example |
|-------|------|-------------|---------|
| **Backend: Unit** | JUnit 5 + Mockito | Service logic, scope resolution, content heuristics, token refresh | `ScopeResolver.isAllowed(trackUri)` returns true when artist is allowed |
| **Backend: Integration** | Testcontainers (MariaDB) + MockWebServer | Full API round-trips, DB queries, Spotify API mocking, WebSocket message delivery | POST content → GET sync → verify content appears in sync payload |
| **Backend: Liquibase** | Testcontainers | Migration rollback/forward, schema integrity after migration | Apply all changelogs → verify table structure → rollback → re-apply |
| **Android: ViewModel** | JUnit 5 + Turbine + MockK | State transitions, Flow emissions, error handling, loading states | Search query → loading state → results state → error state |
| **Android: Repository** | JUnit 5 + MockK | Network ↔ local DB sync logic, offline queue, conflict resolution | Add favorite offline → verify queued → simulate sync → verify sent |
| **Android: Room** | Robolectric + in-memory DB | Queries, schema migrations, data integrity, cascade deletes | Delete profile → verify favorites cascade-deleted |
| **Android: Compose UI** | `createComposeRule()` | Screen rendering, user interactions, navigation, accessibility | Profile selector shows 2 profiles → tap first → navigates to home |
| **Android: E2E** | Maestro (YAML-based) | Critical user journeys without code | `launch → select profile → tap Music → tap first tile → verify playing` |

### 8.3 Spotify API Mocking Strategy

Since all Spotify API calls route through the backend, testing is cleanly separated:

- **Backend tests:** MockWebServer returns pre-recorded Spotify API responses stored as JSON fixtures in `src/test/resources/spotify-fixtures/`. Fixtures are recorded from real Spotify API calls and anonymized.
- **Android tests:** MockK mocks the Repository layer entirely. No Spotify dependency in UI tests. ViewModels are tested with fake data.
- **Integration tests:** Backend test suite with MockWebServer covers all Spotify interactions end-to-end.

### 8.4 Test Coverage Targets

| Module | Target | Rationale |
|--------|--------|-----------|
| Scope resolution logic | **100%** | Core business logic – a bug here means kids hear wrong content or can't hear allowed content |
| Content type heuristic | **95%+** | Important for UX, well-defined inputs and outputs |
| Sync/offline logic | **90%+** | Data integrity is critical – lost favorites or stale content is a bad experience |
| Backend REST endpoints | **95%+** | API contract must be stable for both apps |
| ViewModels | **85%+** | State management must be predictable |
| Room DAOs | **90%+** | Query correctness, especially for filtered content views |
| UI screens | **70%+** | Key interactions covered; pixel-perfect testing is overkill for a family project |

---

## 9. Repository Strategy

### 9.1 Monorepo (Recommended)

All three components live in a **single Git repository**. This is the strongly recommended approach for this project.

**Why monorepo over multi-repo:**

| Concern | Monorepo | Multi-repo |
|---------|----------|------------|
| **Shared code** | `shared/` module referenced directly via Gradle `includeBuild` | Must publish shared module as Maven artifact, manage versioning |
| **API changes** | Single commit changes backend endpoint + both app consumers | Coordinated PRs across 3+ repos, risk of version mismatch |
| **CI/CD** | Single Jenkinsfile with path-based triggers | 3+ separate pipelines, complex cross-repo dependency management |
| **Code reviews** | Full context in one PR when feature spans backend + app | Reviewer must cross-reference multiple PRs |
| **Setup complexity** | Clone once, build all | Clone 3+ repos, configure inter-repo dependencies |
| **Gradle sync** | One Gradle project, IDE resolves everything | Each project independent, shared module needs artifact publishing |

For a family project maintained by a single developer, monorepo eliminates significant overhead with no real downsides. The only scenario where multi-repo would make sense is if multiple independent teams were working on different components.

### 9.2 CI/CD with Path-Based Triggers

Jenkins detects which components changed and only builds those:

```groovy
// Jenkinsfile (simplified)
pipeline {
    stages {
        stage('Detect Changes') {
            steps {
                script {
                    env.BACKEND_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 | grep '^backend/' || true",
                        returnStdout: true).trim()
                    env.KIDS_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 | grep '^kids-app/\\|^shared/' || true",
                        returnStdout: true).trim()
                    env.PARENT_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 | grep '^parent-app/\\|^shared/' || true",
                        returnStdout: true).trim()
                }
            }
        }
        stage('Backend') {
            when { expression { env.BACKEND_CHANGED } }
            steps {
                dir('backend') {
                    sh './gradlew test'
                    sh './gradlew bootBuildImage'
                    // Deploy to homeserver via docker compose
                }
            }
        }
        stage('Kids App') {
            when { expression { env.KIDS_CHANGED } }
            steps {
                dir('kids-app') {
                    sh './gradlew test'
                    sh './gradlew assembleRelease'
                    // Store APK as artifact for sideloading
                }
            }
        }
    }
}
```

**Note:** Changes to `shared/` trigger a rebuild of the kids-app. The web dashboard is part of the backend build, so changes to `backend/` redeploy the web dashboard automatically.

---

## 10. Project Structure

```
kidstune/
│
├── backend/
│   ├── src/main/java/com/kidstune/
│   │   ├── KidstuneApplication.java
│   │   ├── config/                          # Spring config, security, WebSocket, caching
│   │   ├── auth/                            # OAuth, device tokens, pairing
│   │   ├── profile/                         # ChildProfile entity, service, controller
│   │   ├── content/                         # AllowedContent, scope resolution, heuristics
│   │   ├── resolver/                        # ContentResolver: background job resolving content
│   │   │                                    #   into ResolvedAlbum + ResolvedTrack trees
│   │   ├── favorites/                       # Favorites entity, service, controller
│   │   ├── requests/                        # ContentRequest workflow
│   │   ├── spotify/                         # Spotify Web API client, search, import
│   │   ├── sync/                            # Sync endpoints: builds pre-resolved tree payloads
│   │   └── ws/                              # WebSocket handlers, connection registry
│   ├── src/main/resources/
│   │   ├── db/changelog/
│   │   │   ├── db.changelog-master.yaml     # Liquibase master changelog
│   │   │   ├── 001-initial-schema.yaml      # All tables
│   │   │   └── 002-known-artists-seed.yaml  # Seed data
│   │   ├── templates/web/                   # Thymeleaf templates
│   │   │   ├── layout.html                  # Base layout with sidebar
│   │   │   ├── login.html
│   │   │   ├── dashboard.html
│   │   │   ├── profiles/                    # list.html, form.html
│   │   │   ├── content/                     # list.html, add.html
│   │   │   ├── requests/                    # queue.html (tabs)
│   │   │   ├── devices/                     # list.html, pair.html
│   │   │   ├── import/                      # wizard.html
│   │   │   ├── approve.html                 # Public one-click approve page
│   │   │   ├── admin/                       # Admin CRUD tables
│   │   │   ├── fragments/                   # Reusable HTMX partials
│   │   │   └── error/                       # 403, 404, 500 pages
│   │   ├── static/                          # Bootstrap + HTMX webjars (or CDN refs)
│   │   ├── known-artists.yml                # Configurable children's artist list
│   │   └── application.yml
│   ├── src/test/
│   │   ├── java/com/kidstune/               # Unit + integration tests
│   │   └── resources/spotify-fixtures/      # Recorded Spotify API responses
│   ├── Dockerfile
│   └── build.gradle.kts
│
├── kids-app/
│   ├── app/src/main/java/com/kidstune/kids/
│   │   ├── di/                              # Hilt modules
│   │   ├── data/
│   │   │   ├── local/                       # Room DB: LocalContentEntry, LocalAlbum,
│   │   │   │                                #   LocalTrack, LocalFavorite (see §5.1.5)
│   │   │   ├── remote/                      # Ktor API client, WebSocket client
│   │   │   └── repository/                  # Content, favorites, sync, request repos
│   │   ├── domain/
│   │   │   ├── model/                       # ContentTile, NowPlaying, ChildProfile
│   │   │   └── usecase/                     # GetContentTiles, ToggleFavorite, etc.
│   │   ├── ui/
│   │   │   ├── setup/                       # Pairing + one-time profile selection
│   │   │   ├── home/                        # Home screen (Music/Audiobooks/Favorites)
│   │   │   ├── browse/                      # Paginated content grid
│   │   │   ├── player/                      # Now Playing + mini-player bar
│   │   │   ├── discover/                    # Search + request (bonus)
│   │   │   ├── theme/                       # Kid-friendly Material 3 theme
│   │   │   └── components/                  # ContentTile, FavoriteButton, PageIndicator
│   │   ├── playback/                        # Spotify App Remote SDK wrapper
│   │   └── sync/                            # WorkManager sync jobs, offline queue
│   └── app/src/test/
│
├── shared/                                  # Shared Kotlin module
│   ├── src/main/kotlin/com/kidstune/shared/
│   │   ├── model/                           # API DTOs (ContentDto, ProfileDto, etc.)
│   │   └── constants/                       # ApiRoutes, ContentType, ContentScope enums
│   └── build.gradle.kts
│
├── docker-compose.yml
├── Jenkinsfile
├── settings.gradle.kts                      # Root Gradle settings (includes all modules)
├── build.gradle.kts                         # Root build file (common config)
└── README.md
```

---

## 11. Docker Compose

Since MariaDB is already running on the homeserver, the backend container connects to it directly. No separate DB container needed.

```yaml
services:
  kidstune-backend:
    build: ./backend
    container_name: kidstune-backend
    restart: unless-stopped
    ports:
      - "8090:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:3306/kidstune
      - SPRING_DATASOURCE_USERNAME=kidstune
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - SPRING_LIQUIBASE_CHANGE_LOG=classpath:db/changelog/db.changelog-master.yaml
      - SPOTIFY_CLIENT_ID=${SPOTIFY_CLIENT_ID}
      - SPOTIFY_CLIENT_SECRET=${SPOTIFY_CLIENT_SECRET}
      - KIDSTUNE_JWT_SECRET=${JWT_SECRET}
      - KIDSTUNE_BASE_URL=https://kidstune.altenburger.io
    extra_hosts:
      - "host.docker.internal:host-gateway"
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.kidstune.rule=Host(`kidstune.altenburger.io`)"
      - "traefik.http.routers.kidstune.tls.certresolver=letsencrypt"
      - "traefik.http.services.kidstune.loadbalancer.server.port=8080"
    networks:
      - traefik

networks:
  traefik:
    external: true
```

**Note:** If MariaDB runs in its own Docker container rather than directly on the host, replace `host.docker.internal` with the MariaDB container name and ensure both containers share the same Docker network.

**Pre-deployment database setup:**
```sql
CREATE DATABASE kidstune CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'kidstune'@'%' IDENTIFIED BY 'your-secure-password';
GRANT ALL PRIVILEGES ON kidstune.* TO 'kidstune'@'%';
FLUSH PRIVILEGES;
```

---

## 12. Deployment & Setup Procedure

### 12.1 First-Time Setup

```
1. DATABASE SETUP
   - Create kidstune database and user in existing MariaDB (see §11)
   - Liquibase runs automatically on first backend startup

2. SPOTIFY DEVELOPER APP
   - Register at developer.spotify.com
   - Create new app, set redirect URI:
     https://kidstune.altenburger.io/api/v1/auth/spotify/callback
   - Note Client ID and Client Secret
   - Add Spotify accounts (yours + test) to development mode user list

3. BACKEND DEPLOYMENT
   - Set environment variables in .env file
   - docker compose up -d
   - Verify: curl https://kidstune.altenburger.io/actuator/health

4. WEB DASHBOARD SETUP (from any browser)
   - Browse to https://kidstune.altenburger.io/web
   - Spotify OAuth login
   - Settings → configure notification_emails (one per parent)
   - Create child profiles (name, avatar animal + color, age group)
   - Import existing children's content from listening history
   - Add additional artists/playlists as needed via search

5. KIDS DEVICE SETUP (on old Samsung smartphone)
   a. Install Spotify from Play Store
   b. Log in to Spotify with family account
   c. In Spotify settings: download children's playlists for offline use
   d. Install KidsTune Kids APK via ADB
   e. Open KidsTune Kids → enter pairing code from Parent App
   f. Verify content loads and playback works

6. SAMSUNG KIDS CONFIGURATION
   a. Open Samsung Kids from device settings
   b. Set parental PIN
   c. Add ONLY KidsTune Kids to allowed apps
   d. Do NOT add Spotify (it runs as background service)
   e. Optionally set daily time limits
   f. Enable Samsung Kids as default launcher (optional)

7. HAND DEVICE TO CHILD
```

### 12.2 Ongoing Maintenance

| Task | How | Frequency |
|------|-----|-----------|
| **Adding new content** | Web Dashboard → Profile → Search → Add (takes seconds) | As needed |
| **Approving requests** | Click [Approve] link in email, or open web dashboard | As they come in |
| **Backend updates** | Jenkins builds → `docker compose up -d` auto-deployed | On code changes |
| **Kids App updates** | Jenkins builds APK → sideload via ADB or Jenkins artifact | On code changes |
| **Spotify offline content** | Periodically download new content in Spotify app for offline use | Monthly |
| **Review allowed content** | Web Dashboard → Profile → Content List → review and clean up | Quarterly |

---

## 13. Risk Assessment & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Spotify changes/deprecates App Remote SDK | Medium | High | Wrap SDK in abstraction layer (`PlaybackController`); monitor Spotify developer changelog; fallback: use Spotify Connect API |
| Spotify rate limits hit | Low | Medium | Backend caches all metadata in Caffeine; search results cached 1h; artist catalogs cached 6h; track mappings cached 24h |
| Kids' device loses connectivity | High | Low | Full offline support via Room cache + Spotify offline downloads; graceful degradation; sync resumes automatically |
| Spotify revokes dev app in "development mode" | Very Low | High | Stay under 25 users (easy for family); if ever needed, apply for extended quota |
| Content type heuristic misclassifies | Medium | Low | Parent can manually override in web dashboard; heuristic improves with expanded known-artists list |
| Kids break out of Samsung Kids | Very Low | Low | Samsung Kids is well-tested; additional fallback via Lock Task Mode available if needed |
| Email delivery fails (SMTP outage) | Low | Low | Parent can still open web dashboard directly; daily digest retries next day; approve link is permanent until request expires |
| Parent misses email (spam filter) | Low | Low | Daily digest at 19:00 provides a second chance; dashboard always shows pending count when opened |
| MariaDB JSON column performance | Very Low | Low | `cached_metadata` is read-only cache; actual queries use indexed relational columns |
| Spring Boot 4 ecosystem maturity | Low | Medium | Pin exact version (4.0.4); well-tested by community; migration guide available |

---

## 14. Future Considerations (Out of Scope for MVP)

- **Sleep timer** – straightforward addition to player screen (countdown + fade-out)
- **Time limits** – daily listening quota per profile, enforced in-app (more granular than Samsung Kids limits)
- **Home Assistant integration** – expose playback state as HA sensor via MQTT, control via automations
- **Push notifications to parent phone** – add PWA notifications via browser push API (no FCM needed), triggered by existing WebSocket messages
- **Multiple family support** – backend already scoped by `family_id`, just needs registration flow
- **Playback history for parents** – track what each child listened to and for how long
- **Content recommendations** – suggest new content based on allowed artists via Spotify's recommendation API

---

## 15. Claude Code Implementation Strategy

This project is designed to be implemented primarily via **Claude Code**. The following guidelines ensure productive sessions.

### 15.1 CLAUDE.md

The repository root contains a `CLAUDE.md` file that Claude Code reads automatically on every session start. It contains:
- Full architecture overview and key design decisions
- Tech stack with exact versions
- Coding conventions for backend (Java/Spring) and Android (Kotlin/Compose)
- Project structure reference
- Common pitfalls and gotchas (Spring Boot 4 breaking changes, MariaDB differences, Room vs backend entities, etc.)
- Build and run commands

**Keep CLAUDE.md updated.** After each phase, review and update it with any new patterns, conventions, or decisions that emerged during implementation.

### 15.2 Task Decomposition for Claude Code

Claude Code works best with **focused, atomic tasks** that produce testable results. Each phase should be broken into individual prompts following this pattern:

```
Prompt structure for Claude Code:

1. CONTEXT: Which phase/module are we implementing?
2. GOAL: What should exist when this task is done?
3. REFERENCE: Point to specific sections of PROJECT_PLAN.md and CLAUDE.md
4. CONSTRAINTS: What NOT to do (avoid scope creep)
5. VERIFICATION: How to confirm it works (test commands, curl examples)
```

**Example prompt for Phase 1:**
```
Implement the backend project scaffold for KidsTune Phase 1.

Create:
- Spring Boot 4.0.4 project in backend/ with Gradle Kotlin DSL
- application.yml with dev/prod profiles (dev uses localhost MariaDB, prod uses env vars)
- Docker Compose config connecting to existing MariaDB via host.docker.internal
- Liquibase master changelog + 001-initial-schema.yaml with ALL tables from PROJECT_PLAN.md §4.1
- Spring Boot Actuator health endpoint enabled
- Basic Spring Security config (permit all for now, skeleton for JWT later)

Do NOT implement:
- Spotify OAuth (next task)
- Profile CRUD endpoints (next task)
- Any Android app code

Verify by running: ./gradlew bootRun (with local MariaDB)
and: curl http://localhost:8080/actuator/health → should return 200
```

### 15.3 Recommended Prompt Sequence per Phase

#### Phase 1 – Backend Foundation
```
1.1: "Create the backend Spring Boot 4 project scaffold with Gradle, Docker Compose,
      and Liquibase initial schema (all tables from §4.1 including ResolvedAlbum/Track).
      Include Actuator health endpoint. Verify with bootRun + curl."

1.2: "Implement Spotify OAuth PKCE flow in the auth/ package. Store encrypted
      refresh token in Family table. Implement automatic token refresh scheduling.
      Add /api/v1/auth/status endpoint. Write integration test with MockWebServer
      mocking the Spotify token endpoint."

1.3: "Implement ChildProfile CRUD (create, read, update, delete with cascade)
      in the profile/ package. Use Jakarta validation. Write unit tests for service
      layer and integration tests for the REST endpoints with Testcontainers."

1.4: "Implement Spring Security 7 JWT authentication. Create JwtTokenService
      for device token issuance + validation. Configure endpoint security rules:
      public for OAuth callback, device-token for kids endpoints, Spotify-auth
      for parent endpoints. Write integration tests: 401 without token, 200 with."
```

#### Phase 2 – Content Management + Web Dashboard
```
2.1: "Implement AllowedContent CRUD per-profile in the content/ package. Include
      scope field (TRACK/ALBUM/PLAYLIST/ARTIST). Implement scope resolution
      algorithm from §4.2. Write 30+ unit tests covering all scope types.
      Add bulk-add endpoint for adding to multiple profiles at once."

2.2: "Implement ContentTypeClassifier with the heuristic from §4.3. Load
      known-artists.yml config with age ranges. Write 50+ unit tests covering
      genre matching, duration heuristic, name patterns, and edge cases."

2.3: "Implement Spotify search proxy endpoint wrapping the Web API. Group
      results by type (artist/album/playlist). Filter explicit content.
      Write integration tests with MockWebServer and JSON fixtures."

2.4: "Create the parent-app Android project scaffold. Set up Hilt DI, Ktor
      client targeting the backend, Compose Navigation, Material 3 theme.
      Implement Spotify OAuth login screen. Verify app compiles and launches."

2.5: "Implement the Parent App dashboard showing profile cards from the backend.
      Implement content search screen with debounced input, grouped results,
      scope selection, and profile picker. Implement add + delete flows.
      Write ViewModel unit tests with Turbine."
```

#### Phase 3 – Kids App UI Shell
```
3.1: "Create the kids-app Android project scaffold. Set up Hilt DI, Room DB
      with LocalContentEntry/LocalAlbum/LocalTrack/LocalFavorite tables,
      Compose Navigation, kid-friendly Material 3 theme (72dp touch targets,
      color-coded sections). Verify app compiles."

3.2: "Implement all Kids App screens with hardcoded mock data: profile selection
      (one-time), home screen (Music/Audiobooks/Favorites buttons), content
      grid (2x2 paginated tiles), Now Playing screen, mini-player bar,
      favorites view with empty state. Add @Preview for every screen."

3.3: "Write Compose UI tests for Kids App: profile selector → home navigation,
      category navigation, tile grid pagination, Now Playing screen rendering.
      All tests use mock data, no backend dependency."
```

#### Phase 4 – MVP (Real Playback)
```
4.1: "Implement the ContentResolver background job in the resolver/ package.
      When AllowedContent is created, resolve it via Spotify API into
      ResolvedAlbum + ResolvedTrack rows. Handle all scope types.
      Write integration tests with MockWebServer."

4.2: "Implement the full sync endpoint: GET /api/v1/sync/{profileId} returns
      the complete pre-resolved content tree (entries → albums → tracks +
      favorites). Write integration test: add content → resolve → sync
      → verify full tree in response."

4.3: "In kids-app, implement the Ktor sync client + Room storage. On app
      launch, call sync endpoint, store full tree in Room transactionally.
      Replace mock data with Room queries. Verify tiles show real Spotify
      album art. Write integration tests for sync → Room storage."

4.4: "Integrate Spotify App Remote SDK in kids-app. Implement PlaybackController
      wrapper: connect, play(trackUri), pause, skip, seek, nowPlaying StateFlow.
      Wire up to Now Playing screen and mini-player. Track URIs come from
      LocalTrack in Room, NOT from any network call."

4.5: "Implement real favorites in kids-app. Heart button persists to
      LocalFavorite in Room (synced=false). Favorites tab reads from Room.
      Upload queued favorites to backend on next sync. Write unit tests."
```

#### Phase 5 – Device Pairing & Sync
```
5.1: "Implement device pairing in the backend auth/ package. Add:
      - POST /api/v1/auth/pair → generates cryptographically random 6-digit code,
        stores with 5-minute expiry in DB
      - POST /api/v1/auth/pair/confirm → kids device submits code, backend validates,
        returns JWT device token, creates PairedDevice row
      Write unit tests for code generation (uniqueness, expiry) and integration
      tests for the full pairing flow end-to-end."

5.2: "Implement the delta sync endpoint in the backend sync/ package:
      GET /api/v1/sync/{profileId}/delta?since={timestamp}
      Returns only content entries (with full album/track trees) that were
      added, updated (re-resolved), or removed since the given timestamp.
      Also returns favoriteAdded/favoriteRemoved lists.
      Write integration test: add content → sync → add more content →
      delta sync → verify only new content in response."

5.3: "In kids-app, implement the pairing flow UI: a screen with large number
      pad (72dp buttons) for entering 6-digit code. On successful pairing,
      store JWT in EncryptedSharedPreferences, navigate to one-time profile
      selection. On subsequent launches, skip pairing entirely (check for
      stored token). Write UI test for the pairing flow."

5.4: "In kids-app, implement SyncManager using WorkManager. Schedule sync
      on app launch + periodic every 15 minutes (when online). Use delta
      sync when lastSyncTimestamp exists, fall back to full sync. Apply
      changes to Room DB in a single transaction. Upload queued LocalFavorites
      (synced=false) and queued content requests. Write unit tests for:
      delta application logic, offline queue, conflict resolution (server
      wins for content, merge for favorites)."

5.5: "In parent-app, implement the device management screen. Show list
      of paired devices with: device name, bound profile avatar+name,
      last seen timestamp, online/offline indicator. Add 'Generate Pairing
      Code' button that shows large 6-digit code with countdown timer.
      Add 'Reassign Profile' and 'Unpair' options per device.
      Write UI tests for device list and pairing code display."
```

#### Phase 6 – Import, Offline & Samsung Kids
```
6.1: "Implement SpotifyImportService in the backend spotify/ package. Call
      Spotify Web API: recently-played, top artists (medium + long term),
      user playlists. Group and deduplicate results. Apply children's content
      heuristic using known-artists.yml (match by name + genre). Return
      results with age-based pre-selection per profile (compare artist
      min_age with profile age_group). Write integration tests with
      MockWebServer using recorded Spotify API fixtures."

6.2: "Implement the import REST endpoint: POST /api/v1/content/import
      accepts { items: [{ spotifyUri, scope, profileIds[] }] }. For each
      item and profile, create AllowedContent + trigger ContentResolver
      (from Phase 4.1) to populate ResolvedAlbum/Track. Return created
      content count per profile. Write integration test for the full
      import → resolve pipeline."

6.3: "In parent-app, implement the import wizard UI:
      Step 1: select which profiles to import for (checkboxes)
      Step 2: display results from backend grouped into 'Detected children's
      content' (pre-checked) and 'Other artists' (unchecked). Each item
      shows per-profile toggle chips (pre-selected based on age heuristic).
      Step 3: review + 'Import selected' button with progress indicator.
      Write ViewModel tests with Turbine for the multi-step state machine."

6.4: "In kids-app, implement offline hardening. Test and fix:
      - Cold start in airplane mode: app should launch, show all cached
        content from Room, allow browsing and playback (if Spotify cached)
      - Wi-Fi loss during playback: should continue playing
      - Favorites added offline: verify synced=false in Room, uploaded
        on next successful sync
      - Content requests queued offline: verify stored locally, sent on reconnect
      - Add subtle offline indicator (cloud icon with strikethrough) in top bar
      Write integration tests simulating network loss scenarios."

6.5: "Create Samsung Kids compatibility documentation and test checklist.
      In kids-app, verify and fix if needed:
      - Activity lifecycle inside Samsung Kids (recreation, onStop/onStart)
      - Audio focus handling when Samsung Kids pauses/resumes the app
      - Spotify background process survives Samsung Kids transitions
      - Correct behavior when Samsung Kids daily time limit is reached
      - App state preservation when Samsung Kids kills and restarts it
      Document the setup procedure in README.md with step-by-step instructions."
```

#### Phase 7 – Content Requests & Live Notifications
```
7.1: "Implement the WebSocket hub in the backend ws/ package using Spring
      WebFlux. Create:
      - WebSocketHandler: accepts connections at /ws/parent/{familyId} and
        /ws/kids/{deviceId}
      - ConnectionRegistry: tracks connected devices/parents by ID, provides
        sendToParent(familyId, message) and sendToDevice(deviceId, message)
      - Heartbeat: server sends ping every 30s, drops connections that don't
        pong within 10s
      Write integration tests: connect → send message → verify received,
      reconnection after drop, stale connection cleanup."

7.2: "Implement ContentRequest workflow in the backend requests/ package.
      - POST /api/v1/content-requests: creates request with status=PENDING.
        REJECT with 429 if profile already has 3+ PENDING requests.
        Dispatches CONTENT_REQUEST via WebSocket to parent.
      - PUT /api/v1/content-requests/{id}: approve or reject. On APPROVED:
        auto-create AllowedContent for the requesting profile + trigger
        ContentResolver + dispatch REQUEST_APPROVED to kids device.
        On REJECTED: dispatch REQUEST_REJECTED to kids device with optional
        parent note.
      - PUT /api/v1/content-requests/bulk: bulk approve/reject
      - GET /api/v1/content-requests: list with status filter and profileId filter
      - GET /api/v1/content-requests/pending/count: returns count per profile
        (used by Layer 2 polling in parent app)
      Write integration tests for: full lifecycle (request → notify → approve →
      content created → notify kids), 429 when limit exceeded, bulk operations."

7.3: "Implement backend scheduled jobs for content requests:
      - expireStaleRequests(): runs daily at 03:00, sets PENDING requests
        older than 7 days to EXPIRED, notifies kids devices via WebSocket
      - sendDailyDigest(): runs daily at 19:00, for each family with PENDING
        requests older than 4 hours: builds DAILY_DIGEST WebSocket message
        with summary (count per child, titles, oldest request age), sets
        digest_sent_at flag for Layer 2 polling detection
      Write unit tests for expiry logic (boundary: exactly 7 days, 6 days 23h).
      Write integration test for digest generation with mock data."

7.4: "In kids-app, implement the Discover screen (§5.1.6):
      - Search box with voice input button (Android SpeechRecognizer)
      - Results display: max 10 items, explicit content filtered, large tiles
      - 'Request' button on each result → POST to backend (or queue offline)
      - Request button DISABLED with friendly message when 3 pending requests
        exist (check local count, verified on backend too)
      - 'My wishes' section below search, showing pending requests with
        kid-friendly time context (not spinners):
        < 1h: 'Mama/Papa schauen sich das an'
        1-24h: 'Gestern gewünscht'
        > 24h: 'Vor ein paar Tagen gewünscht'
      - Rejected requests shown with ❌ and parent note for 24h, then hidden
      - Expired requests silently removed from UI
      - On REQUEST_APPROVED WebSocket: celebration animation (confetti + sound),
        trigger delta sync, new tile gets 'NEW' badge for 24h
      - Search rate-limited to 1 query per 5 seconds
      Write UI tests for: search → results → request → pending state → approved
      celebration. Test request limit (4th request shows disabled state).
      Unit test for time context strings and rate limiting."

7.5: "In parent-app, implement the three-layer notification system (§5.2.3):

      LAYER 1 – Foreground Service (real-time):
      - NotificationService extends Service, promoted to foreground with
        persistent notification: 'KidsTune – listening for requests'
      - Maintains WebSocket connection to wss://backend/ws/parent/{familyId}
      - Auto-reconnect with exponential backoff: 1s → 2s → 4s → ... → 30s max
      - On CONTENT_REQUEST message: create high-priority notification with
        child avatar, content artwork, [Approve] [Reject] action buttons
      - On DAILY_DIGEST message: create summary notification bundling all
        pending requests ('Luna and Max have 4 open wishes')
      - ApproveRejectReceiver (BroadcastReceiver): handles action button taps,
        calls PUT /api/v1/content-requests/{id} without opening app
      - Notifications tagged with requestId for deduplication

      LAYER 2 – WorkManager polling (fallback):
      - PendingRequestPollWorker: PeriodicWorkRequest every 15 minutes
      - Calls GET /api/v1/content-requests/pending/count
      - Compares with locally stored 'last seen request IDs' in SharedPrefs
      - Creates notification only if NEW requests found AND Layer 1 has NOT
        already delivered them (check via shared flag)
      - Survives app force-kill, phone reboot, Doze mode

      BOOT_COMPLETED:
      - BootReceiver re-registers WorkManager periodic poll and optionally
        restarts Foreground Service on device boot

      SETUP WIZARD:
      - On first launch: explain notification layers, request battery
        optimization exemption (Android system dialog), register WorkManager

      Write integration tests for:
      - Layer 1: service starts → WS connects → mock message → notification shown
      - Layer 2: service NOT running → WorkManager fires → polls backend →
        notification shown for new requests
      - Deduplication: Layer 1 delivers → Layer 2 polls → no duplicate notification
      - Boot receiver: simulate boot → verify WorkManager re-registered"

7.6: "In parent-app, implement the approval queue screen:
      - List of pending requests sorted by recency (newest first)
      - Each item shows: child avatar + name, content artwork, title,
        artist name, time ago, request age indicator
      - Approve options: 'Add for [child] only' / 'Add for all children'
      - Reject button with optional text note (shown to child)
      - Bulk 'Approve All' / 'Reject All' buttons at top
      - Expired requests tab showing 'Expired – not reviewed' with option
        to retroactively approve
      - Badge count on dashboard navigation item (pending count)
      Write UI tests for: list rendering, approve flow (single + bulk),
      reject with note, empty state, expired requests tab."
```

#### Phase 8 – Polish, Hardening & Documentation
```
8.1: "In kids-app, implement animations and transitions:
      - Shared element transition: content tile → Now Playing cover art
      - Button press scale animation (scale to 0.95 on press, back to 1.0)
      - Page swipe animation with spring physics for content grid
      - Shimmer loading placeholder for tiles before images load
      - Heart bounce animation when adding a favorite
      - Celebration confetti animation when a content request is approved
      Verify all animations run at 60fps on target device."

8.2: "In kids-app, implement edge case handling:
      - Spotify app not installed → show friendly screen with setup instructions
        and a dinosaur illustration, suggest handing device to parent
      - Spotify not logged in → same approach, 'ask a grown-up' message
      - Spotify premium expired → graceful error with explanation
      - Device storage full → warning suggesting to clear Spotify cache
      - Backend unreachable on first ever launch (no cached content) →
        'Ask a grown-up to connect to Wi-Fi' screen with airplane illustration
      Write unit tests for each state detection. Write UI tests for each
      error screen rendering."

8.3: "In kids-app, audit accessibility:
      - Add contentDescription to ALL images and icons (album art, buttons,
        avatars). Use artist name + album title for content tiles.
      - Verify all touch targets are ≥ 72dp using a custom Compose test rule
      - Check color contrast ratios against WCAG AA (4.5:1 for text)
      - Test full navigation flow with TalkBack screen reader enabled
      - Fix any issues found. Document accessibility status in README."

8.4: "In parent-app, implement error handling and polish:
      - Network error states on all screens: show friendly message with
        retry button, preserve any user input
      - Spotify token expiry: detect 401 from backend → trigger automatic
        re-auth flow → retry the failed request
      - Empty states for all list screens: content list, device list,
        approval queue, import results (friendly illustrations + guidance text)
      - Input validation with inline errors for profile creation
      - 'Quick add to other profile' action on content list items:
        'Also add to Max?' shortcut
      Write UI tests for: error state rendering, empty state rendering,
      retry flow after error."

8.5: "In the backend, implement resilience and observability:
      - Spotify API rate limit handling: detect 429 response, apply
        exponential backoff with jitter, queue requests during backoff
      - Per-device request throttling: max 10 search queries per minute,
        max 5 content requests per hour per profile
      - Circuit breaker on Spotify API client (Resilience4j): open after
        5 consecutive failures, half-open after 30s
      - Structured JSON logging for Grafana Loki: request ID, profile ID,
        device ID in all log entries via MDC
      - Actuator health indicators: DB connectivity, Spotify API reachable,
        WebSocket hub stats (connected parents, connected kids devices)
      Write integration tests for rate limiting and circuit breaker behavior."

8.6: "Write end-to-end Maestro test suites covering critical user journeys:
      Test 1: Fresh setup → pair device → select profile → verify home screen
      Test 2: Parent adds artist → sync → kids app shows new content → play
      Test 3: Kid requests content → parent approves from notification → kid plays
      Store Maestro YAML files in e2e-tests/ directory."

8.7: "Write comprehensive project documentation in README.md:
      - Architecture overview with diagram
      - Prerequisites (JDK 21, Android SDK, MariaDB, Docker, Spotify Dev App)
      - Quick start guide: clone → configure → run backend → build apps
      - Spotify Developer App setup with screenshots
      - Samsung Kids device configuration step-by-step with screenshots
      - APK sideloading via ADB instructions
      - Troubleshooting guide: common issues and fixes
      - Known limitations and future roadmap
      Also update CLAUDE.md with any new patterns or conventions that
      emerged during implementation."
```

### 15.4 Session Management

Claude Code sessions have finite context. For effective long sessions:

- **Start each session** by saying: "Read CLAUDE.md and PROJECT_PLAN.md, then implement task X.Y"
- **One task per prompt** when the task is complex (new module, new package)
- **Batch related small tasks** (e.g., "add these 3 Room entities and their DAOs")
- **End each session** by running `./gradlew test` to verify nothing is broken
- **After each phase**, commit and review. Update CLAUDE.md if patterns emerged.

### 15.5 What Claude Code Handles Well

- Generating Spring Boot controllers, services, entities from clear specifications
- Writing Room entities, DAOs, and migrations from a schema description
- Implementing Compose screens from ASCII wireframes (the plan has these)
- Writing comprehensive test suites when the expected behavior is clearly described
- Refactoring and wiring up layers (ViewModel → UseCase → Repository → DAO)
- Docker Compose, Liquibase changelogs, Gradle config

### 15.6 What Needs Human Attention

- **Spotify App Remote SDK setup:** The AAR file must be manually downloaded from Spotify's developer portal and placed in `kids-app/libs/`. Claude Code can write the Gradle config to include it, but can't download the SDK.
- **Spotify Developer App registration:** Manual step at developer.spotify.com
- **Samsung Kids configuration:** Physical device setup, not automatable
- **UI feel testing:** Hand the device to your kids and observe – no test suite replaces this
- **Spotify OAuth callback testing:** Requires a real browser redirect, hard to fully automate
- **Sideloading APKs:** ADB commands on physical devices

### 15.7 Testing Workflow with Claude Code

For every implementation prompt, include the testing expectation:

```
"Implement X. Write tests that cover:
- Happy path: ...
- Edge case: ...
- Error case: ...

Run ./gradlew test and show me the output."
```

Claude Code can run the tests and iterate if they fail, which is one of its strongest capabilities. Leverage this by always requesting test execution as part of the task.

