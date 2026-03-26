# KidsTune – Claude Code Prompts

All 40 implementation prompts for the KidsTune project, organized by phase.
Each prompt is self-contained and follows the structure: CONTEXT → GOAL → REFERENCE → CONSTRAINTS → VERIFICATION.

Start every Claude Code session with: **"Read CLAUDE.md and PROJECT_PLAN.md first."**

---

## Phase 1 – Backend Foundation (Week 1)

### Prompt 1.1 – Backend Project Scaffold

```
CONTEXT: Phase 1 of KidsTune. We are setting up the backend Spring Boot project from scratch.
The backend is a Spring Boot 4.0.4 / Java 21 application using Gradle Kotlin DSL that connects
to an existing MariaDB instance on the homeserver.

GOAL: When this task is done, the following should exist:
- A Spring Boot 4.0.4 project in backend/ with Gradle Kotlin DSL build
- build.gradle.kts with dependencies: spring-boot-starter-webflux, spring-boot-starter-data-jpa,
  spring-boot-starter-security, spring-boot-starter-actuator, liquibase-core,
  mariadb-java-client, caffeine, jackson-module-kotlin
- application.yml with two profiles:
  - dev: datasource url=jdbc:mariadb://server.altenburger.io:3306/kidstune, show-sql=true
  - prod: datasource url from env var SPRING_DATASOURCE_URL
  - common: liquibase changelog=classpath:db/changelog/db.changelog-master.yaml,
    server.port=8080, actuator health+info endpoints enabled
- Dockerfile (multi-stage: gradle build → eclipse-temurin:21-jre runtime)
- docker-compose.yml connecting to existing MariaDB via host.docker.internal
  with Traefik labels for kidstune.altenburger.io (see PROJECT_PLAN.md §11)
- Liquibase master changelog (db.changelog-master.yaml) including 001-initial-schema.yaml
- 001-initial-schema.yaml creating ALL tables from PROJECT_PLAN.md §4.1:
  Family (id UUID PK, spotify_user_id, spotify_refresh_token, created_at, updated_at)
  ChildProfile (id UUID PK, family_id FK, name, avatar_icon, avatar_color,
    age_group ENUM('TODDLER','PRESCHOOL','SCHOOL'), created_at, updated_at)
  AllowedContent (id UUID PK, profile_id FK, spotify_uri, content_type ENUM('MUSIC','AUDIOBOOK','MIXED'),
    scope ENUM('TRACK','ALBUM','PLAYLIST','ARTIST'), title, image_url, artist_name,
    cached_metadata JSON, added_by, created_at)
  ResolvedAlbum (id UUID PK, allowed_content_id FK, spotify_album_uri, title, image_url,
    release_date, total_tracks INT, content_type ENUM, resolved_at)
  ResolvedTrack (id UUID PK, resolved_album_id FK, spotify_track_uri, title, artist_name,
    duration_ms BIGINT, track_number INT, disc_number INT, image_url)
  Favorite (id UUID PK, profile_id FK, spotify_track_uri, track_title, track_image_url,
    artist_name, added_at)
  ContentRequest (id UUID PK, profile_id FK, spotify_uri, content_type ENUM, title, image_url,
    artist_name, status ENUM('PENDING','APPROVED','REJECTED','EXPIRED'),
    requested_at, resolved_at, resolved_by, parent_note, digest_sent_at)
  PairedDevice (id UUID PK, family_id FK, device_token_hash, device_name, device_type ENUM('KIDS','PARENT'),
    profile_id FK nullable, last_seen_at, created_at)
  All tables: utf8mb4, timestamps as DATETIME(6) in UTC, appropriate indexes on FKs
  and on (profile_id, spotify_uri) for AllowedContent uniqueness
  Cascade deletes: Profile deletion cascades to AllowedContent, Favorites, ContentRequests
- Basic Spring Security config: permit all for now (JWT comes in prompt 1.4)
- A main application class KidstuneApplication.java

REFERENCE: PROJECT_PLAN.md §4.1 (Core Entities), §4.1 (Resolved content tables),
§11 (Docker Compose), §3.2 (Backend tech stack). CLAUDE.md "Backend" coding conventions.

CONSTRAINTS:
- Do NOT implement any REST controllers or business logic yet
- Do NOT implement Spotify OAuth (prompt 1.2)
- Do NOT implement JWT authentication (prompt 1.4)
- Do NOT create any Android project code
- Use YAML format for Liquibase changelogs, not XML or SQL
- Use jakarta.* imports, NOT javax.* (Spring Boot 4 / Jakarta EE 11)

VERIFICATION:
- cd backend && ./gradlew bootRun (with local MariaDB running on port 3306)
  → app starts without errors, Liquibase migrations apply
- curl http://localhost:8080/actuator/health → {"status":"UP"}
- Verify all 8 tables exist in MariaDB: SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='kidstune';
- cd backend && ./gradlew test → tests pass (write a basic integration test
  with Testcontainers that verifies app context loads and Liquibase migrations apply)
```

### Prompt 1.2 – Spotify OAuth

```
CONTEXT: Phase 1 of KidsTune. The backend project scaffold exists (prompt 1.1).
We now need Spotify OAuth PKCE authentication so parents can link their Spotify account.

GOAL: When this task is done:
- A SpotifyConfig class reads spotify.client-id and spotify.client-secret from application.yml
- A SpotifyOAuthController handles the OAuth PKCE flow:
  - GET /api/v1/auth/spotify/login → redirects to Spotify authorize URL with PKCE challenge,
    scopes: user-read-playback-state, user-modify-playback-state, user-library-read,
    user-read-recently-played, playlist-read-private, streaming
  - POST /api/v1/auth/spotify/callback → receives auth code, exchanges for access+refresh tokens,
    stores encrypted refresh_token in Family table (create Family if first login),
    returns { familyId, accessToken, expiresIn }
- A SpotifyTokenService that:
  - Encrypts refresh tokens before storing (AES-256-GCM with key from env var KIDSTUNE_JWT_SECRET)
  - Decrypts refresh tokens when needed for API calls
  - Schedules automatic token refresh 5 minutes before expiry using a @Scheduled background job
  - Provides getValidAccessToken(familyId) that returns a current access token (refreshing if needed)
- GET /api/v1/auth/status returns { authenticated: true/false, spotifyConnected: true/false,
  familyId: "...", tokenExpiresAt: "..." }

REFERENCE: PROJECT_PLAN.md §2.3 (Authentication Flow), §6.1 Auth endpoints.
Spotify OAuth documentation: https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow

CONSTRAINTS:
- Do NOT implement device pairing or JWT device tokens (prompt 1.4 and phase 5)
- Do NOT implement any Spotify Web API data calls (search, playlists, etc.)
- Do NOT touch Android app code
- Store only the refresh_token encrypted in DB. Access tokens are ephemeral (in-memory or Caffeine cache).

VERIFICATION:
- Unit test: SpotifyTokenService.encrypt() → decrypt() round-trip returns original value
- Unit test: Token refresh scheduling triggers before expiry
- Integration test with MockWebServer:
  - Mock Spotify token endpoint at /api/token
  - Call callback with auth code → verify Family created, refresh_token stored (encrypted)
  - Call getValidAccessToken → verify returns access token from mock
  - Simulate expired token → verify automatic refresh happens
- curl http://localhost:8080/api/v1/auth/status → {"authenticated":false,"spotifyConnected":false}
```

### Prompt 1.3 – Profile CRUD

```
CONTEXT: Phase 1 of KidsTune. Backend scaffold (1.1) and Spotify OAuth (1.2) exist.
We need CRUD endpoints for child profiles.

GOAL: When this task is done:
- ChildProfile JPA entity with all fields from §4.1: id (UUID, auto-generated), family_id FK,
  name (required, 1-50 chars), avatar_icon (ENUM: BEAR, FOX, BUNNY, OWL, CAT, PENGUIN),
  avatar_color (ENUM: RED, BLUE, GREEN, PURPLE, ORANGE, PINK), age_group (ENUM: TODDLER, PRESCHOOL, SCHOOL),
  created_at, updated_at (auto-managed)
- ProfileRepository extends JpaRepository
- ProfileService with business logic:
  - createProfile: validates uniqueness of name within family, max 6 profiles per family
  - updateProfile: validates same constraints
  - deleteProfile: cascades to AllowedContent, Favorites, ContentRequests (DB cascade handles this)
  - listProfiles: returns all profiles for a given familyId
- ProfileController with REST endpoints (see §6.1 Profiles):
  - GET /api/v1/profiles → list (familyId resolved from authenticated context)
  - POST /api/v1/profiles → create { name, avatarIcon, avatarColor, ageGroup }
  - PUT /api/v1/profiles/{id} → update (same body)
  - DELETE /api/v1/profiles/{id} → delete with cascade
- Jakarta Bean Validation annotations on the request DTO
- Error responses as { "error": "message", "code": "ERROR_CODE" }
  e.g., { "error": "Profile name already exists", "code": "DUPLICATE_NAME" }

REFERENCE: PROJECT_PLAN.md §4.1 (ChildProfile entity), §6.1 Profiles endpoints,
§5.1.3 (profile avatars: animal icons + colors). CLAUDE.md coding conventions for backend.

CONSTRAINTS:
- Do NOT implement content management (phase 2)
- Do NOT implement device pairing or assignment
- Family ID is derived from the authenticated Spotify user for now (use a simple
  request header X-Family-Id for testing until JWT auth is implemented in 1.4)
- Do NOT add Android code

VERIFICATION:
- Unit tests for ProfileService: create (happy path), create with duplicate name (error),
  create 7th profile (error, max 6), update, delete
- Integration test with Testcontainers:
  - POST /api/v1/profiles with valid body → 201, verify in DB
  - GET /api/v1/profiles → returns created profile
  - PUT /api/v1/profiles/{id} → 200, verify updated in DB
  - DELETE /api/v1/profiles/{id} → 204, verify gone from DB
  - POST with missing name → 400 with validation error
- Run ./gradlew test → all tests pass
```

### Prompt 1.4 – Spring Security JWT

```
CONTEXT: Phase 1 of KidsTune. Backend has scaffold (1.1), OAuth (1.2), profiles (1.3).
We need JWT-based authentication to secure endpoints for different client types.

GOAL: When this task is done:
- JwtTokenService that:
  - Issues JWT device tokens: createDeviceToken(familyId, deviceId, deviceType) → JWT string
  - Tokens contain claims: familyId, deviceId, deviceType (KIDS or PARENT), iat, exp
  - Token lifetime: 30 days for device tokens (long-lived, refreshable)
  - Validates tokens: validateToken(jwt) → claims or throw
  - Signs with HMAC-SHA256 using KIDSTUNE_JWT_SECRET env var
- JwtAuthenticationFilter (WebFilter for WebFlux) that:
  - Reads Authorization: Bearer {token} header
  - Validates token and sets SecurityContext with familyId, deviceId, deviceType
  - Passes through if no token (for public endpoints)
- SecurityConfig updated with endpoint security rules:
  - PUBLIC (no auth): /actuator/health, /api/v1/auth/spotify/*, /api/v1/auth/pair/confirm
  - PARENT auth (Spotify-authenticated or parent device token): /api/v1/profiles/**,
    /api/v1/profiles/*/content/**, /api/v1/content/**, /api/v1/spotify/**,
    /api/v1/content-requests (GET, PUT), /api/v1/auth/pair (POST)
  - KIDS device auth (device token with type=KIDS): /api/v1/sync/**,
    /api/v1/profiles/*/favorites/**, /api/v1/content-requests (POST),
    /api/v1/spotify/search
  - All WebSocket endpoints (/ws/**) require valid device token
- Utility to extract familyId and profileId from SecurityContext in controllers

REFERENCE: PROJECT_PLAN.md §2.3 (Authentication Flow), §6.1 (endpoint list with auth requirements).
CLAUDE.md Spring Security 7 note in pitfalls.

CONSTRAINTS:
- Do NOT implement the device pairing flow itself (phase 5) – just the token issuance/validation
- Do NOT implement WebSocket endpoints (phase 7)
- Do NOT modify Android code
- Use Spring Security 7 API (ServerHttpSecurity for WebFlux, not HttpSecurity)

VERIFICATION:
- Unit test: createDeviceToken → validateToken round-trip returns correct claims
- Unit test: expired token → validation throws
- Unit test: tampered token → validation throws
- Integration tests:
  - GET /api/v1/profiles without token → 401
  - GET /api/v1/profiles with valid parent token → 200
  - GET /api/v1/profiles with valid kids token → 403 (wrong role)
  - GET /actuator/health without token → 200 (public)
- Run ./gradlew test → all tests pass
```

---

## Phase 2 – Content Management Backend + Parent App Shell (Weeks 2-3)

### Prompt 2.1 – AllowedContent Backend

```
CONTEXT: Phase 2 of KidsTune. Backend foundation is complete (phase 1). We now implement the core
content management system. AllowedContent is per-profile (each child has their own whitelist).

GOAL: When this task is done:
- AllowedContent JPA entity with all fields from §4.1
- ContentRepository extends JpaRepository with custom queries:
  - findByProfileId(profileId)
  - findByProfileIdAndContentType(profileId, type)
  - findByProfileIdAndSpotifyUri(profileId, uri) → for uniqueness check
  - existsByProfileIdAndSpotifyUri(profileId, uri)
- ScopeResolver service implementing the isAllowed(trackUri, profileId) algorithm from §4.2:
  Step 1: direct track match, Step 2: album match, Step 3: artist match, Step 4: playlist match
  (Note: Steps 2-4 will call Spotify API via SpotifyApiClient – for now, mock this dependency.
  Full Spotify integration comes in prompt 2.3)
- ContentService with:
  - addContent(profileId, spotifyUri, scope, contentTypeOverride?) → creates AllowedContent,
    validates no duplicate (same profile + URI), auto-classifies content type if no override
  - addContentBulk(spotifyUri, scope, profileIds[]) → creates one AllowedContent per profileId
  - removeContent(profileId, contentId) → deletes
  - listContent(profileId, typeFilter?, scopeFilter?, searchQuery?) → filtered list
  - checkContent(profileId, spotifyUri) → calls ScopeResolver.isAllowed()
- ContentController with REST endpoints (see §6.1 Content Management):
  - GET /api/v1/profiles/{profileId}/content (with query params for filters)
  - POST /api/v1/profiles/{profileId}/content
  - POST /api/v1/content/bulk
  - DELETE /api/v1/profiles/{profileId}/content/{id}
  - GET /api/v1/profiles/{profileId}/content/check/{spotifyUri}

REFERENCE: PROJECT_PLAN.md §4.1 (AllowedContent entity), §4.2 (Scope Resolution with full algorithm),
§6.1 Content Management endpoints. CLAUDE.md per-profile content note.

CONSTRAINTS:
- Do NOT implement ContentResolver/background resolution (phase 4)
- Do NOT implement Spotify API calls – use a SpotifyApiClient interface with a mock for tests
- Do NOT implement content type heuristic yet (prompt 2.2)
- Do NOT create Android code
- Content type defaults to MUSIC if no override and no heuristic (heuristic comes next)

VERIFICATION:
- Unit tests for ScopeResolver (30+ test cases):
  - TRACK scope: exact match allowed, different track denied
  - ALBUM scope: track in allowed album → allowed, track in other album → denied
  - ARTIST scope: any track by allowed artist → allowed, track by other artist → denied
  - PLAYLIST scope: track in allowed playlist → allowed, track not in playlist → denied
  - Combined: artist allowed for Luna, not for Max → check returns correctly per profile
  - Edge: track with multiple artists, one is allowed → allowed
- Integration tests: full REST round-trips for all endpoints with Testcontainers
- Bulk add: POST /api/v1/content/bulk with 2 profileIds → verify 2 AllowedContent rows created
- Duplicate prevention: POST same URI for same profile twice → 409 Conflict
- Run ./gradlew test → all tests pass
```

### Prompt 2.2 – Content Type Heuristic

```
CONTEXT: Phase 2 of KidsTune. AllowedContent CRUD exists (2.1). We need automatic classification
of content as MUSIC vs AUDIOBOOK when parents add content without specifying a type.

GOAL: When this task is done:
- ContentTypeClassifier service with classifyContent(SpotifyItem) → ContentType:
  Strong signals (high confidence):
    - Spotify native audiobook type → AUDIOBOOK
    - Genre contains "hörspiel", "audiobook", "spoken word" → AUDIOBOOK
  Medium signals (need corroboration):
    - album.total_tracks > 20 AND average track duration > 5 min → AUDIOBOOK
    - album.name matches /Folge \d+|Episode \d+|Teil \d+|Kapitel \d+/ → AUDIOBOOK
  Children's music:
    - Genre contains "children's music", "kindermusik", "kinderlieder" → MUSIC
  Default → MUSIC
- known-artists.yml configuration file loaded at startup with @ConfigurationProperties:
  Each entry has: name, min_age (int). Example entries as in §5.2.2.
  Used by import heuristic (phase 6) but also by classifier for additional signal.
- KnownChildrenArtists service: loads YAML, provides isKnownChildrenArtist(name) and getMinAge(name)
- ContentService.addContent() now auto-calls classifier if no contentTypeOverride provided
- Manual override: if contentTypeOverride is passed, it takes precedence over heuristic

REFERENCE: PROJECT_PLAN.md §4.3 (Content Type Classification with full heuristic pseudocode),
§5.2.2 (known-artists.yml format with age ranges).

CONSTRAINTS:
- Do NOT implement the import flow (phase 6)
- Do NOT modify Android code
- The classifier receives a SpotifyItem DTO (genres, album info, duration) – it does NOT
  call Spotify API itself. The caller provides the Spotify data.
- Keep the classifier pure/functional – easy to test

VERIFICATION:
- 50+ unit tests for ContentTypeClassifier:
  - Spotify audiobook type → AUDIOBOOK
  - Genre "hörspiel" → AUDIOBOOK
  - Genre "audiobook" → AUDIOBOOK
  - 25 tracks, avg 8 min → AUDIOBOOK
  - Album name "Folge 1 - Das große Abenteuer" → AUDIOBOOK
  - Album name "Kapitel 3" → AUDIOBOOK
  - Genre "kindermusik" → MUSIC
  - 10 tracks, avg 3 min, no special genre → MUSIC (default)
  - Edge: "hörspiel" genre + 5 short tracks → AUDIOBOOK (genre wins)
  - Edge: no genre data, 30 tracks, avg 2 min → MUSIC (short tracks)
- Unit tests for KnownChildrenArtists: loads YAML, finds "Bibi & Tina", returns min_age 3
- Integration test: POST content without type override → verify auto-classified correctly
- Run ./gradlew test → all tests pass
```

### Prompt 2.3 – Spotify Search Proxy

```
CONTEXT: Phase 2 of KidsTune. Content management (2.1) and type heuristic (2.2) exist.
We need the backend to proxy Spotify search for the Parent App and Kids App Discover feature.

GOAL: When this task is done:
- SpotifyApiClient service wrapping Spotify Web API via Spring WebClient:
  - search(query, types[artist,album,playlist], limit) → grouped results
  - getArtist(id) → artist details with genres
  - getArtistAlbums(id) → album list
  - getAlbumTracks(id) → track list
  - getPlaylistTracks(id) → track list
  - getRecentlyPlayed(limit) → recent tracks
  - getTopArtists(timeRange, limit) → top artists
  - getUserPlaylists(limit) → user playlists
  All calls use the access token from SpotifyTokenService.getValidAccessToken()
  All responses cached in Caffeine with TTLs:
    - search results: 1 hour
    - artist/album details: 24 hours
    - playlist tracks: 6 hours
- SpotifySearchService wrapping SpotifyApiClient:
  - search(query) → groups results by type (artists, albums, playlists)
  - Filters out explicit content (Spotify's explicit flag)
  - Limits to 10 results per type
  - Returns enriched DTOs with title, imageUrl, spotifyUri, artistName, type
- SpotifyProxyController:
  - GET /api/v1/spotify/search?q=query&limit=10
  - Returns { artists: [...], albums: [...], playlists: [...] }
- CacheConfig class configuring Caffeine caches with named caches and TTLs

REFERENCE: PROJECT_PLAN.md §2.2 (Spotify API strategy), §6.1 Spotify Proxy endpoints,
§4.2 (cache TTLs). Spotify Web API docs: https://developer.spotify.com/documentation/web-api

CONSTRAINTS:
- Do NOT implement the import flow (phase 6) – just expose the raw API client methods
- Do NOT implement content resolution/resolver (phase 4)
- Do NOT create Android code
- Do NOT call Spotify API in tests – use MockWebServer with JSON fixtures
  stored in src/test/resources/spotify-fixtures/

VERIFICATION:
- Create JSON fixtures: search-bibi.json (search results for "Bibi"), artist-albums.json,
  album-tracks.json, recently-played.json
- Unit tests for SpotifySearchService: groups results correctly, filters explicit content
- Integration tests with MockWebServer:
  - Search "Bibi" → returns grouped results with artists, albums, playlists
  - Verify caching: second search with same query → no second HTTP call to mock
  - Verify explicit content filtered out
- Run ./gradlew test → all tests pass
```

### Prompt 2.4 – Parent App Project Setup + Login

```
CONTEXT: Phase 2 of KidsTune. Backend is complete through content management and Spotify proxy.
We now create the Parent App Android project and implement the Spotify login flow.

GOAL: When this task is done:
- parent-app/ Android project with:
  - Kotlin 2.x, target SDK 35, min SDK 28
  - Jetpack Compose with Material 3
  - Hilt for DI
  - Ktor Client configured for backend REST API (base URL configurable in BuildConfig)
  - Compose Navigation with type-safe routes
  - Material 3 theme (standard, not kid-themed – this is for parents)
- Hilt modules: NetworkModule (Ktor client, backend base URL), AppModule
- Navigation graph with routes: Login, Dashboard, ProfileDetail, SearchContent,
  ContentList, ImportHistory, DeviceManagement, ApprovalQueue, Settings
- Login screen:
  - Spotify OAuth PKCE flow: launches browser intent to backend /api/v1/auth/spotify/login
  - Handles callback deep link (kidstune://auth/callback)
  - Stores familyId and token in EncryptedSharedPreferences
  - Navigates to Dashboard on success
  - Shows error state on failure
- LoginViewModel with MVI pattern (states: Idle, Loading, Success, Error)

REFERENCE: PROJECT_PLAN.md §3.1 (Android tech stack), §5.2.1 (Parent App screen flow),
§2.3 (Auth flow steps 1-2). CLAUDE.md Android coding conventions.

CONSTRAINTS:
- Do NOT implement dashboard content (prompt 2.5)
- Do NOT implement any other screens beyond login (just navigation stubs)
- Do NOT create kids-app code
- Do NOT implement the Foreground Service or WebSocket (phase 7)
- Use Material 3 (androidx.compose.material3), NOT Material 2

VERIFICATION:
- App compiles: cd parent-app && ./gradlew assembleDebug
- UI test: LoginScreen renders with Spotify login button
- UI test: navigation graph resolves all routes without crashes
- Unit test: LoginViewModel state transitions (Idle → Loading → Success, Idle → Loading → Error)
- Run ./gradlew test → all tests pass
```

### Prompt 2.5 – Parent App Dashboard + Content Management UI

```
CONTEXT: Phase 2 of KidsTune. Parent App scaffold with login exists (2.4). We now build the
main dashboard and the content search/add/remove screens.

GOAL: When this task is done:
- DashboardScreen showing:
  - Pending requests badge at top (placeholder count for now, real data in phase 7)
  - Profile cards: for each child profile from backend, show avatar icon + color, name,
    content count (e.g., "12 items"). Tapping a card navigates to ProfileDetailScreen.
  - Bottom navigation items: Manage Devices, Import from History, Settings (all stub screens for now)
- DashboardViewModel: fetches profiles from GET /api/v1/profiles on launch
- ProfileDetailScreen showing:
  - Child avatar + name + age group header
  - Actions: Add Content, View Content ({count}), Requests ({pending count}), Edit Profile
  - Content overview summary: "4 Artists, 5 Albums, 2 Playlists, 1 Track"
- SearchContentScreen:
  - Search bar with 300ms debounce
  - Results grouped by type: Artists section, Albums section, Playlists section
  - Each result shows: image, title, artist name
  - Add button with dropdown:
    - For artists: "All content (recommended)" / "Browse albums first..." / "Only music" / "Only audiobooks"
    - For albums/playlists: "Add album" / "Add playlist"
  - Profile target picker after add: "Only for Luna" / "For all children" / "Pick profiles..." (checkboxes)
  - Confirmation toast: "Added Bibi & Tina (all content) to Luna – 48 albums"
- SearchViewModel with states: Idle, Searching, Results(grouped), Error
- ContentListScreen:
  - Lists AllowedContent for the selected profile from GET /api/v1/profiles/{id}/content
  - Filter chips: All / Music / Audiobooks | All / Artists / Albums / Playlists / Tracks
  - Swipe-to-delete with undo snackbar
  - Each item shows: image, title, scope badge (ARTIST/ALBUM/...), content type badge
- ContentListViewModel with filter state management

REFERENCE: PROJECT_PLAN.md §5.2.1 (Parent App screen flow with ASCII wireframes),
§5.2.3 (Content search & add flow with dropdown details), §6.1 Content Management endpoints.
CLAUDE.md MVI architecture pattern.

CONSTRAINTS:
- Do NOT implement the import flow (phase 6)
- Do NOT implement device management (phase 5)
- Do NOT implement approval queue (phase 7)
- Do NOT implement the Foreground Service or notifications
- Stub screens for Import, Devices, Settings (just a centered text "Coming soon")
- Do NOT create kids-app code

VERIFICATION:
- UI test: DashboardScreen shows profile cards fetched from mock repository
- UI test: SearchContentScreen → type "Bibi" → results appear grouped → tap add → profile picker shown
- UI test: ContentListScreen → filter by MUSIC → only music items shown → swipe to delete
- Unit test: SearchViewModel debounce (query change within 300ms → single search call)
- Unit test: DashboardViewModel fetches profiles on init
- Unit test: ContentListViewModel filter logic
- Run ./gradlew test → all tests pass
```

---

## Phase 3 – Kids App UI Shell with Mock Data (Week 4)

### Prompt 3.1 – Kids App Project Setup + Theme

```
CONTEXT: Phase 3 of KidsTune. Backend and Parent App exist. We now create the Kids App
Android project with a kid-friendly design system. This phase uses MOCK DATA ONLY – no
backend connection yet.

GOAL: When this task is done:
- kids-app/ Android project with:
  - Kotlin 2.x, target SDK 35, min SDK 28
  - Jetpack Compose with Material 3
  - Hilt for DI
  - Room database with tables: LocalContentEntry, LocalAlbum, LocalTrack, LocalFavorite
    (schema exactly as in PROJECT_PLAN.md §5.1.5 "Local Room DB schema")
  - Compose Navigation with routes: Setup, Home, Browse(category), NowPlaying, Discover
  - Coil 3 for image loading
- Kid-friendly Material 3 theme:
  - Color scheme: Music=blue/purple, Audiobooks=green/teal, Favorites=pink/red
  - Minimum touch targets: 72dp (custom Modifier.kidsTouchTarget())
  - Large rounded corners (16dp)
  - Typography: minimum 18sp, playful but readable sans-serif
  - Shapes: large rounded corners on cards and buttons
- Reusable components:
  - ContentTile: large card with image (fills card), title below, optional badge overlay
  - FavoriteButton: animated heart icon (tap to add, long-press to remove)
  - PageIndicator: dots showing current page in paginated grid
  - MiniPlayerBar: bottom bar with thumbnail, title, play/pause button
- Mock data provider: MockContentProvider with ~20 hardcoded content entries
  (mix of MUSIC and AUDIOBOOK), ~5 mock albums with tracks, 2 mock profiles

REFERENCE: PROJECT_PLAN.md §5.1.2 (UI design principles), §5.1.5 (Local Room DB schema),
§3.1 (Android tech stack). CLAUDE.md Kids App specifics (72dp targets, color scheme).

CONSTRAINTS:
- Do NOT connect to the backend – all data comes from MockContentProvider
- Do NOT implement Spotify App Remote SDK (phase 4)
- Do NOT implement sync, pairing, or WebSocket
- Do NOT implement the Discover screen (phase 7)
- Room DB exists but is populated from mock data, not from network

VERIFICATION:
- App compiles: cd kids-app && ./gradlew assembleDebug
- Room schema test: in-memory DB creates all 4 tables, basic insert/query works
- Visual inspection: install on target device, verify colors and touch target sizes
- Run ./gradlew test → all tests pass
```

### Prompt 3.2 – Kids App All Screens (Mock Data)

```
CONTEXT: Phase 3 of KidsTune. Kids App scaffold with theme exists (3.1).
We implement all screens with hardcoded mock data for UX validation.

GOAL: When this task is done, every screen in the kids app is navigable with realistic mock data:
- ProfileSelectionScreen (one-time, first launch):
  - Large avatar tiles for each mock profile (e.g., [🐻 Luna] [🦊 Max])
  - Tapping a profile shows confirmation: "Is this Luna's device?" [Yes] [No]
  - On confirm: stores profileId in SharedPreferences, navigates to HomeScreen
  - On subsequent launches: skips directly to HomeScreen (check SharedPreferences)
- HomeScreen:
  - Two large buttons: 🎵 Music (blue) and 📖 Audiobooks (green)
  - One heart button: ❤️ Favorites (pink)
  - Profile avatar in top-left corner showing bound profile
  - MiniPlayerBar at bottom (static, shows mock "now playing" data)
- BrowseScreen(category: MUSIC | AUDIOBOOK | FAVORITES):
  - Paginated 2x2 grid of ContentTile components
  - Each tile shows album art image (from mock URLs), title text below
  - Page indicator dots at bottom
  - Left/right swipe to change pages
  - Tapping a tile navigates to a mock track list, then to NowPlayingScreen
- NowPlayingScreen:
  - Large cover art (fills ~60% of screen)
  - Title + artist name below
  - Play/pause button (large, 72dp), skip forward/back buttons
  - Progress bar (static at 1:23 / 3:45)
  - FavoriteButton (heart, toggles red/gray)
- FavoritesView (in BrowseScreen with category=FAVORITES):
  - Grid of favorited tracks from mock data
  - Empty state: friendly illustration + "Noch keine Lieblingssongs" text
- @Preview composable for EVERY screen with mock data

REFERENCE: PROJECT_PLAN.md §5.1.1 (Screen flow with ASCII wireframes), §5.1.2 (UI design principles),
§5.1.3 (Profile binding, one-time selection). CLAUDE.md kids app UI conventions.

CONSTRAINTS:
- All data is hardcoded mock data – no backend calls, no Room queries for content
  (Room is used only for profile persistence in SharedPreferences)
- Play/pause/skip buttons are non-functional (no Spotify SDK yet)
- Discover button on HomeScreen is HIDDEN (phase 7)
- MiniPlayerBar shows static mock data, no real playback state
- Do NOT implement sync, pairing, or networking

VERIFICATION:
- UI test: first launch → profile selector shown → tap Luna → confirm → HomeScreen
- UI test: second launch (profile already stored) → HomeScreen directly
- UI test: HomeScreen → tap Music → BrowseScreen with mock tiles → tap tile → NowPlayingScreen
- UI test: HomeScreen → tap Audiobooks → BrowseScreen with different mock tiles
- UI test: HomeScreen → tap Favorites → empty state shown (no mock favorites yet)
- UI test: NowPlayingScreen → tap heart → FavoriteButton turns red
- UI test: BrowseScreen pagination → swipe right → page 2 → dots update
- Install on target Samsung phone → hand to child → observe usability
- Run ./gradlew test → all tests pass
```

### Prompt 3.3 – Kids App UI Tests

```
CONTEXT: Phase 3 of KidsTune. All Kids App screens exist with mock data (3.2).
We now write comprehensive Compose UI tests to lock down the UI behavior.

GOAL: When this task is done:
- Compose UI test suite covering all critical interactions:
  1. ProfileSelectionTest:
     - Shows correct number of profiles from mock data
     - Tapping profile shows confirmation dialog
     - Confirming navigates to HomeScreen
     - Subsequent launch skips profile selector
  2. HomeScreenTest:
     - Three category buttons are visible and have correct icons
     - Profile avatar is shown in top-left
     - MiniPlayerBar is visible at bottom
     - Tapping Music navigates to BrowseScreen with MUSIC category
     - Tapping Audiobooks navigates to BrowseScreen with AUDIOBOOK category
     - Tapping Favorites navigates to BrowseScreen with FAVORITES category
  3. BrowseScreenTest:
     - Grid shows 4 tiles per page (2x2)
     - Page indicator shows correct number of dots
     - Swipe right changes to page 2, dots update
     - Swipe left returns to page 1
     - Tapping a tile navigates to NowPlayingScreen
  4. NowPlayingScreenTest:
     - Cover art image is displayed
     - Title and artist text are shown
     - Play/pause, skip forward, skip back buttons exist and have 72dp touch targets
     - FavoriteButton toggles state on tap
     - Progress bar is visible
  5. AccessibilityTest:
     - All images have contentDescription set
     - All interactive elements have minimum 72dp touch target (custom assertion)
     - Text contrast meets WCAG AA (manual check documented)

REFERENCE: PROJECT_PLAN.md §5.1.2 (UI design principles, 72dp touch targets),
§8.2 (Compose UI testing approach). CLAUDE.md test naming convention (backtick descriptive).

CONSTRAINTS:
- Tests use mock data only (MockContentProvider from 3.1)
- Do NOT test networking, sync, or Spotify SDK
- Do NOT write backend tests in this prompt
- Use createComposeRule() for all tests
- Test naming: fun `should navigate to music when music button tapped`()

VERIFICATION:
- cd kids-app && ./gradlew test → all UI tests pass
- Test report shows 15+ test cases across 5 test classes
- No test depends on network or external services
```

---

## Phase 4 – Kids App Plays Real Music (Weeks 5-6)

### Prompt 4.1 – Backend Content Resolver

```
CONTEXT: Phase 4 of KidsTune. Backend has content management and Spotify proxy. When a parent
adds content (e.g., "Bibi & Tina" as ARTIST), the backend must resolve it into concrete albums
and tracks so the Kids App can work fully offline.

GOAL: When this task is done:
- ContentResolver service in the resolver/ package:
  - resolve(AllowedContent entry) → populates ResolvedAlbum and ResolvedTrack tables:
    - ARTIST scope: calls SpotifyApiClient.getArtistAlbums(id) → for each album:
      calls getAlbumTracks(id) → stores ResolvedAlbum + ResolvedTrack rows
    - ALBUM scope: calls getAlbumTracks(id) → stores 1 ResolvedAlbum + ResolvedTrack rows
    - PLAYLIST scope: calls getPlaylistTracks(id) → groups tracks by album →
      stores ResolvedAlbum per unique album + ResolvedTrack rows
    - TRACK scope: calls getTrack(id) → stores 1 ResolvedAlbum (from track's album) + 1 ResolvedTrack
  - Applies ContentTypeClassifier per album (not per track) for content_type field
  - Sets resolved_at timestamp on each ResolvedAlbum
- Background job trigger: when ContentService.addContent() or addContentBulk() is called,
  it fires an @Async method to resolve the newly added entry
- Re-resolution scheduled job: @Scheduled daily at 04:00, re-resolves all ARTIST and PLAYLIST
  scoped entries to pick up new releases. Compares old vs new album list and only adds/removes
  the diff (not a full replace).
- ResolvedAlbumRepository and ResolvedTrackRepository with queries:
  - findByAllowedContentId(contentId) → albums with tracks (for sync)
  - deleteByAllowedContentId(contentId) → cleanup when content removed
- On AllowedContent delete: cascade deletes ResolvedAlbum + ResolvedTrack rows

REFERENCE: PROJECT_PLAN.md §4.1 (ResolvedAlbum/Track tables), §6.1 Sync endpoints
(backend content resolution pipeline), §5.1.5 (why pre-resolved content is needed for offline).

CONSTRAINTS:
- Do NOT implement sync endpoints (prompt 4.2)
- Do NOT modify Kids App code
- Do NOT modify Parent App code
- Use @Async for background resolution (Spring's task executor)
- Handle Spotify API errors gracefully: if resolution fails for one album, skip it and
  log a warning – don't fail the entire resolution

VERIFICATION:
- Integration test with MockWebServer:
  - Add ARTIST content → mock Spotify returns 3 albums with 5 tracks each →
    verify 3 ResolvedAlbum rows and 15 ResolvedTrack rows in DB
  - Add ALBUM content → verify 1 ResolvedAlbum, correct number of ResolvedTrack rows
  - Add PLAYLIST content → verify albums grouped correctly
  - Delete AllowedContent → verify ResolvedAlbum and ResolvedTrack cascade deleted
- Unit test: re-resolution diff logic (old: albums A,B,C; new: albums B,C,D → removes A, adds D, keeps B,C)
- Unit test: content type applied per album (album with genre "hörspiel" → AUDIOBOOK)
- Run ./gradlew test → all tests pass
```

### Prompt 4.2 – Backend Sync Endpoint

```
CONTEXT: Phase 4 of KidsTune. Content resolver exists (4.1). We now implement the sync endpoint
that delivers the complete pre-resolved content tree to kids' devices.

GOAL: When this task is done:
- SyncService building the full sync payload from DB:
  - Collects all AllowedContent for the given profileId
  - For each entry, includes all ResolvedAlbums (ordered by release_date desc)
  - For each album, includes all ResolvedTracks (ordered by disc_number, track_number)
  - Includes all Favorites for the profile
  - Includes profile metadata (name, avatar, age_group)
  - Includes a syncTimestamp (current server time)
- SyncController with endpoints:
  - GET /api/v1/sync/{profileId} → full sync payload (JSON):
    { profile: {...}, favorites: [...], content: [{ entry: {...}, albums: [{ ..., tracks: [...] }] }],
      syncTimestamp: "..." }
  - GET /api/v1/sync/{profileId}/delta?since={timestamp} → delta payload:
    { added: [full trees], updated: [full trees], removed: [entryUris],
      favoritesAdded: [...], favoritesRemoved: [...], syncTimestamp: "..." }
- Delta logic: entries where created_at > since → added; entries where resolved albums have
  resolved_at > since → updated (include full tree); entries deleted since timestamp → removed
  (track deletions via a soft-delete flag or a separate DeletionLog table)

REFERENCE: PROJECT_PLAN.md §6.1 Sync endpoints (full response schema), §5.1.5 (sync strategy,
delta sync details).

CONSTRAINTS:
- Do NOT modify Kids App or Parent App
- Do NOT implement device authentication validation on sync endpoints yet
  (it's wired up but use the test token from Phase 1.4 for now)
- Keep the sync payload efficient: album images are URLs (not base64), track lists are flat

VERIFICATION:
- Integration test with Testcontainers:
  - Create profile → add 2 AllowedContent entries → wait for resolver (use @SpyBean to trigger
    synchronously in test) → GET /api/v1/sync/{profileId} →
    verify JSON contains 2 content entries, each with albums and tracks
  - Verify album ordering (newest first) and track ordering (disc, track number)
  - Add more content → GET delta?since={prevTimestamp} → only new entry in "added"
  - Delete content → GET delta → deleted URI in "removed"
  - Verify favorites included in both full and delta sync
- Performance test: sync with 50 entries, 200 albums, 1000 tracks → response < 500ms
- Run ./gradlew test → all tests pass
```

### Prompt 4.3 – Kids App Sync + Room Storage

```
CONTEXT: Phase 4 of KidsTune. Backend sync endpoint exists (4.2). The Kids App has mock data (phase 3).
We now replace mock data with real data from the backend, stored in Room for offline access.

GOAL: When this task is done:
- KidstuneApiClient (Ktor client) in kids-app/data/remote/:
  - sync(profileId): calls GET /api/v1/sync/{profileId} → returns SyncPayloadDto
  - Uses device token from EncryptedSharedPreferences for Authorization header
  - Base URL configurable in BuildConfig (default: https://kidstune.altenburger.io)
- ContentDao, AlbumDao, TrackDao, FavoriteDao in kids-app/data/local/:
  - ContentDao: insert/replace all, deleteAll, getByType(contentType), getAll, getById
  - AlbumDao: insert/replace all, deleteByContentEntryId, getByContentEntryId (ordered by release_date desc)
  - TrackDao: insert/replace all, deleteByAlbumId, getByAlbumId (ordered by disc, track number)
  - FavoriteDao: insert, delete, getAll, existsByTrackUri
- SyncRepository in kids-app/data/repository/:
  - fullSync(profileId): calls API → maps DTOs to Room entities → stores in single Room transaction
    (delete old data, insert new data, all in @Transaction)
  - On first app launch after pairing: full sync
  - Error handling: if network fails, return cached data from Room
- ContentRepository: reads from Room, provides flows for UI (Flow<List<LocalContentEntry>>,
  Flow<List<LocalAlbum>>, Flow<List<LocalTrack>>)
- Replace MockContentProvider with ContentRepository in all ViewModels:
  - HomeViewModel gets content counts from Room
  - BrowseViewModel gets entries by content type from Room
  - Album/track views read from Room
  - NowPlayingViewModel plays trackUri from LocalTrack in Room
- Coil image loading configured with 200MB disk cache for cover art

REFERENCE: PROJECT_PLAN.md §5.1.5 (Local Room DB schema, complete offline data flow, caching strategy),
§6.1 Sync endpoints (response format).

CONSTRAINTS:
- Do NOT implement WorkManager sync yet (phase 5) – for now, sync is called once on app launch
- Do NOT implement delta sync on client side yet (phase 5) – full sync only
- Do NOT implement Spotify playback yet (prompt 4.4) – just store the track URIs
- Do NOT implement the pairing flow – for MVP, hardcode a test device token in BuildConfig
- Do NOT add the Discover screen

VERIFICATION:
- Integration test: mock backend returns sync payload → verify Room contains correct entries,
  albums, tracks with correct relationships
- Integration test: Room already has data → network fails → verify cached data returned
- UI test: app launch → sync → BrowseScreen shows real album art from Coil (use mock HTTP for images)
- Unit test: ContentRepository flows emit correct data for MUSIC/AUDIOBOOK filters
- Run ./gradlew test → all tests pass
```

### Prompt 4.4 – Spotify Playback Integration

```
CONTEXT: Phase 4 of KidsTune. Kids App shows real content from Room (4.3). We now integrate
the Spotify App Remote SDK so children can actually play music.

GOAL: When this task is done:
- Spotify App Remote SDK added to kids-app (AAR file in kids-app/libs/, added to build.gradle.kts)
- SpotifyRemoteManager in kids-app/playback/:
  - connect(): connects to Spotify app on device, handles lifecycle (auto-reconnect on disconnect)
  - disconnect(): cleanup
  - isConnected: StateFlow<Boolean>
  - connectionError: StateFlow<SpotifyConnectionError?> (NOT_INSTALLED, NOT_LOGGED_IN, PREMIUM_REQUIRED, OTHER)
- PlaybackController in kids-app/playback/:
  - play(trackUri: String): plays a specific track by spotify:track:... URI from Room DB
  - playAlbum(albumUri: String, startTrackIndex: Int): plays album starting at track
  - pause(), resume(), skipNext(), skipPrevious()
  - seekTo(positionMs: Long)
  - nowPlaying: StateFlow<NowPlayingState> (trackUri, title, artist, imageUrl, durationMs, positionMs, isPlaying)
  - Listens to Spotify's PlayerState updates and maps to NowPlayingState
- PlayerViewModel updated to use PlaybackController:
  - NowPlayingScreen shows real track info from nowPlaying StateFlow
  - Play/pause button toggles real playback
  - Skip forward/back works
  - Progress bar updates in real-time (poll every 500ms or use Spotify's callback)
- MiniPlayerBar updated to show real nowPlaying data, tappable to expand to NowPlayingScreen
- BrowseScreen: tapping a tile → starts playing the first track of that album via PlaybackController
  using the trackUri from LocalTrack in Room (NO network call to resolve)

REFERENCE: PROJECT_PLAN.md §2.2 (Spotify App Remote SDK), §5.1.5 (offline playback flow –
track URIs come from Room, not network). Spotify App Remote SDK docs:
https://developer.spotify.com/documentation/android/

CONSTRAINTS:
- Track URIs MUST come from LocalTrack in Room DB, NOT from any network call
- Do NOT call Spotify Web API from the kids app – all data is in Room
- Do NOT implement queueing or playlist management beyond basic skip
- Handle connection errors gracefully: show friendly error screen (detailed error screens come in phase 8)
- The Spotify AAR must be manually downloaded – document the path in a comment

VERIFICATION:
- Integration test: SpotifyRemoteManager connection lifecycle (mock or stub the SDK for unit tests)
- Unit test: PlaybackController state transitions (play → isPlaying=true, pause → isPlaying=false)
- Manual test on real device: tap a tile → music plays from Spotify → pause works → skip works
  → MiniPlayerBar updates → progress bar moves
- Run ./gradlew test → all tests pass (unit tests that don't depend on real Spotify SDK)
```

### Prompt 4.5 – Kids App Real Favorites

```
CONTEXT: Phase 4 of KidsTune. Playback works (4.4). We now implement real favorites that
persist to Room and sync to the backend.

GOAL: When this task is done:
- FavoriteRepository in kids-app/data/repository/:
  - toggleFavorite(track: LocalTrack): if already favorite → remove, else → add
  - Inserts into LocalFavorite table with synced=false
  - isFavorite(trackUri): Flow<Boolean> (used by FavoriteButton)
  - getAllFavorites(): Flow<List<LocalFavorite>> (used by Favorites tab)
- ToggleFavoriteUseCase: wraps FavoriteRepository.toggleFavorite()
- FavoriteButton updated: reads from FavoriteRepository.isFavorite(), calls ToggleFavoriteUseCase on tap
  - Tap → instant Room insert (optimistic UI), heart turns red with bounce animation
  - Long-press → remove with gentle shrink animation
- BrowseScreen with FAVORITES category:
  - Shows grid of LocalFavorite entries with album art
  - Empty state: friendly illustration + "Noch keine Lieblingssongs" text
  - Tapping a favorite tile starts playback (trackUri from LocalFavorite)
- Sync upload: when SyncRepository.fullSync() runs, also:
  - POST /api/v1/profiles/{id}/favorites for each LocalFavorite where synced=false
  - After successful upload: set synced=true
  - Delete locally removed favorites from backend: DELETE /api/v1/profiles/{id}/favorites/{uri}
- FavoriteDao updated: getUnsynced() returns LocalFavorites where synced=false

REFERENCE: PROJECT_PLAN.md §5.1.5 (LocalFavorite schema with synced flag, offline data flow),
§6.1 Favorites endpoints. CLAUDE.md: "No confirmation dialogs – adding is instant, removing needs long-press."

CONSTRAINTS:
- Favorites persist locally even when offline (synced=false, uploaded later)
- Do NOT implement the Discover screen
- Do NOT implement content requests
- Do NOT add WorkManager sync yet (still manual sync on app launch for now)

VERIFICATION:
- Unit test: ToggleFavoriteUseCase adds and removes favorites correctly
- Unit test: FavoriteRepository.isFavorite() emits true after add, false after remove
- Unit test: getUnsynced() returns only favorites with synced=false
- UI test: NowPlayingScreen → tap heart → Favorites tab shows the track → tap heart again → removed
- Integration test: add favorite (synced=false) → run sync → verify POST called → synced=true
- Run ./gradlew test → all tests pass
```

---

## Phase 5 – Device Pairing & Sync (Week 7)

### Prompt 5.1 – Backend Device Pairing

```
CONTEXT: Phase 5 of KidsTune. The MVP works with hardcoded tokens. We now implement proper
device pairing so kids' devices securely pair with the backend using a 6-digit code.

GOAL: When this task is done:
- DevicePairingService in auth/ package:
  - generatePairingCode(familyId) → generates cryptographically random 6-digit numeric code,
    stores in DB with 5-minute expiry, returns code
  - confirmPairing(code, deviceName) → validates code not expired, creates PairedDevice row,
    issues JWT device token (from JwtTokenService), returns { deviceToken, familyId, profiles[] }
  - Codes are single-use: deleted after successful confirmation
  - Rate limit: max 5 active pairing codes per family (prevent spam)
- DevicePairingController:
  - POST /api/v1/auth/pair → requires parent auth → returns { code: "123456", expiresAt: "..." }
  - POST /api/v1/auth/pair/confirm → public endpoint → { code, deviceName } → returns device token
- DeviceService:
  - listDevices(familyId) → returns all paired devices with profile info and last_seen_at
  - unpairDevice(deviceId) → deletes PairedDevice, invalidates token
  - reassignProfile(deviceId, newProfileId) → updates PairedDevice.profile_id
  - updateLastSeen(deviceId) → called on each API request from device (via filter)
- DeviceController:
  - GET /api/v1/devices → list paired devices
  - DELETE /api/v1/devices/{id} → unpair
  - PUT /api/v1/devices/{id}/profile → reassign { profileId }

REFERENCE: PROJECT_PLAN.md §2.3 (Authentication Flow, pairing steps 3-5),
§5.1.3 (profile binding), §6.1 Auth & Pairing endpoints.

CONSTRAINTS:
- Do NOT modify Kids App or Parent App UI (next prompts)
- Pairing codes are numeric only (easy for kids to enter on a number pad)
- Device tokens are JWT signed with the same secret as other tokens

VERIFICATION:
- Unit test: code generation produces 6-digit numeric codes, all unique
- Unit test: code expires after 5 minutes
- Unit test: code is single-use (second confirm attempt fails)
- Integration test: generate code → confirm within 5 min → device token returned → token is valid JWT
- Integration test: generate code → wait 5+ min → confirm fails with 410 Gone
- Integration test: list devices → shows newly paired device
- Integration test: unpair → device token no longer works (401)
- Run ./gradlew test → all tests pass
```

### Prompt 5.2 – Backend Delta Sync

```
CONTEXT: Phase 5 of KidsTune. Full sync exists (4.2). We now implement efficient delta sync
so kids' devices only download changes, not the full content tree every time.

GOAL: When this task is done:
- DeletionLog table (new Liquibase changelog 002-deletion-log.yaml):
  - id (PK), profile_id, spotify_uri, deleted_at
  - Populated by ContentService.removeContent() before deleting the AllowedContent row
  - Cleaned up by a scheduled job (delete entries older than 30 days)
- SyncService.deltaSync(profileId, since: Instant) returns:
  - added: AllowedContent entries where created_at > since, with full resolved album/track trees
  - updated: entries where any ResolvedAlbum.resolved_at > since (e.g., re-resolved artist
    got new albums), with full updated trees
  - removed: URIs from DeletionLog where deleted_at > since
  - favoritesAdded: Favorites where added_at > since
  - favoritesRemoved: favorite URIs from a FavoriteDeletionLog where deleted_at > since
  - syncTimestamp: current server time
- FavoriteDeletionLog table (same pattern as DeletionLog but for favorites)
- SyncController.deltaSync endpoint updated to use the new logic

REFERENCE: PROJECT_PLAN.md §5.1.5 (sync strategy: delta sync details),
§6.1 Sync endpoints (delta response format).

CONSTRAINTS:
- Do NOT modify the full sync endpoint (it still works as before)
- Do NOT modify Android apps
- DeletionLog is an append-only table – never update, only insert and periodically clean up

VERIFICATION:
- Integration test: add content → full sync → add more → delta sync(since=firstSync) →
  only new content in "added", nothing in "removed"
- Integration test: delete content → delta sync → deleted URI in "removed"
- Integration test: re-resolve artist (mock new album) → delta sync → entry in "updated" with new album
- Integration test: add favorite → delta sync → favorite in "favoritesAdded"
- Unit test: DeletionLog cleanup job removes entries older than 30 days
- Run ./gradlew test → all tests pass
```

### Prompt 5.3 – Kids App Pairing Flow

```
CONTEXT: Phase 5 of KidsTune. Backend pairing exists (5.1). We now implement the pairing UI
in the Kids App, replacing the hardcoded test token.

GOAL: When this task is done:
- PairingScreen in kids-app/ui/setup/:
  - Large number pad with 72dp buttons (kid-friendly, but parent will enter the code)
  - 6 input fields showing entered digits
  - "Connect" button enabled when 6 digits entered
  - Loading state while confirming with backend
  - Error state: "Code expired – ask parent for a new code" / "Invalid code"
  - Success: stores device token in EncryptedSharedPreferences, navigates to ProfileSelectionScreen
- PairingViewModel with states: EnteringCode, Confirming, Success(profiles), Error(message)
- App launch flow updated:
  - Check EncryptedSharedPreferences for device token
  - If no token → PairingScreen
  - If token but no profile → ProfileSelectionScreen (one-time)
  - If token + profile → HomeScreen (normal launch)
- Remove hardcoded test token from BuildConfig
- KidstuneApiClient updated: reads device token from EncryptedSharedPreferences for all API calls

REFERENCE: PROJECT_PLAN.md §5.1.3 (profile binding, one-time setup flow),
§2.3 (Authentication Flow, step 4). CLAUDE.md: 72dp touch targets.

CONSTRAINTS:
- Do NOT implement WorkManager sync yet (prompt 5.4)
- Do NOT implement the Discover screen
- The pairing screen is designed for parent input (not child) but must be visually consistent
  with the kids app theme

VERIFICATION:
- UI test: PairingScreen shows number pad → enter 6 digits → Connect button enabled
- UI test: invalid code → error message shown → can retry
- Unit test: PairingViewModel state transitions (EnteringCode → Confirming → Success)
- Integration test: full pairing flow with mock backend → token stored → profile selection shown
- Run ./gradlew test → all tests pass
```

### Prompt 5.4 – Kids App Sync Manager (WorkManager)

```
CONTEXT: Phase 5 of KidsTune. Pairing works (5.3), delta sync backend exists (5.2).
We now implement automatic background sync using WorkManager.

GOAL: When this task is done:
- SyncWorker (CoroutineWorker) in kids-app/sync/:
  - On execute: reads lastSyncTimestamp from SharedPreferences
  - If lastSyncTimestamp exists: calls delta sync endpoint
  - If no timestamp (first sync): calls full sync endpoint
  - Maps response to Room entities, applies in single @Transaction:
    - added: insert new LocalContentEntry + LocalAlbum + LocalTrack rows
    - updated: delete old albums/tracks for entry, insert new ones
    - removed: delete LocalContentEntry + cascaded albums/tracks by URI
    - favoritesAdded: insert LocalFavorite with synced=true
    - favoritesRemoved: delete LocalFavorite by URI
  - Uploads queued favorites (synced=false → POST → set synced=true)
  - Uploads queued content requests (if any)
  - Updates lastSyncTimestamp on success
  - Returns Result.success() or Result.retry() on network error
- SyncManager in kids-app/sync/:
  - registerPeriodicSync(): PeriodicWorkRequest every 15 minutes, constraints: CONNECTED network
  - syncNow(): OneTimeWorkRequest for immediate sync (called on app launch)
  - Registered in Application.onCreate() or via Hilt initializer
- OfflineQueue in kids-app/sync/:
  - Stores failed favorite uploads and content requests in Room
  - SyncWorker drains the queue on each run
- Conflict resolution: server wins for content (replace local), merge for favorites
  (keep local additions, accept server deletions)
- App launch flow updated: on HomeScreen appearance, call SyncManager.syncNow()

REFERENCE: PROJECT_PLAN.md §5.1.5 (sync strategy diagram, delta application logic,
conflict resolution rules).

CONSTRAINTS:
- Do NOT implement WebSocket listener (phase 7) – sync is purely poll-based for now
- Do NOT implement the Discover screen
- WorkManager is the ONLY background execution mechanism – no AlarmManager or custom threads

VERIFICATION:
- Unit test: delta application logic (added/updated/removed correctly applied to Room)
- Unit test: offline queue: add favorite offline → queue has 1 entry → sync drains it
- Unit test: conflict resolution: server removes favorite that's locally added → server wins
- Integration test: mock backend → SyncWorker runs → Room updated correctly
- Integration test: network failure → SyncWorker returns retry → WorkManager reschedules
- Run ./gradlew test → all tests pass
```

### Prompt 5.5 – Parent App Device Management

```
CONTEXT: Phase 5 of KidsTune. Backend pairing and sync work. We now add device management
to the Parent App.

GOAL: When this task is done:
- DeviceScreen in parent-app/ui/devices/:
  - List of paired devices from GET /api/v1/devices
  - Each item shows: device name, bound profile (avatar + name), last seen (relative time),
    online/offline indicator (green dot if last_seen < 5 min, gray otherwise)
  - "Generate Pairing Code" button → calls POST /api/v1/auth/pair →
    shows large 6-digit code in a dialog with countdown timer (5 min)
  - Per-device actions (long-press or menu):
    - "Reassign Profile" → profile picker dialog → PUT /api/v1/devices/{id}/profile
    - "Unpair Device" → confirmation dialog → DELETE /api/v1/devices/{id}
- DeviceViewModel with states: Loading, Loaded(devices), Error
- PairingCodeDialog: shows code in large, monospace digits (easy to read across the room),
  countdown timer, "Code copied" on tap
- Dashboard updated: "Manage Devices" button navigates to DeviceScreen

REFERENCE: PROJECT_PLAN.md §5.2.1 (screen flow), §12.1 (setup procedure step 5).

CONSTRAINTS:
- Do NOT implement notification service or WebSocket (phase 7)
- Do NOT implement import flow (phase 6)
- Do NOT modify kids-app code

VERIFICATION:
- UI test: DeviceScreen shows list of mock devices
- UI test: "Generate Pairing Code" → dialog shows 6-digit code with timer
- UI test: Reassign profile → picker shown → confirm → API called
- Unit test: DeviceViewModel fetches devices on init
- Run ./gradlew test → all tests pass
```

---

## Phase 6 – Import, Offline & Samsung Kids (Week 8)

### Prompt 6.1 – Backend Import Service

```
CONTEXT: Phase 6 of KidsTune. We implement the Spotify listening history import to ease
onboarding for new families.

GOAL: When this task is done:
- SpotifyImportService in spotify/ package:
  - getImportSuggestions(familyId) → calls Spotify Web API:
    - GET /v1/me/player/recently-played (limit 50)
    - GET /v1/me/top/artists?time_range=medium_term (limit 50)
    - GET /v1/me/top/artists?time_range=long_term (limit 50)
    - GET /v1/me/playlists (limit 50)
  - Deduplicates artists across all sources
  - Groups results into:
    - detectedChildrenContent: items matching children's heuristic (pre-selected)
    - playlists: user playlists containing likely children's content
    - otherArtists: everything else (not pre-selected)
  - Per item, per profile: suggests inclusion based on age matching:
    - Loads known-artists.yml min_age
    - Compares with each profile's age_group (TODDLER=0-3, PRESCHOOL=4-6, SCHOOL=7-12)
    - e.g., "Die drei ??? Kids" (min_age 6) pre-selected for SCHOOL, not for TODDLER
  - Returns ImportSuggestionsDto with per-item per-profile selection flags
- POST /api/v1/content/import endpoint (already defined) now fully implemented:
  - Accepts { items: [{ spotifyUri, scope, profileIds[] }] }
  - Creates AllowedContent rows per profile
  - Triggers ContentResolver for each new entry
  - Returns { created: int, profiles: [{ id, name, newContentCount }] }

REFERENCE: PROJECT_PLAN.md §5.2.2 (import flow with per-profile age-based pre-selection),
§5.2.2 (known-artists.yml format with age ranges), §6.1 Content Management import endpoint.

CONSTRAINTS:
- Do NOT modify Android apps
- Genre-based heuristic reuses ContentTypeClassifier from phase 2
- Known-artists.yml reuses KnownChildrenArtists from phase 2

VERIFICATION:
- Unit test: age-based pre-selection logic:
  - "Bibi & Tina" (min_age 3) → selected for TODDLER, PRESCHOOL, SCHOOL
  - "Die drei ??? Kids" (min_age 6) → NOT for TODDLER, YES for PRESCHOOL, YES for SCHOOL
  - "Die drei ???" (min_age 10) → NOT for TODDLER, NOT for PRESCHOOL, YES for SCHOOL
- Integration test with MockWebServer: mock Spotify responses → verify grouped results
  with correct pre-selection per profile
- Integration test: POST /api/v1/content/import with 3 items, 2 profiles →
  verify 6 AllowedContent rows created, ContentResolver triggered 6 times
- Run ./gradlew test → all tests pass
```

### Prompt 6.2 – Parent App Import Wizard

```
CONTEXT: Phase 6 of KidsTune. Backend import service exists (6.1). We build the import UI.

GOAL: When this task is done:
- ImportScreen in parent-app/ui/import_/:
  - Step 1: Profile selector – checkboxes for each profile, "Select all" option, Continue button
  - Step 2: Loading state while backend fetches suggestions, then displays:
    - "Detected children's content" section (pre-checked items):
      Each item shows: image, name, play count, "Will add as: Artist (all content)",
      per-profile toggle chips (pre-selected based on age heuristic from backend)
    - "Your playlists" section with per-profile toggles
    - "Other artists" section (unchecked by default)
    - Each toggle chip shows profile avatar + name, colored when selected
  - Step 3: Review summary ("Importing 5 items for Luna, 8 items for Max") + Import button
  - Import progress: progress bar while backend processes
  - Success: "Done! Added X items for Luna, Y items for Max" with confetti animation
- ImportViewModel: multi-step state machine (SelectProfiles → Loading → Suggestions → Importing → Done)
- Dashboard "Import from History" button navigates to ImportScreen

REFERENCE: PROJECT_PLAN.md §5.2.2 (import flow wireframe with per-profile toggles and age hints).

CONSTRAINTS:
- Do NOT modify backend or kids-app
- Do NOT implement device management changes
- Step 2 data comes from a new GET /api/v1/content/import/suggestions endpoint
  (add this endpoint to the backend in the same prompt if needed)

VERIFICATION:
- UI test: Step 1 → select profiles → Step 2 → pre-checked items shown with profile chips →
  toggle a chip → Step 3 → import → success screen
- Unit test: ImportViewModel state machine transitions
- Unit test: profile chip selection logic (toggle on/off, "select all" toggles all)
- Run ./gradlew test → all tests pass
```

### Prompt 6.3 – Kids App Offline Hardening

```
CONTEXT: Phase 6 of KidsTune. The kids app works with sync and playback. We now stress-test
and harden offline behavior.

GOAL: When this task is done:
- Offline indicator: small cloud-with-strikethrough icon in top-right corner of HomeScreen,
  visible when device has no network connectivity. Uses ConnectivityManager callback.
  Subtle, does not block any interaction.
- Cold start in airplane mode:
  - App launches, skips sync (SyncWorker detects no network → Result.retry())
  - Shows all content from Room cache with cover art from Coil disk cache
  - All navigation works (browse → album → tracks)
  - Playback works if Spotify has cached the audio
- Wi-Fi loss during active use:
  - If sync is in progress → SyncWorker fails gracefully, retries later
  - If playing music → Spotify handles its own offline mode (we don't interfere)
  - If adding favorite → stored in Room with synced=false
  - If making content request → stored in OfflineQueue
- Stale content indicator: if lastSyncTimestamp > 24 hours ago AND device is online,
  show subtle yellow dot on HomeScreen (not blocking, just informational)
- First launch with no cache (brand new device, no internet):
  - Special screen: friendly airplane illustration + "Bitte mit WLAN verbinden"
  - Shows only when: no device token OR device token exists but Room has 0 content entries

REFERENCE: PROJECT_PLAN.md §5.1.5 (complete offline data flow, caching strategy table,
sync strategy diagram).

CONSTRAINTS:
- Do NOT change the backend
- Do NOT add any new features – this is purely about resilience and edge cases
- Do NOT block user interaction for any error state (except the "no cache ever" first launch)

VERIFICATION:
- Integration test: airplane mode cold start → app launches → Room data displayed
- Integration test: SyncWorker with no network → returns Result.retry()
- Integration test: add favorite offline → stored with synced=false → reconnect → synced
- Unit test: ConnectivityManager callback → offline indicator shown/hidden
- Unit test: stale content detection (lastSync > 24h)
- Manual test: toggle airplane mode on/off during use → document results
- Run ./gradlew test → all tests pass
```

### Prompt 6.4 – Samsung Kids Compatibility

```
CONTEXT: Phase 6 of KidsTune. The kids app is feature-complete for the core flow.
We verify it works correctly inside Samsung Kids.

GOAL: When this task is done:
- Documented and tested Samsung Kids compatibility:
  - Activity lifecycle: app correctly saves/restores state when Samsung Kids
    pauses/resumes it (e.g., time limit pause, parent exits Samsung Kids temporarily)
  - Audio focus: app handles AUDIOFOCUS_LOSS and AUDIOFOCUS_GAIN correctly
    (Spotify SDK handles most of this, but verify no crashes)
  - Spotify background process: verify Spotify continues running as a background service
    when Samsung Kids is active and only KidsTune Kids is in the allowed apps list
  - Time limit reached: verify app saves state (current playback position, profile)
    when Samsung Kids forcefully stops it
  - App restart: verify app resumes correctly after Samsung Kids restart
- Fix any issues found during testing
- README.md section: "Samsung Kids Setup" with step-by-step instructions:
  1. Install Spotify + KidsTune Kids
  2. Log in to Spotify once (outside Samsung Kids)
  3. Download children's content for offline use in Spotify
  4. Open Samsung Kids settings → add KidsTune Kids as allowed app
  5. Do NOT add Spotify to allowed apps
  6. Set PIN and daily time limits
  7. Verify: open Samsung Kids → KidsTune Kids launches → play music → works

REFERENCE: PROJECT_PLAN.md §5.1.4 (Samsung Kids containment),
§12.1 (deployment setup procedure steps 5-6).

CONSTRAINTS:
- This is primarily a testing and documentation task
- Fix only issues that prevent correct operation inside Samsung Kids
- Do NOT implement kiosk mode fallback (documented as future option, not implemented now)
- Do NOT change the backend

VERIFICATION:
- Manual test checklist (documented in README):
  ☐ App launches correctly inside Samsung Kids
  ☐ Music plays via Spotify background process
  ☐ Pause/resume works when switching between Samsung Kids screens
  ☐ State preserved when Samsung Kids time limit pauses the app
  ☐ App restarts cleanly after Samsung Kids restart
  ☐ Spotify is NOT visible in Samsung Kids (only KidsTune Kids is)
  ☐ Child cannot exit to Spotify or other apps
```

### Prompt 6.5 – Spotify Offline Download Guidance

```
CONTEXT: Phase 6 of KidsTune. Samsung Kids compatibility is verified (6.4). We add guidance
for parents to download Spotify content for reliable offline playback on kids' devices.

GOAL: When this task is done:
- Parent App Settings screen includes a new section: "Offline Playback Tips"
  - Explains that Spotify needs to download content for reliable offline use
  - Step-by-step: "1. Exit Samsung Kids on the kids' device, 2. Open Spotify,
    3. Go to [child]'s content, 4. Tap download, 5. Re-enter Samsung Kids"
  - Shows which profiles have the most content (suggesting which to prioritize)
- README.md updated with offline playback section explaining the Spotify caching model
- Future consideration documented (not implemented): backend generates a Spotify playlist
  per profile containing all allowed tracks, which parents can download with one tap

REFERENCE: PROJECT_PLAN.md §5.1.5 ("What happens if Spotify hasn't cached the audio?"),
§12.2 (ongoing maintenance: "Spotify offline content" row).

CONSTRAINTS:
- This is a documentation and minor UI task
- Do NOT implement automatic Spotify download triggering (not possible via SDK)
- Do NOT change backend logic

VERIFICATION:
- UI test: Settings screen shows "Offline Playback Tips" section
- README has clear offline playback instructions
- Run ./gradlew test → all tests pass
```

---

## Phase 7 – Content Requests & Live Notifications (Weeks 9-10)

### Prompt 7.1 – Backend WebSocket Hub

```
CONTEXT: Phase 7 of KidsTune. We implement real-time communication between backend
and both apps using WebSocket.

GOAL: When this task is done:
- WebSocketConfig in config/ registering WebSocket endpoints with Spring WebFlux
- WebSocketHandler in ws/ package:
  - Accepts connections at /ws/parent/{familyId} and /ws/kids/{deviceId}
  - Validates JWT device token from query parameter (?token=...)
  - Maintains bidirectional message channel
- ConnectionRegistry in ws/ package:
  - registerParent(familyId, session), deregisterParent(familyId, session)
  - registerKids(deviceId, session), deregisterKids(deviceId, session)
  - sendToParent(familyId, message) → sends to all connected parent sessions for that family
  - sendToDevice(deviceId, message) → sends to specific kids device
  - isParentConnected(familyId): boolean
  - getConnectedDeviceCount(familyId): int
  - Automatic cleanup of stale sessions
- Heartbeat: server sends PING every 30s. If no PONG within 10s, connection dropped and deregistered.
- Message format: JSON matching §6.3 WebSocket message format
  (type, timestamp, payload fields)

REFERENCE: PROJECT_PLAN.md §6.2 (WebSocket endpoints), §6.3 (message format).

CONSTRAINTS:
- Do NOT implement content request handling (prompt 7.2)
- Do NOT modify Android apps
- WebSocket runs on the same port as REST (Spring WebFlux handles both)
- Use reactive WebSocket API (not the older blocking API)

VERIFICATION:
- Integration test: connect WebSocket → send message → verify received
- Integration test: connect → disconnect → verify deregistered from ConnectionRegistry
- Integration test: heartbeat timeout → connection dropped
- Integration test: sendToParent with 2 connected sessions → both receive message
- Integration test: invalid token → connection rejected
- Run ./gradlew test → all tests pass
```

### Prompt 7.2 – Backend Content Request Workflow

```
CONTEXT: Phase 7 of KidsTune. WebSocket hub exists (7.1). We implement the content request
lifecycle: kids request → parents notified → approve/reject → content created.

GOAL: When this task is done:
- ContentRequestService in requests/ package:
  - createRequest(profileId, spotifyUri, title, imageUrl, artistName):
    - Validates max 3 PENDING requests per profile (throw 429 if exceeded)
    - Creates ContentRequest with status=PENDING
    - Dispatches CONTENT_REQUEST message via WebSocket to parent (ConnectionRegistry.sendToParent)
    - Returns created request
  - approveRequest(requestId, approvedByProfileIds?, note?):
    - Sets status=APPROVED, resolved_at=now, resolved_by
    - Creates AllowedContent for the requesting profile (or specified profiles)
    - Triggers ContentResolver to populate albums/tracks
    - Dispatches REQUEST_APPROVED via WebSocket to kids device
  - rejectRequest(requestId, note?):
    - Sets status=REJECTED, resolved_at=now, parent_note
    - Dispatches REQUEST_REJECTED via WebSocket to kids device
  - bulkApprove/bulkReject(requestIds[], status, note?)
  - listRequests(familyId, statusFilter?, profileFilter?) → paginated list
  - getPendingCount(familyId) → { profiles: [{ id, name, count }], total: int }
- ContentRequestController with endpoints from §6.1 Content Requests
- Scheduled jobs:
  - expireStaleRequests: @Scheduled(cron = "0 0 3 * * *")
    - Sets PENDING requests older than 7 days to EXPIRED
    - Dispatches REQUEST_EXPIRED via WebSocket to kids devices
  - sendDailyDigest: @Scheduled(cron = "0 0 19 * * *")
    - For each family with PENDING requests older than 4 hours:
    - Builds DAILY_DIGEST message with per-child summary
    - Sends via WebSocket (if parent connected)
    - Sets digest_sent_at on the requests (for polling detection)

REFERENCE: PROJECT_PLAN.md §5.1.6 (request limits, auto-expiry), §5.2.3 (three-layer
notification strategy, daily digest), §6.1 Content Requests API (lifecycle diagram, endpoints).

CONSTRAINTS:
- Do NOT modify Android apps
- Do NOT implement the polling endpoint for Layer 2 (it's GET /pending/count, already defined)
- ContentResolver from phase 4 handles the actual content resolution after approval

VERIFICATION:
- Integration test: create request → verify PENDING in DB → verify WebSocket message dispatched
- Integration test: 4th request for same profile → 429 status
- Integration test: approve → AllowedContent created → ContentResolver triggered →
  REQUEST_APPROVED dispatched
- Integration test: reject with note → status REJECTED, parent_note stored
- Integration test: expire job → requests older than 7 days become EXPIRED
- Integration test: daily digest → builds correct summary for family with 3 pending requests
- Integration test: bulk approve 3 requests → all become APPROVED
- Run ./gradlew test → all tests pass
```

### Prompt 7.3 – Backend Scheduled Jobs Tests

```
CONTEXT: Phase 7 of KidsTune. Content request workflow exists (7.2). We add thorough tests
for the scheduled jobs (expiry and daily digest) since they run unattended.

GOAL: When this task is done:
- Comprehensive test suite for expireStaleRequests():
  - Request exactly 7 days old → EXPIRED
  - Request 6 days 23 hours old → still PENDING (boundary)
  - Request already APPROVED → not touched
  - Request already REJECTED → not touched
  - Multiple requests across profiles → each handled independently
  - WebSocket dispatch verified for each expired request
- Comprehensive test suite for sendDailyDigest():
  - Family with 2 pending requests (both > 4h) → digest sent with correct counts
  - Family with 1 pending request < 4h old → no digest
  - Family with requests in APPROVED/REJECTED/EXPIRED → not included in digest
  - Multiple families → each gets their own digest
  - digest_sent_at set on affected requests
  - Digest message format matches §6.3 (DAILY_DIGEST type with payload)
- Test that both jobs can be triggered manually (for debugging) via an admin endpoint

REFERENCE: PROJECT_PLAN.md §5.2.3 (Layer 3 daily digest details),
§5.1.6 (auto-expiry after 7 days).

CONSTRAINTS:
- Do NOT modify Android apps
- These are purely backend tests
- Use @SpyBean or direct service calls to trigger jobs in tests (not actual cron)

VERIFICATION:
- All edge case tests pass for both scheduled jobs
- Boundary conditions explicitly tested (exactly 7 days, exactly 4 hours)
- Run ./gradlew test → all tests pass including 15+ new test cases
```

### Prompt 7.4 – Kids App Discover Screen

```
CONTEXT: Phase 7 of KidsTune. Backend content requests work (7.2). We now build the Discover
screen in the Kids App where children can search and request new content.

GOAL: When this task is done:
- DiscoverScreen in kids-app/ui/discover/:
  - Search box with 72dp height, large text, microphone button for voice input
    (Android SpeechRecognizer for speech-to-text)
  - Search results: max 10 items in large tiles, explicit content filtered by backend
  - Each result has a "🙏 Request" button → POST /api/v1/content-requests (or queue if offline)
  - Request button DISABLED when 3 pending requests exist, with friendly message:
    "Du hast schon 3 Wünsche offen – warte bis Mama/Papa geantwortet hat!"
  - "My wishes" section below search:
    - Shows pending requests with kid-friendly time context:
      < 1 hour: "Mama/Papa schauen sich das an"
      1-24 hours: "Gestern gewünscht"
      > 24 hours: "Vor ein paar Tagen gewünscht"
    - Clock icon 🕐, no spinner
    - Rejected items: shown with ❌ and parent note (if any) for 24 hours, then hidden
    - Expired items: silently removed
  - On REQUEST_APPROVED WebSocket message: celebration animation (confetti + cheering sound),
    trigger SyncManager.syncNow() to fetch new content, new tile gets "NEW" badge for 24h
  - Search rate-limited: 1 query per 5 seconds (debounce on input, toast on rapid retry)
- DiscoverViewModel: manages search state, pending requests, WebSocket listener for approvals
- HomeScreen updated: Discover button (🔍) now visible and navigates to DiscoverScreen
- WebSocket listener in kids-app: listens for REQUEST_APPROVED, REQUEST_REJECTED, REQUEST_EXPIRED
  and updates local state accordingly

REFERENCE: PROJECT_PLAN.md §5.1.6 (Discover screen wireframe, request limits, pending UX,
time context strings, auto-expiry behavior).

CONSTRAINTS:
- Search calls GET /api/v1/spotify/search via backend (requires internet)
- Content requests POST to backend (or queue offline)
- Do NOT implement parent notification (that's the parent app's job)
- Do NOT play requested content – it's only playable after parent approval + sync

VERIFICATION:
- UI test: search "Frozen" → results shown → tap Request → "My wishes" section shows pending item
- UI test: 3 pending requests → 4th request button disabled with message
- UI test: rejected request shows ❌ with note
- Unit test: time context strings ("Mama/Papa schauen sich das an" for < 1h, etc.)
- Unit test: search rate limiting (2 queries within 5s → second blocked)
- Unit test: DiscoverViewModel processes WebSocket approval → triggers sync
- Run ./gradlew test → all tests pass
```

### Prompt 7.5 – Parent App Three-Layer Notification System

```
CONTEXT: Phase 7 of KidsTune. Backend sends content request notifications via WebSocket.
We now implement the complete three-layer notification system in the Parent App to ensure
parents are ALWAYS notified, even when the app is not running.

GOAL: When this task is done:

LAYER 1 – NotificationService (Foreground Service + WebSocket):
- Extends Service, promoted to foreground with persistent notification:
  "KidsTune Parent – Connected and listening for requests"
- Maintains WebSocket connection to wss://backend/ws/parent/{familyId}
- On CONTENT_REQUEST message: creates high-priority Android notification with:
  - Child avatar icon + name ("🐻 Luna wants to listen to...")
  - Content artwork (loaded via Coil into notification)
  - Title and artist name
  - [Approve] and [Reject] inline action buttons (via PendingIntent)
  - Tapping notification body opens ApprovalQueueScreen
- On DAILY_DIGEST message: creates summary notification:
  "🐻 Luna and 🦊 Max have 4 open wishes" → tapping opens ApprovalQueueScreen
- Auto-reconnect: exponential backoff 1s → 2s → 4s → 8s → 16s → 30s (max)
- Heartbeat: sends PONG in response to server PING
- Started on app launch, survives backgrounding

LAYER 2 – PendingRequestPollWorker (WorkManager):
- PeriodicWorkRequest every 15 minutes
- Constraints: requiredNetworkType = CONNECTED
- On execute:
  1. GET /api/v1/content-requests/pending/count
  2. Compare with locally stored "last seen request IDs" in SharedPreferences
  3. If NEW requests found AND Layer 1 has NOT already notified (check shared flag):
     Create Android notification (same format as Layer 1)
  4. Store current request IDs in SharedPreferences
- This worker is registered in Application.onCreate() and survives app kill + reboot

LAYER 3 – BootReceiver:
- BroadcastReceiver for BOOT_COMPLETED
- Re-registers WorkManager periodic poll (belt-and-suspenders)
- Optionally restarts Foreground Service if user opted in

DEDUPLICATION:
- Layer 1 notifications tagged with requestId (Android replaces existing notification for same tag)
- Layer 2 checks shared "notifiedRequestIds" set before creating notification
- Layer 2 checks if NotificationService is connected (via shared boolean flag) → skips if Layer 1 active

SETUP:
- On first Parent App launch, a setup wizard:
  1. Explains notification layers in plain language
  2. Requests NOTIFICATION permission (Android 13+)
  3. Requests battery optimization exemption (opens system settings intent)
  4. Registers WorkManager periodic job
  5. Starts Foreground Service

ApproveRejectReceiver (BroadcastReceiver):
- Handles notification action button PendingIntents
- On Approve: PUT /api/v1/content-requests/{id} { status: APPROVED } → dismiss notification
- On Reject: PUT /api/v1/content-requests/{id} { status: REJECTED } → dismiss notification
- Runs in background without opening the app

REFERENCE: PROJECT_PLAN.md §5.2.3 (complete three-layer architecture with code samples,
deduplication strategy, setup wizard wireframe, FCM rationale).

CONSTRAINTS:
- Do NOT use Firebase Cloud Messaging
- Do NOT modify backend or kids-app
- WorkManager is the critical layer – it MUST work even when everything else fails
- Foreground Service notification must be low-priority (PRIORITY_LOW) to avoid annoying the user,
  but content request notifications must be high-priority (PRIORITY_HIGH)

VERIFICATION:
- Integration test: Layer 1: start service → connect WebSocket → mock CONTENT_REQUEST message →
  Android notification created with correct title, image, action buttons
- Integration test: Layer 1: mock DAILY_DIGEST message → summary notification created
- Integration test: Layer 2: NotificationService NOT running → WorkManager fires →
  polls mock backend → new request found → notification created
- Integration test: Deduplication: Layer 1 notifies → Layer 2 polls → no duplicate notification
- Integration test: ApproveRejectReceiver: mock approve action → PUT call made to backend
- Unit test: exponential backoff timing (1s, 2s, 4s, ..., 30s max)
- Unit test: BootReceiver re-registers WorkManager
- Run ./gradlew test → all tests pass
```

### Prompt 7.6 – Parent App Approval Queue

```
CONTEXT: Phase 7 of KidsTune. Notifications work (7.5). We build the approval queue screen
where parents review and act on content requests.

GOAL: When this task is done:
- ApprovalQueueScreen in parent-app/ui/requests/:
  - Tab bar: "Pending" | "History" (approved + rejected + expired)
  - Pending tab:
    - List sorted by recency (newest first)
    - Each item: child avatar + name, content artwork, title, artist, relative time ("2 min ago")
    - Approve options per item:
      - "Add for [child name] only" → default, one tap
      - "Add for all children" → adds content to all profiles
    - Reject button with optional text note field (shown to child): "Not suitable right now"
    - Bulk actions at top: "Approve All" / "Reject All"
  - History tab:
    - Shows approved, rejected, and expired requests
    - Expired items marked "Expired – not reviewed" with option to retroactively approve
    - Filter chips: All / Approved / Rejected / Expired
  - Badge count on dashboard navigation item showing pending count
  - Pull-to-refresh to re-fetch from backend
- ApprovalQueueViewModel with tab state, pending list, history list, bulk action handling
- Dashboard pending requests badge wired up to real count from GET /api/v1/content-requests/pending/count
- Navigation: tapping a notification opens the app directly on ApprovalQueueScreen

REFERENCE: PROJECT_PLAN.md §5.2.1 (Approval Queue wireframe with per-child approve),
§5.1.6 (request lifecycle: PENDING → APPROVED/REJECTED/EXPIRED).

CONSTRAINTS:
- Do NOT modify backend or kids-app
- The approval queue is the ONLY place parents review requests (not in the notification itself,
  though notification actions are a shortcut)
- Retroactive approve of expired requests creates a new AllowedContent entry (same as normal approve)

VERIFICATION:
- UI test: Pending tab shows mock requests sorted by recency
- UI test: Approve for child only → API called → item moves to History tab
- UI test: Reject with note → note field shown → API called
- UI test: Bulk approve all → all items move to History
- UI test: History tab → filter by Expired → expired items shown with "Approve anyway" button
- UI test: Badge count updates after approval
- Unit test: ApprovalQueueViewModel processes approval correctly
- Run ./gradlew test → all tests pass
```

---

## Phase 8 – Polish, Hardening & Documentation (Week 11)

### Prompt 8.1 – Kids App Animations

```
CONTEXT: Phase 8 of KidsTune. All features work. We add polish animations to make the
kids app feel delightful.

GOAL: When this task is done:
- Shared element transition: tapping a content tile → cover art animates to NowPlayingScreen
  (Compose AnimatedContent or Shared Element Transitions API)
- Button press: scale to 0.95 on press, spring back to 1.0 on release (Modifier.pointerInput)
- Page swipe: spring physics for content grid pagination (Compose Pager with spring fling)
- Shimmer loading: before images load, tiles show an animated shimmer placeholder
  (custom Modifier with linear gradient animation)
- Favorite heart: bounce animation on add (scale 1.0 → 1.3 → 1.0 with overshoot),
  gentle shrink on long-press remove
- Celebration: confetti particle animation when content request is approved
  (custom Canvas composable with random particle physics)
- "NEW" badge: pulse animation on newly approved content tiles (scale oscillation for 24h)
- All animations respect @Composable AnimatedVisibility transitions

REFERENCE: PROJECT_PLAN.md §5.1.2 (UI design principles, haptic feedback).

CONSTRAINTS:
- All animations must run at 60fps on the target device (old Samsung phone)
- No heavy animation libraries – use built-in Compose animation APIs
- Haptic feedback on button press (HapticFeedbackType.LongPress)
- Animations must not block user interaction
- Do NOT change any business logic or API calls

VERIFICATION:
- Manual test: each animation runs smoothly on target device
- UI test: verify animations don't crash or cause layout issues
- Run ./gradlew test → all existing tests still pass
```

### Prompt 8.2 – Kids App Edge Cases

```
CONTEXT: Phase 8 of KidsTune. We handle all error states in the kids app with friendly,
kid-appropriate screens.

GOAL: When this task is done:
- SpotifyNotInstalledScreen: friendly dinosaur illustration (Compose Canvas drawing),
  "Spotify fehlt! Gib das Handy bitte Mama oder Papa." No action buttons for kids.
  Detected via SpotifyRemoteManager.connectionError == NOT_INSTALLED
- SpotifyNotLoggedInScreen: same style, "Spotify ist nicht angemeldet. Bitte Mama oder Papa fragen."
  Detected via NOT_LOGGED_IN error.
- SpotifyPremiumExpiredScreen: "Musik geht gerade nicht. Bitte Mama oder Papa fragen."
  Detected via PREMIUM_REQUIRED error.
- StorageFullScreen: "Nicht genug Platz! Bitte Mama oder Papa fragen." (shown when Room write fails)
- NoContentYetScreen (after pairing but before first sync delivers content):
  "Verbinde dich mit WLAN" with airplane illustration. Only shown when Room has 0 content entries
  AND device has no network.
- BackendUnreachableState: NOT a blocking screen. Just the offline indicator + content from cache.
  Only if cache is completely empty do we show NoContentYetScreen.
- All error screens: large illustrations, simple German text, no technical jargon,
  consistent visual style

REFERENCE: PROJECT_PLAN.md §5.1.5 ("First launch with no cache" scenario).

CONSTRAINTS:
- Error screens are NOT interactive for kids (no retry buttons) – the solution requires parent intervention
- Illustrations are simple Compose Canvas drawings (geometric shapes), not image files
- Do NOT change backend or parent-app
- Keep existing functionality working – error screens are fallbacks only

VERIFICATION:
- Unit test: SpotifyRemoteManager connection errors correctly detected per type
- UI test: each error screen renders with correct illustration and text
- UI test: storage full error shown when Room write throws IOException
- Run ./gradlew test → all tests pass
```

### Prompt 8.3 – Kids App Accessibility Audit

```
CONTEXT: Phase 8 of KidsTune. We audit and fix accessibility in the kids app.

GOAL: When this task is done:
- Every image and icon has a meaningful contentDescription:
  - Content tiles: "{title} by {artist}" (e.g., "Bibi & Tina Folge 1 by Bibi & Tina")
  - Profile avatars: "{name}'s profile" (e.g., "Luna's profile")
  - Action buttons: "Play", "Pause", "Skip forward", "Skip back", "Add to favorites",
    "Remove from favorites", "Search", "Request this"
  - Category buttons: "Music", "Audiobooks", "Favorites"
- Custom Compose test rule asserting all clickable elements have ≥ 72dp touch target:
  - Scans all nodes with click action
  - Asserts minimumTouchTargetSize ≥ 72.dp
  - Run as part of every UI test
- Color contrast check:
  - All text on colored backgrounds meets WCAG AA (4.5:1 ratio)
  - Document check results in ACCESSIBILITY.md
- TalkBack testing:
  - Navigate full app flow with TalkBack enabled
  - Fix any issues (missing descriptions, focus order problems)
  - Document TalkBack compatibility in ACCESSIBILITY.md

REFERENCE: PROJECT_PLAN.md §5.1.2 (72dp touch targets, content descriptions).

CONSTRAINTS:
- Do NOT change visual design significantly
- Fix only genuine accessibility issues, don't over-annotate
- Do NOT modify backend or parent-app

VERIFICATION:
- Custom touch target assertion passes for all screens
- All UI tests still pass with new contentDescriptions
- ACCESSIBILITY.md documents: contrast ratios, TalkBack results, known limitations
- Run ./gradlew test → all tests pass
```

### Prompt 8.4 – Parent App Error Handling & Polish

```
CONTEXT: Phase 8 of KidsTune. We add comprehensive error handling and polish to the parent app.

GOAL: When this task is done:
- Network error states on all screens:
  - SnackBar with retry button for transient errors
  - Full-screen error state for screens that can't show any data
  - User input preserved during errors (search query, form fields)
- Spotify token expiry handling:
  - Detect 401 from backend → trigger automatic re-auth flow via SpotifyOAuthService
  - Retry the failed request after re-auth succeeds
  - If re-auth fails → navigate to login screen
- Empty states for all lists:
  - Content list: "No content yet. Tap + to add music and audiobooks for [child]"
  - Device list: "No devices paired. Tap 'Generate Code' to pair a device."
  - Approval queue: "All caught up! No pending requests." (with checkmark illustration)
  - Import suggestions: "No listening history found. Play some music on Spotify first."
- Profile management polish:
  - Edit profile: change name, avatar icon/color, age group
  - "Also add to [other child]?" quick action when viewing content for one profile
  - View content summary: "4 Artists, 5 Albums, 2 Playlists, 1 Track" per profile
- Input validation:
  - Profile name: required, 1-50 chars, inline error
  - Search: minimum 2 characters before searching

REFERENCE: PROJECT_PLAN.md §5.2.1 (Parent App screen flows).

CONSTRAINTS:
- Do NOT change backend or kids-app
- Do NOT add new features – this is about resilience of existing features
- Error messages should be user-friendly, not technical

VERIFICATION:
- UI test: network error → snackbar shown → tap retry → data loads
- UI test: empty content list → empty state message shown
- UI test: edit profile → change name → save → updated in list
- Unit test: token expiry detection → re-auth triggered
- Run ./gradlew test → all tests pass
```

### Prompt 8.5 – Backend Resilience & Observability

```
CONTEXT: Phase 8 of KidsTune. We harden the backend for production use.

GOAL: When this task is done:
- Spotify API rate limit handling:
  - Detect 429 responses from Spotify
  - Read Retry-After header
  - Exponential backoff with jitter (random 0-500ms added)
  - Queue pending requests during backoff period
  - Log warnings for rate limit hits
- Per-device request throttling:
  - Max 10 search queries per minute per device (Caffeine rate limiter)
  - Max 5 content requests per hour per profile
  - Return 429 with Retry-After header when exceeded
- Circuit breaker on SpotifyApiClient (implement manually or use Resilience4j):
  - Open after 5 consecutive Spotify API failures
  - Half-open after 30 seconds (allow 1 test request)
  - Closed on successful test request
  - While open: return cached data if available, error if not
- Structured logging:
  - JSON format for Grafana Loki ingestion
  - MDC fields on every request: requestId (UUID), familyId, profileId, deviceId
  - Log levels: INFO for API calls, WARN for rate limits/retries, ERROR for failures
- Actuator health indicators:
  - DB connectivity check
  - Spotify API reachable check (cached, checked every 60s)
  - WebSocket hub stats: connected parents count, connected kids devices count
  - Active content resolution jobs count
- All existing endpoints return appropriate error codes (400, 401, 403, 404, 409, 429, 500)

REFERENCE: PROJECT_PLAN.md §13 (Risk Assessment: rate limits, resilience).

CONSTRAINTS:
- Do NOT change Android apps
- Do NOT add new features
- Keep it simple – avoid over-engineering the circuit breaker

VERIFICATION:
- Integration test: Spotify returns 429 → backoff applied → retried after delay
- Integration test: 6 consecutive Spotify failures → circuit opens → returns cached data
- Integration test: device makes 11 searches in 1 minute → 11th returns 429
- Unit test: structured log output contains requestId, familyId in JSON
- Integration test: /actuator/health returns all custom indicators
- Run ./gradlew test → all tests pass
```

### Prompt 8.6 – End-to-End Tests

```
CONTEXT: Phase 8 of KidsTune. All features implemented and hardened. We write E2E tests
covering critical user journeys.

GOAL: When this task is done:
- Maestro YAML test suites in e2e-tests/ directory:
  Test 1 – Fresh Setup:
    - Launch parent app → login → create profile "Luna" → add "Bibi & Tina" artist
    - Launch kids app → enter pairing code → select Luna → verify content tiles appear
  Test 2 – Content Sync:
    - Parent app: add new album for Luna
    - Kids app: wait for sync (trigger manually) → verify new album tile appears
  Test 3 – Approval Flow:
    - Kids app: navigate to Discover → search "Frozen" → tap Request
    - Parent app: verify notification → tap Approve
    - Kids app: verify celebration animation → new content playable
  Test 4 – Offline Resilience:
    - Kids app: verify content from cache → airplane mode → browse and navigate → all works
- Test execution instructions in e2e-tests/README.md

REFERENCE: PROJECT_PLAN.md §8.2 (E2E testing with Maestro).

CONSTRAINTS:
- Maestro tests need two devices (or emulators) for parent + kids flows
- Tests may require manual Spotify login (not automatable)
- Document any manual steps required before test execution

VERIFICATION:
- All 4 Maestro tests pass on emulators or real devices
- e2e-tests/README.md has clear execution instructions
```

### Prompt 8.7 – Documentation

```
CONTEXT: Phase 8 of KidsTune. Everything works. We write comprehensive documentation.

GOAL: When this task is done:
- README.md at project root covering:
  - Architecture overview with ASCII diagram
  - Prerequisites: JDK 21, Android SDK 35, MariaDB 10.x+, Docker, Spotify Developer Account
  - Quick start: clone → create DB → configure .env → docker compose up → build apps
  - Spotify Developer App setup (step-by-step)
  - Samsung Kids device configuration (step-by-step)
  - APK sideloading via ADB instructions
  - How to add content for children (Parent App walkthrough)
  - How the approval workflow works
  - Offline playback tips
  - Troubleshooting: common issues and fixes
    - Spotify not connecting: check login, check premium, check SDK version
    - Sync not working: check network, check backend logs, force sync
    - Notifications not arriving: check battery optimization, check WorkManager
    - Samsung Kids issues: check allowed apps list, check Spotify background
  - Known limitations and future roadmap
- Update CLAUDE.md with any new patterns or conventions from implementation
- Verify all inline code comments are accurate and helpful
- Add LICENSE file (choose appropriate license for personal project)

REFERENCE: PROJECT_PLAN.md §12 (Deployment & Setup), §14 (Future Considerations).

CONSTRAINTS:
- Documentation must be written from the perspective of someone setting up from scratch
- Include actual commands, not just descriptions
- Screenshots are optional but paths should be documented for later addition

VERIFICATION:
- A fresh reader can follow README.md from clone to working setup
- All commands in README are tested and work
- CLAUDE.md is up to date with final project state
```
