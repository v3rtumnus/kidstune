# KidsTune – Claude Code Prompts

All implementation prompts for the KidsTune project, organized by phase.
Each prompt is self-contained and follows the structure: CONTEXT → GOAL → REFERENCE → CONSTRAINTS → VERIFICATION.

Start every Claude Code session with: **"Read CLAUDE.md and PROJECT_PLAN.md first."**

---

**Implementation Progress:**
- ✅ **Phase 1 complete** (1.1 – 1.4) — Backend foundation, DB schema, auth, profiles
- ✅ **Phase 2 complete** (2.1 – 2.6) — Content management, web dashboard, email notifications, per-profile Spotify tokens
- ✅ **Phase 3 complete** (3.1 – 3.4) — Kids app setup, all screens with mock data, UI tests, Discover screen
- ✅ **Phase 4 complete** (4.1 – 4.5) — Backend resolver + sync, kids app Room storage, Spotify playback, real favorites + Spotify Liked Songs sync
- ✅ **Phase 5 complete** (5.1 – 5.4) — Device pairing, delta sync backend, kids app pairing flow, WorkManager sync manager
- ✅ **Prompt 6.1 complete** — Backend import service + liked songs pre-population
- ✅ **Prompt 6.2 complete** — Web dashboard import wizard + liked songs import
- ✅ **Prompt 6.4 complete** — Samsung Kids compatibility: lifecycle fixes, README setup guide
- ✅ **Prompt 6.5 complete** — MediaSession / media notification ownership: KidstuneMediaService + SpotifyMirrorPlayer + ArtworkLoader; notification tap opens KidsTune not Spotify
- ⬅️ **NEXT: Phase 7**

---

---

## Phase 1 – Backend Foundation (Week 1) ✅ COMPLETE

### Prompt 1.1 – Backend Project Scaffold ✅

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 1.2 – Spotify OAuth ✅

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 1.3 – Profile CRUD ✅

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 1.4 – Spring Security JWT ✅

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

---

## Phase 2 – Content Management Backend + Web Dashboard (Weeks 2-3)

### Prompt 2.1 – AllowedContent Backend ✅

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 2.2 – Content Type Heuristic ✅

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 2.3 – Spotify Search Proxy ✅

```
CONTEXT: Phase 2 of KidsTune. Content management (2.1) and type heuristic (2.2) exist.
We need the backend to proxy Spotify search for the web dashboard and Kids App Discover feature.

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 2.4 – Web Dashboard Foundation ✅
> ⚠️ **NOTE:** This prompt was implemented with Spotify OAuth as the login mechanism.
> Prompt 2.4b below refactors this to email/password. The layout, dashboard stats,
> and settings page built here remain valid; only the login/auth mechanism changes.

```
CONTEXT: Phase 2 of KidsTune. Backend has content management (2.1-2.3) and Spotify proxy.
We now add a Thymeleaf-based web dashboard served by the same Spring Boot backend.
Read CLAUDE.md and PROJECT_PLAN.md §5.2 before starting.

GOAL: When this task is done:
- spring-boot-starter-thymeleaf (reactive, for WebFlux) added to backend/build.gradle.kts
- HTMX 2.x and Bootstrap 5.x added as webjars dependencies, referenced in base layout
- Family entity updated: add notification_emails (TEXT, comma-separated) column +
  Liquibase changelog (002-notification-emails.yaml or add to existing)
- Base layout template (src/main/resources/templates/web/layout.html):
  - Top nav: "KidsTune" branding, logged-in family identifier, logout link
  - Left sidebar: Dashboard, Profiles, Requests, Import, Devices, Settings, Admin
  - Main content area via Thymeleaf layout fragments
  - Mobile-responsive (Bootstrap grid, sidebar collapses on small screens)
- Web controllers in new package com.kidstune.web:
  - WebLoginController:
    - GET /web/login → login page with "Login with Spotify" button (reuses SpotifyOAuthService)
    - GET /web/auth/callback → OAuth callback, creates WebSession, redirects to /web/dashboard
    - POST /web/logout → invalidates session, redirects to /web/login
  - WebDashboardController:
    - GET /web/dashboard → shows: total profiles, total content count, pending request count,
      paired device count, recent activity (last 5 content additions)
- Spring Security updated for dual auth:
  - /web/** → session-based (WebSession + cookie), redirect unauthenticated to /web/login
  - /api/** → JWT Bearer (unchanged)
  - Public: /web/login, /web/auth/callback, /web/approve/**, /actuator/health
- Custom error pages in templates/web/error/ (403, 404, 500)
- SettingsWebController:
  - GET /web/settings → settings page
  - POST /web/settings → save notification_emails (trim, validate non-empty)

REFERENCE: PROJECT_PLAN.md §5.2.1 (web dashboard page structure and layout),
§5.2.3 (notification_emails per family), §3.2 (backend tech stack with Thymeleaf/HTMX).

CONSTRAINTS:
- Do NOT implement profile/content pages yet (prompt 2.5)
- Do NOT implement approval queue (prompt 2.5)
- Do NOT implement email notifications yet (prompt 2.6)
- Do NOT modify existing REST API controllers or JWT security for /api/**
- Web controllers call services directly – never HTTP back to own REST API
- Thymeleaf must work with Spring WebFlux (reactive Thymeleaf, not Spring MVC)

VERIFICATION:
- ./gradlew compileJava → succeeds with Thymeleaf on classpath
- GET http://localhost:8080/web/login (no session) → HTML login page shown
- Complete Spotify OAuth in browser → redirected to /web/dashboard → overview stats shown
- GET /web/dashboard without session → 302 redirect to /web/login
- GET /api/v1/profiles without JWT → 401 (existing behavior unchanged)
- GET /api/v1/profiles with valid JWT → 200 (existing behavior unchanged)
- GET /web/settings → settings page with notification_emails field
- POST /web/settings with emails → saved to Family.notification_emails in DB
- Dashboard is mobile-friendly (tested at 375px width in browser)
- Unit tests for WebLoginController (mock SpotifyOAuthService)
- Run ./gradlew test → all existing tests still pass
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'
  → curl http://localhost:8080/web/login → HTML returned
```

### Prompt 2.4b – Refactor Dashboard Auth to Email/Password ✅

```
CONTEXT: Phase 2 of KidsTune. The web dashboard foundation exists (2.4) but uses Spotify
OAuth as the login mechanism. We decouple dashboard authentication from Spotify entirely:
login uses email + password, with "Remember me" support. The parent's Spotify account
becomes an optional "Connect Spotify" step in Settings (needed for content search/resolution,
but not for login). Read CLAUDE.md pitfall #8 and PROJECT_PLAN.md §2.4 before starting.

GOAL: When this task is done:

- Family entity updated (Liquibase migration 003-family-auth.yaml):
  - Add email VARCHAR(255) NOT NULL UNIQUE
  - Add password_hash VARCHAR(255) NOT NULL  (BCrypt)
  - spotify_user_id and spotify_refresh_token become nullable
    (existing NOT NULL constraints relaxed)
  - For any existing rows in the dev DB: set a placeholder password_hash so migration
    applies cleanly (use a fixed BCrypt hash that the dev knows)

- FamilyService (new or updated):
  - register(email, password) → validates unique email, hashes password with BCrypt,
    creates Family row, returns familyId
  - authenticate(email, password) → loads Family by email, verifies BCrypt hash,
    returns familyId or throws InvalidCredentialsException
  - A FamilyRepository.findByEmail(email) query method

- WebLoginController refactored (replaces Spotify-based login):
  - GET  /web/login     → renders login form (email + password fields + "Remember me" checkbox)
  - POST /web/login     → authenticates via FamilyService; on success: creates WebSession
                          storing familyId; if "Remember me" checked: also sets a persistent
                          "remember_me" cookie (signed token, 30-day TTL, stored in DB);
                          redirects to /web/dashboard (or to the originally requested URL
                          if redirected from a protected page)
                          on failure: re-renders login form with generic error
                          ("E-Mail oder Passwort falsch")
  - GET  /web/register  → renders registration form (email, password, confirm password)
  - POST /web/register  → validates fields (email format, password min 8 chars,
                          passwords match, unique email); on success: creates Family,
                          creates WebSession, redirects to /web/dashboard; on error:
                          re-renders form with field-level errors
  - POST /web/logout    → invalidates WebSession + deletes remember_me cookie/token;
                          redirects to /web/login

- Remember-me mechanism:
  - New table remember_me_token (Liquibase migration 003-family-auth.yaml):
    series VARCHAR(64) PK (random base64),
    token_hash VARCHAR(64) (BCrypt or SHA-256 of random value),
    family_id VARCHAR(36) FK → family(id) ON DELETE CASCADE,
    created_at DATETIME(6), expires_at DATETIME(6)
  - On "Remember me" login: generate random series + token; store hashed token in DB;
    set cookie "remember_me={series}:{token}" (HttpOnly, Secure, SameSite=Strict, 30 days)
  - On request with no WebSession but valid remember_me cookie:
    WebSessionSecurityContextRepository (or a new RememberMeWebFilter) looks up series,
    verifies token hash, creates new WebSession with familyId, rotates token (new token,
    same series — standard Spring-style remember-me token rotation to detect theft)
  - On logout: delete all remember_me_token rows for the family (not just the current series)

- SecurityConfig updated:
  - Public: /web/login, /web/register, /web/approve/**, /actuator/health
    (remove /web/auth/callback and /web/auth/spotify-login from public list)
  - Remove the web redirect to Spotify; unauthenticated /web/** → redirect to /web/login

- SettingsWebController updated:
  - GET /web/settings → shows notification_emails + Spotify section:
    "Spotify account: [Connected as {spotifyUserId}] [Disconnect]"  or
    "Spotify account: Not connected [Connect Spotify Account]"
  - GET /web/settings/connect-spotify → initiates Spotify OAuth PKCE (same flow as before,
    reusing existing SpotifyOAuthController internals) using a new redirect URI:
    {base-url}/web/settings/spotify-callback
  - GET /web/settings/spotify-callback → stores family.spotify_refresh_token + spotify_user_id;
    redirects to /web/settings with ?spotify=connected
  - POST /web/settings/disconnect-spotify → clears spotify_user_id + spotify_refresh_token
    from Family; redirects to /web/settings

- Old Spotify-based web login routes cleaned up:
  - Remove /web/auth/spotify-login and /web/auth/callback from WebLoginController
  - These routes no longer exist (they were only used for login, not for API use)
  - The /api/v1/auth/spotify/** routes for device clients remain untouched

REFERENCE: PROJECT_PLAN.md §2.4 (updated auth flow), §4.1 (Family entity with email + password_hash),
§5.2.1 (/web/login and /web/register pages). CLAUDE.md pitfall #8 (two Spotify token levels;
dashboard auth is independent of Spotify).

CONSTRAINTS:
- Use BCrypt (Spring Security's BCryptPasswordEncoder) for password hashing
- Remember-me token uses the "persistent token" approach (series + token) — NOT a JWT or
  a simple cookie value — to enable theft detection via series collision
- Do NOT store plaintext passwords anywhere (not even in logs)
- Registration is open (no invite code) — it's a private homeserver, not a public app
- Do NOT modify /api/v1/** endpoints or JWT auth
- Do NOT implement password reset (out of scope for now)
- Thymeleaf error messages in German to match existing UI language

VERIFICATION:
- GET /web/register → registration form renders
- POST /web/register with valid data → Family created (BCrypt hash in DB), session created,
  redirected to /web/dashboard
- POST /web/register with duplicate email → form re-rendered with "E-Mail bereits vergeben"
- GET /web/login → login form renders (no more Spotify button)
- POST /web/login with correct credentials → session created, redirected to /web/dashboard
- POST /web/login with wrong password → form re-rendered with generic error
- POST /web/login with "Remember me" → remember_me cookie set (HttpOnly, 30d) + DB row created
- New browser (no session), valid remember_me cookie → automatically logged in, cookie rotated
- POST /web/logout → session gone, remember_me cookie cleared, DB token deleted
- GET /web/dashboard without session AND without remember_me cookie → 302 to /web/login
- GET /web/settings → shows Spotify "Not connected" (since no OAuth happened yet)
- GET /web/settings/connect-spotify → redirects to Spotify OAuth
- Callback → Family.spotify_refresh_token stored, settings shows "Connected"
- POST /web/settings/disconnect-spotify → token cleared
- GET /api/v1/profiles without JWT → 401 (unchanged)
- GET /api/v1/profiles with valid JWT → 200 (unchanged)
- Run ./gradlew test → all tests pass
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'
  → test register + login flow in browser
```

### Prompt 2.5 – Web Dashboard Profile, Content & Approval Queue ✅

```
CONTEXT: Phase 2 of KidsTune. Web dashboard foundation exists (2.4). We add profile
management (including per-profile Spotify account linking), per-profile content pages,
and the content request approval queue.

Each child has their own individual Spotify account. These are linked to child profiles
via a separate OAuth flow (child's own Spotify credentials, not the parent's). The linked
token is stored at the ChildProfile level and used later for the import wizard (phase 6).

GOAL: When this task is done:

- ChildProfile entity updated (requires Liquibase migration 003-profile-spotify.yaml):
  - Add spotify_user_id VARCHAR(255) NULL
  - Add spotify_refresh_token TEXT NULL
  (Encrypted at rest using the same AES-256-GCM encryption already used for Family tokens)

- SpotifyTokenService extended with profile-level methods (parallel to the existing family methods):
  - getValidProfileAccessToken(profileId) → decrypts + refreshes child's token; returns current access token
  - storeProfileTokens(profileId, accessToken, refreshToken, expiresIn)
  - isProfileSpotifyLinked(profileId) → true if spotify_refresh_token is non-null for profile

- New OAuth endpoints in WebLoginController (or a new ProfileSpotifyLinkController):
  - GET /web/profiles/{id}/link-spotify → initiates Spotify OAuth PKCE for child's account;
    stores profileId in session (to know which profile to update on callback);
    uses a SEPARATE redirect_uri: {base-url}/web/profiles/spotify-callback
    and the scopes required for import (user-library-read, user-read-recently-played,
    user-top-read, playlist-read-private) — NOT the parent's scopes
  - GET /web/profiles/spotify-callback → receives code for child's account,
    exchanges for tokens, stores in ChildProfile via SpotifyTokenService,
    redirects to /web/profiles/{id}/edit with ?linked=true

- ProfileWebController with pages:
  - GET /web/profiles → list all profiles: name, avatar (colored CSS circle + emoji),
    age group, content count, bound device name, Spotify account linked badge
  - GET /web/profiles/new → create form (name, avatar icon/color dropdowns, age group)
  - POST /web/profiles → create, same validation as REST (max 6, unique name);
    on error: re-render form with field errors; on success: redirect to list
  - GET /web/profiles/{id}/edit → edit form pre-populated; shows "Spotify account:
    [Linked as {spotifyUserId}] [Unlink]" or "Not linked [Link Spotify Account]"
  - POST /web/profiles/{id} → update; redirect to list on success
  - POST /web/profiles/{id}/delete → HTMX confirmation modal; on confirm: delete + redirect
  - POST /web/profiles/{id}/unlink-spotify → clears spotify_user_id + spotify_refresh_token
    on the profile; redirect to edit page with ?unlinked=true

- ContentWebController with pages:
  - GET /web/profiles/{profileId}/content → content table: title, artist, scope badge,
    type badge, added date, delete button; query params: ?type=, ?scope=, ?search=
  - GET /web/profiles/{profileId}/content/add → Spotify search form + scope dropdown
  - POST /web/profiles/{profileId}/content/search → HTMX endpoint: returns partial
    template (fragments/search-results.html) with result cards grouped by type
  - POST /web/profiles/{profileId}/content → add: scope dropdown + multi-profile
    checkboxes (for bulk-add to siblings); calls ContentService.addContent() or addContentBulk()
  - POST /web/profiles/{profileId}/content/{id}/delete → HTMX confirmation;
    on confirm: delete + HTMX swap removes the row

- RequestWebController with pages:
  - GET /web/requests → tabbed page: PENDING, APPROVED, REJECTED, EXPIRED
    (tab content loaded via HTMX hx-get on tab click)
  - GET /web/requests/pending → HTMX partial: request cards with child name+avatar,
    content image, title, artist, time-ago; buttons: [✓ Approve for {child}]
    [✓ Approve for all children] [✗ Reject]
  - POST /web/requests/{id}/approve → approveRequest(); HTMX swap removes card from pending
  - POST /web/requests/{id}/approve-all → adds to all family profiles
  - POST /web/requests/{id}/reject → inline HTMX note field; rejectRequest(note);
    HTMX swap removes card
  - POST /web/requests/bulk-approve → approve all selected (checkbox array)
  - GET /web/requests/history → HTMX partial for history tabs (APPROVED/REJECTED/EXPIRED)
- Shared fragments: web/fragments/confirm-modal.html (HTMX), web/fragments/search-results.html

REFERENCE: PROJECT_PLAN.md §2.2 (Spotify account model — child tokens at profile level),
§2.4 (auth flow step 2b — child Spotify linking), §4.1 (ChildProfile entity with new Spotify fields),
§5.2.1 (dashboard layout wireframe, approval queue wireframe),
§4.2 (content scope). CLAUDE.md pitfall #7 (per-profile, not per-family).

CONSTRAINTS:
- Do NOT implement email notifications here (prompt 2.6)
- Do NOT implement import wizard (phase 6)
- Do NOT implement device management (phase 5)
- Reuse ProfileService, ContentService, ContentRequestService directly
- HTMX for search results and delete confirmations; no full-page reloads for these actions
- Avatar display: colored CSS circles with animal emoji characters (no image files)
- The child OAuth callback must use a DIFFERENT redirect_uri than the parent login callback
  (e.g., /web/profiles/spotify-callback vs /web/auth/callback)
- Register /web/profiles/spotify-callback as a valid redirect URI in Spotify Developer App settings

VERIFICATION:
- GET /web/profiles → shows profiles with content count and Spotify linked/unlinked badge
- Create profile via web → appears in list → visible via GET /api/v1/profiles
- Edit profile → updated values in list
- "Link Spotify Account" on edit page → triggers child OAuth → redirects back → profile shows linked
- "Unlink" → clears token, profile shows not linked
- Delete profile → confirmation modal → cascade-deleted in DB
- Spotify search HTMX: POST search → partial HTML returned (no full page reload)
- Add content → visible in content list + in API response
- Remove content → row removed without full page reload
- GET /web/requests → PENDING tab loads with pending requests
- Approve request → card removed from pending, AllowedContent created in DB
- Approve for all → AllowedContent created for every profile in family
- Reject with note → note stored in DB, card removed
- All pages render correctly on mobile (Bootstrap responsive)
- Run ./gradlew test → all tests pass
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 2.6 – Email Notifications with Approve Token ✅

```
CONTEXT: Phase 2 of KidsTune. Web dashboard is live (2.4, 2.5). We add email notifications
so parents are alerted immediately when a child submits a content request.

GOAL: When this task is done:
- spring-boot-starter-mail added to backend/build.gradle.kts
- application.yml updated with Spring Mail config (all configurable via environment variables):
  spring.mail.host, spring.mail.port, spring.mail.username, spring.mail.password,
  spring.mail.properties.mail.smtp.auth, spring.mail.properties.mail.smtp.starttls.enable
  (values left as ${MAIL_HOST:localhost} style with fallback for local dev)
- ContentRequest entity updated: add approve_token (UUID, generated on creation,
  unique index); add Liquibase migration
- Public endpoint: GET /web/approve/{token}
  - No authentication required
  - Looks up ContentRequest by approve_token
  - If not found or EXPIRED/APPROVED/REJECTED: shows appropriate Thymeleaf page
    (e.g., "This request has already been handled" or "Link has expired")
  - If PENDING: calls ContentRequestService.approveRequest() → shows success page:
    "✓ Approved! [child] can now listen to [title]."
  - Token is single-use (approve_token set to null after use, or status changes prevent reuse)
- EmailNotificationService in a new com.kidstune.notification package:
  - sendRequestNotification(ContentRequest request):
    - Fetches Family.notification_emails for the request's profile's family
    - If no emails configured: skips silently (logs a warning)
    - Sends email to ALL notification_emails via Spring JavaMailSender
    - Subject: "🎵 [childName] wants to listen to [contentTitle]"
    - Body: Thymeleaf template (templates/email/request-notification.html):
      - Child name + avatar emoji
      - Content title + artist
      - [Approve] button → absolute URL to /web/approve/{approveToken}
      - [View in Dashboard] link → absolute URL to /web/requests
      - Friendly footer: "You're receiving this because you're set up as a parent in KidsTune"
    - Uses absolute URLs (configured via kidstune.base-url property, e.g., https://kidstune.altenburger.io)
  - sendDailyDigest():
    - Called by @Scheduled(cron="0 0 19 * * *")
    - For each family with PENDING requests older than 4 hours where digest_sent_at IS NULL:
      - Sends one summary email to all notification_emails
      - Body: list of pending requests with individual approve links
      - Sets digest_sent_at = now() on affected requests
- ContentRequestService.createRequest() updated: after saving the request, calls
  emailNotificationService.sendRequestNotification() asynchronously (@Async)

REFERENCE: PROJECT_PLAN.md §5.2.3 (notification system spec with email flow diagram,
approve_token, digest behavior). §6.1 ContentRequest API.

CONSTRAINTS:
- Email sending must be @Async – never block the request thread
- If Spring Mail is not configured (MAIL_HOST not set), email service gracefully skips
  (configure a no-op MailSender bean for tests)
- Do NOT send emails in tests – use a MockMailSender (GreenMail or Spring's JavaMailSenderImpl mock)
- approve_token must be regenerated if the same content is re-requested after expiry
- Do NOT modify Android apps or kids-app

VERIFICATION:
- Unit test: EmailNotificationService builds correct email content (subject, body, approve URL)
- Unit test: sendRequestNotification with no notification_emails → skips silently
- Unit test: sendRequestNotification with 2 emails → both receive the email
- Integration test (GreenMail or MockMailSender):
  - Create content request → verify email sent to all notification_emails
  - Verify email contains correct approve URL with approve_token
  - Verify email subject contains child name and content title
- Integration test: GET /web/approve/{validToken} → request approved → success page shown
- Integration test: GET /web/approve/{usedToken} → "already handled" page shown
- Integration test: GET /web/approve/{expiredToken} (token on EXPIRED request) → "expired" page shown
- Integration test: sendDailyDigest → families with pending > 4h get email;
  digest_sent_at set; families with no pending requests get no email
- Run ./gradlew test → all tests pass
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

---

## Phase 3 – Kids App UI Shell with Mock Data (Week 4)

### Prompt 3.1 – Kids App Project Setup + Theme ✅

```
CONTEXT: Phase 3 of KidsTune. Backend exists (phases 1-2). We now create the Kids App
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
- Compose Preview Screenshot Testing configured from the start:
  - Apply plugin `com.android.tools.compose:compose-preview-screenshot-testing` in
    kids-app/build.gradle.kts (check AGP + plugin compatibility for target version)
  - Configure screenshot output directory to docs/screenshots/ (via plugin DSL or default
    src/debug/screenshotTest/reference/) — whichever the installed plugin version supports
  - Every reusable component (ContentTile, FavoriteButton, PageIndicator, MiniPlayerBar) gets
    at least one @Preview function covering the normal state; the plugin renders all @Preview
    composables to PNG on the host JVM without a device or emulator
  - Run ./gradlew updateDebugScreenshotTest → PNG reference images generated
  - Commit the generated PNG files to the repo under docs/screenshots/ (or reference dir)
    so screenshots are persisted and tracked alongside code

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
- Run ./gradlew updateDebugScreenshotTest → PNG files generated for ContentTile,
  FavoriteButton, PageIndicator, MiniPlayerBar (one PNG per @Preview function)
- Confirm generated PNGs look correct, then commit them to the repo
```

### Prompt 3.2 – Kids App All Screens (Mock Data) ✅

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
- Run ./gradlew updateDebugScreenshotTest → new PNGs generated for all screen @Preview functions
  (ProfileSelectionScreen, HomeScreen, BrowseScreen, NowPlayingScreen, FavoritesEmptyState)
- Confirm PNGs look correct (colors, touch targets, layout), then commit them to the repo
```

### Prompt 3.3 – Kids App UI Tests ✅

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

### Prompt 3.4 – Kids App Discover Screen (Mock Data) ✅

```
CONTEXT: Phase 3 of KidsTune. All core screens exist and are tested (3.2/3.3). The DiscoverScreen
is currently a bare placeholder. We now implement it fully with hardcoded mock data so the complete
UX can be validated before any backend wiring.

KEY DESIGN PRINCIPLE: Children can search and find ANY Spotify content. There is NO play button on
this screen. The only action is "Request". Playing becomes possible only after parent approval +
sync. The safety constraint is on playback, not discovery.

GOAL: When this task is done:
- DiscoverScreen fully implemented with mock data:
  - Back button (72dp, same custom Row pattern as BrowseScreen / NowPlayingScreen)
  - Search box: large text (sp 20+), 72dp height, microphone icon button on the right
    (mic button triggers Android SpeechRecognizer for voice input; for now just show the button)
  - IDLE STATE (query is empty): shows hardcoded curated suggestions (mock list of 6–8 items
    representing known-artists.yml content: Bibi & Tina, Pumuckl, Die Drei ???, Pippi Långstrump,
    TKKG, Benjamin Blümchen, Lillifee). Large tiles (same ContentTile component), each with a
    "🙏 Ich will das!" Request button instead of being tappable to play.
  - ACTIVE SEARCH STATE (query non-empty): curated list replaced by mock search results
    (another hardcoded list of 6 items e.g. "Frozen OST", "Frozen 2 OST", "Encanto OST", etc.)
    Each tile has a Request button.
  - "Meine Wünsche" (My wishes) section always visible below the tile list — shows pending
    requests. The section is hidden (not shown at all) when there are no pending requests.
    - Pending tile: clock icon 🕐, title, time-context label computed from requestedAt:
        < 1 h  → "Mama/Papa schauen sich das an"
        1–24 h → "Gestern gewünscht"
        > 24 h → "Vor ein paar Tagen gewünscht"
    - Rejected tile: ❌ icon, title, parent note (if any). Dismissed after 24 h (mock: always shown)
  - Tapping Request on an idle/search result tile:
    - Adds item to "Meine Wünsche" as PENDING
    - The tile's Request button changes to "Angefragt" (disabled) immediately
    - If 3 pending requests already exist: button is disabled with tooltip/text
      "Du hast schon 3 Wünsche offen – warte bis Mama/Papa geantwortet hat!"
  - HomeScreen: add a fourth button "Entdecken" (🔍, accent color purple/indigo) that navigates
    to DiscoverRoute. Place it below the three existing category buttons.

- MVI state:
  - DiscoverState(
      query: String = "",
      suggestions: List<DiscoverTile> = mockSuggestions,   // idle tiles
      searchResults: List<DiscoverTile> = emptyList(),      // active search tiles
      pendingRequests: List<PendingRequest> = emptyList(),
      requestedUris: Set<String> = emptySet()               // tracks already-requested URIs
    )
  - DiscoverIntent: UpdateQuery(q), SubmitSearch, RequestContent(tile), DismissRejected(id)
  - DiscoverViewModel (no Hilt injection needed for mock phase – use default constructor)
  - DiscoverTile(spotifyUri, title, artistName, imageUrl?, type: ContentType)
  - PendingRequest(id, tile, status: PENDING|REJECTED, requestedAt, parentNote?)

- MockDiscoverData object (src/main/java/.../data/mock/MockDiscoverData.kt):
  - mockSuggestions: List<DiscoverTile> (8 items, German kids content)
  - mockSearchResults: List<DiscoverTile> (6 items, movie soundtracks)
  - mockPendingRequests: List<PendingRequest> (2 items: one PENDING < 1h, one REJECTED with note)

- @Preview functions (src/main/java/.../ui/screens/DiscoverScreen.kt):
  - DiscoverIdlePreview – idle state with suggestions
  - DiscoverSearchPreview – active search with results
  - DiscoverWithPendingPreview – idle + "Meine Wünsche" section showing 2 items
  - DiscoverLimitReachedPreview – all Request buttons disabled (3 pending requests)

- Screenshot tests (src/screenshotTest/java/.../ui/screens/DiscoverScreenshots.kt):
  - 4 reference PNGs matching the 4 @Preview functions above
  - Run ./gradlew updateDebugScreenshotTest → commit PNGs

- UI tests (src/test/java/.../ui/screen/DiscoverScreenTest.kt, ~10 test cases):
  - Idle state: suggestion tiles are shown
  - Active search: typing query replaces suggestions with search results
  - Tapping Request adds item to "Meine Wünsche" and disables its button
  - 3 pending requests: 4th Request button is disabled with limit message
  - Back button (72dp touch target)
  - Pending time-context string function: unit tests for all 3 thresholds (< 1h, 1–24h, > 24h)

CONSTRAINTS:
- No networking – all data is hardcoded mock
- No Spotify SDK calls
- Search debounce / voice recognition: add the mic button but it can be a no-op for now
- Follow the exact same patterns as BrowseScreen / NowPlayingScreen:
  - Custom 72dp Row for the top bar (NOT TopAppBar)
  - kidsTouchTarget() / Modifier.size(72.dp) for interactive elements
  - Stateless composable overload for testability
- DiscoverViewModel does NOT need @HiltViewModel in this phase (no injected deps)

REFERENCE: PROJECT_PLAN.md §5.1.6 (full Discover screen design, wireframe, request limits,
pending UX, time-context strings, auto-expiry, celebration animation — the celebration animation
and WebSocket wiring come in Prompt 7.4, not here).

VERIFICATION:
- ./gradlew testDebugUnitTest → all tests pass (including new DiscoverScreenTest)
- ./gradlew updateDebugScreenshotTest → 4 new PNGs generated and look correct
- Commit PNGs to repo
- HomeScreen shows the Discover button and navigates correctly (manual check)
```

---

## Phase 4 – Kids App Plays Real Music (Weeks 5-6)

### Prompt 4.1 – Backend Content Resolver ✅

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

### Prompt 4.2 – Backend Sync Endpoint ✅

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
- Do NOT modify Kids App or Android apps
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

### Prompt 4.3 – Kids App Sync + Room Storage ✅

```
CONTEXT: Phase 4 of KidsTune. Backend sync endpoint exists (4.2). The Kids App has mock data (phase 3).
We now replace mock data with real data from the backend, stored in Room for offline access.

GOAL: When this task is done:
- KidstuneApiClient (Ktor client) in kids-app/data/remote/:
  - sync(profileId): calls GET /api/v1/sync/{profileId} → returns SyncPayloadDto
  - Uses device token from EncryptedSharedPreferences for Authorization header
  - Base URL configurable in BuildConfig (default: https://kidstune.altenburger.io)
- ContentDao, AlbumDao, TrackDao, FavoriteDao, PlaybackPositionDao in kids-app/data/local/:
  - ContentDao: insert/replace all, deleteAll, getByType(contentType), getAll, getById
  - AlbumDao: insert/replace all, deleteByContentEntryId, getByContentEntryId (ordered by release_date desc)
  - TrackDao: insert/replace all, deleteByAlbumId, getByAlbumId (ordered by disc, track number),
    getIndexByUri(albumId, trackUri): returns the 0-based index of a track within its album
    (used to resume playback at the right chapter via skipToIndex)
  - FavoriteDao: insert, delete, getAll, existsByTrackUri
  - PlaybackPositionDao: upsert(LocalPlaybackPosition), getByProfileId(profileId)
    LocalPlaybackPosition entity: profile_id (PK), context_uri, track_uri, track_index, position_ms, updated_at
- SyncRepository in kids-app/data/repository/:
  - fullSync(profileId): calls API → maps DTOs to Room entities → stores in single Room transaction
    (delete old data, insert new data, all in @Transaction)
  - On first app launch after pairing: full sync
  - Error handling: if network fails, return cached data from Room
- ContentRepository: reads from Room, provides flows for UI (Flow<List<LocalContentEntry>>,
  Flow<List<LocalAlbum>>, Flow<List<LocalTrack>>)
- Replace MockContentProvider with ContentRepository in all ViewModels:
  - HomeViewModel gets content counts from Room
  - BrowseViewModel gets entries by content type from Room, exposes entries as Flow<List<LocalContentEntry>>
  - BrowseViewModel tile tap dispatches NavigateIntent based on LocalContentEntry.scope (§5.1.8):
    - ARTIST → navigate to AlbumGridScreen(contentEntryId)
    - ALBUM / PLAYLIST → navigate to TrackListScreen(albumId = entry's first LocalAlbum id)
    - TRACK → signal playback intent (no-op for now, implemented in 4.4)
  - AlbumGridScreen (new screen): shows paginated 2x2 grid of LocalAlbum rows for a given contentEntryId;
    tapping an album navigates to TrackListScreen(albumId)
  - TrackListScreen (new screen): shows vertical list of LocalTrack rows for a given albumId,
    ordered by disc_number ASC, track_number ASC; tapping a track signals playback intent (no-op for now)
  - NowPlayingViewModel plays trackUri from LocalTrack in Room
- Scope badge on BrowseScreen tiles (Music + Audiobook screens):
  Each LocalContentEntry tile shows a small pill/chip in the bottom-left corner of the cover art
  indicating its scope so kids (and parents during setup) can see what they added:
    - ARTIST  → "Künstler"  (purple tint)
    - ALBUM   → "Album"     (blue tint)
    - PLAYLIST → "Playlist" (teal tint)
    - TRACK   → "Song"      (no badge — single tracks are self-evident from context)
  The badge uses a semi-transparent dark background so it's readable on any cover art color.
  AlbumGridScreen and TrackListScreen tiles do NOT show scope badges (they are all albums/tracks by definition).
- Coil image loading configured with 200MB disk cache for cover art

REFERENCE: PROJECT_PLAN.md §5.1.5 (Local Room DB schema, complete offline data flow, caching strategy),
§5.1.8 (hierarchical browse – scope-driven navigation), §6.1 Sync endpoints (response format).

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 4.4 – Spotify Playback Integration ✅

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
  - IMPORTANT: Never call play(trackUri) alone — always play as a context so Spotify
    auto-advances through chapters. Use album URI as the context.
  - playFromChapter(albumUri: String, trackIndex: Int): calls PlayerApi.play(albumUri) then
    PlayerApi.skipToIndex(albumUri, trackIndex) so playback starts at the chosen chapter
    and auto-advances through subsequent chapters
  - playAlbumFromStart(albumUri: String): shortcut for playFromChapter(albumUri, 0)
  - pause(), resume(), skipNext(), skipPrevious()
  - seekTo(positionMs: Long)
  - nowPlaying: StateFlow<NowPlayingState>:
    { trackUri, title, artist, imageUrl, durationMs, positionMs, isPlaying,
      chapterIndex: Int?, totalChapters: Int? }
    chapterIndex/totalChapters are populated by looking up the currently playing trackUri
    in LocalTrack Room table to get track_number and parent album's total_tracks
    (only non-null when content_type == AUDIOBOOK)
  - Observes Spotify's PlayerState updates (via subscribeToPlayerState) and maps to NowPlayingState
  - Persists playback position to LocalPlaybackPosition via PlaybackPositionDao:
    throttled write every 5 seconds + on pause + on app background
    (stores: context_uri = albumUri, track_uri, track_index, position_ms, updated_at)
- PlayerViewModel updated to use PlaybackController:
  - On init: reads LocalPlaybackPosition for the profile → pre-populates NowPlayingState
    so mini-player bar is populated even before the user taps play
  - NowPlayingScreen shows real track info from nowPlaying StateFlow
  - For AUDIOBOOK: shows "Kapitel N von M" subtitle using chapterIndex + totalChapters
  - Play/pause button toggles real playback
  - Skip forward/back works (Spotify handles context-aware skipping natively)
  - Progress bar updates in real-time (poll every 500ms or use Spotify's callback)
- MiniPlayerBar updated to show real nowPlaying data, tappable to expand to NowPlayingScreen
- TrackListScreen (introduced in 4.3) wired up for playback:
  - MUSIC: tapping any track → playFromChapter(albumUri, trackIndex) — plays album from that track,
    auto-advances through remaining tracks; never play(trackUri) alone
  - AUDIOBOOK: tapping any chapter → renamed to ChapterListScreen (see below); same playFromChapter logic
- AlbumGridScreen (introduced in 4.3) wired up:
  - MUSIC album tile tap → playAlbumFromStart(albumUri) (no intermediate track list for music albums;
    tapping an album starts playing it immediately from track 1)
  - AUDIOBOOK album tile tap → navigates to ChapterListScreen instead of playing immediately
- ChapterListScreen (rename/refine of AUDIOBOOK TrackListScreen):
  - Displays vertical list of LocalTrack ordered by disc_number ASC, track_number ASC
  - Shows album art, album title, chapter count and total duration at top
  - Each chapter row: title, duration, small progress bar if this is the last-played chapter
  - ▶ resume indicator on the chapter that matches LocalPlaybackPosition.track_uri
  - Tapping any chapter: playFromChapter(albumUri, trackIndex)
  - Tapping the resume chapter: playFromChapter(albumUri, trackIndex) then seekTo(position_ms)
- Playlist playback: LocalContentEntry with scope=PLAYLIST has a single LocalAlbum row representing
  the playlist. Tapping a track in its TrackListScreen calls:
  playFromPlaylist(playlistUri: String, trackIndex: Int):
    PlayerApi.play(playlistUri) then PlayerApi.skipToIndex(playlistUri, trackIndex)
  playlistUri is LocalContentEntry.spotify_uri (spotify:playlist:xxx)
- Favorites sequential playback (BrowseScreen category=FAVORITES):
  - Tapping any favorite tile starts playing that track AND queues all other favorites after it.
  - PlaybackController.playFavoritesFrom(favorites: List<LocalFavorite>, startIndex: Int):
    - Plays favorites[startIndex].spotify_track_uri directly (bare track URI is acceptable here
      since favorites are individual tracks from mixed contexts with no shared album/playlist)
    - Subscribes to PlayerState; when a track ends (playerState.isPaused && position == 0),
      advances to the next track in the favorites list (wraps around)
    - The in-progress favorites queue is held in a local StateFlow<List<String>> (track URIs)
      in PlaybackController; cleared when playback is started from a non-favorites context

REFERENCE: PROJECT_PLAN.md §2.2 (Spotify App Remote SDK), §5.1.5 (offline playback flow –
track URIs come from Room, not network), §5.1.7 (sequential & continuous playback, chapter list UX,
playback position persistence schema), §5.1.8 (hierarchical browse), §5.1.9 (playback context rules).
Spotify App Remote SDK docs: https://developer.spotify.com/documentation/android/

CONSTRAINTS:
- Track URIs MUST come from LocalTrack in Room DB, NOT from any network call
- Do NOT call Spotify Web API from the kids app – all data is in Room
- NEVER call play(trackUri) alone for albums or playlists — always use playFromChapter/playFromPlaylist
  so Spotify maintains the context and auto-advances tracks
- Favorites are the ONLY case where bare track URIs are played one-at-a-time (no shared context)
- Do NOT implement queueing or playlist management beyond the favorites sequential queue
- Handle connection errors gracefully: show friendly error screen (detailed error screens come in phase 8)
- The Spotify AAR must be manually downloaded – document the path in a comment

VERIFICATION:
- Integration test: SpotifyRemoteManager connection lifecycle (mock or stub the SDK for unit tests)
- Unit test: PlaybackController.playFromChapter calls PlayerApi.play(albumUri) then
  PlayerApi.skipToIndex(albumUri, trackIndex) in that order
- Unit test: PlaybackController state transitions (play → isPlaying=true, pause → isPlaying=false)
- Unit test: position persistence – playback state update → PlaybackPositionDao.upsert called
  with correct context_uri, track_uri, track_index, position_ms
- Unit test: NowPlayingState.chapterIndex and totalChapters populated correctly from Room lookup
  for AUDIOBOOK content_type; null for MUSIC content_type
- Unit test: TrackDao.getIndexByUri returns correct 0-based index within album
- UI test: AUDIOBOOK album tile → ChapterListScreen shows chapters in order with correct titles
- UI test: last-played chapter has ▶ resume indicator; tapping it calls seekTo with saved position_ms
- Manual test on real device: tap music tile → music plays → pause → skip works
  → MiniPlayerBar updates → progress bar moves
- Manual test on real device: tap audiobook tile → chapter list → tap chapter 3 → chapter 3 plays
  → chapter 4 starts automatically when chapter 3 ends → skip works
- Manual test: play chapter 3 partway through → close app → reopen → mini-player shows
  saved position, tapping resume plays chapter 3 from saved position
- Run ./gradlew test → all tests pass (unit tests that don't depend on real Spotify SDK)
- Run ./gradlew updateDebugScreenshotTest → new PNG generated for ChapterListScreen @Preview
- Confirm ChapterListScreen PNG looks correct (album art header, chapter list, resume indicator),
  then commit it to the repo
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 4.5 – Kids App Real Favorites + Spotify Liked Songs Sync ✅

```
CONTEXT: Phase 4 of KidsTune. Playback works (4.4). We now implement real favorites that
persist to Room and sync to the backend. KidsTune favorites are also mirrored to Spotify
"Lieblingssongs" (Liked Songs) bidirectionally: a heart tap in KidsTune adds the track to
Spotify Liked Songs, and removal mirrors back. This uses the child's profile-level Spotify
token already stored on the backend (set up in 2.5). If no child Spotify account is linked,
favorites silently work as standalone (no errors, no Spotify calls).

GOAL: When this task is done:

Kids App:
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

Backend (new, in favorites/ package):
- SpotifyFavoriteSyncService:
  - mirrorAdd(profileId, trackUri): if SpotifyTokenService.isProfileSpotifyLinked(profileId),
    calls PUT https://api.spotify.com/v1/me/tracks?ids={trackId} using
    SpotifyTokenService.getValidProfileAccessToken(profileId).
    trackId is extracted from the Spotify URI (spotify:track:{id} → {id}).
    Silently no-ops (log.debug) if profile has no linked Spotify account.
  - mirrorRemove(profileId, trackUri): same guard, calls DELETE /v1/me/tracks?ids={trackId}
  - Both methods swallow non-fatal Spotify API errors (log.warn) so a Spotify failure
    never breaks the KidsTune favorite operation
- FavoritesController (POST /api/v1/profiles/{profileId}/favorites):
  - Creates Favorite row in DB (existing behaviour)
  - Calls SpotifyFavoriteSyncService.mirrorAdd() asynchronously (Mono.fromRunnable on
    Schedulers.boundedElastic, subscribeOn, no await — fire and forget)
- FavoritesController (DELETE /api/v1/profiles/{profileId}/favorites/{uri}):
  - Deletes Favorite row (existing behaviour)
  - Calls SpotifyFavoriteSyncService.mirrorRemove() asynchronously

REFERENCE: PROJECT_PLAN.md §2.2 (per-profile Spotify tokens), §4.5 (Spotify Liked Songs sync
design), §5.1.5 (LocalFavorite schema with synced flag, offline data flow),
§6.1 Favorites endpoints. CLAUDE.md pitfall #8 (never use family token for per-child operations).

CONSTRAINTS:
- Favorites persist locally even when offline (synced=false, uploaded later)
- Spotify mirror is fire-and-forget: a Spotify API error must never fail the KidsTune favorite
- Use SpotifyTokenService.getValidProfileAccessToken(profileId) — NOT the family token
- extracting trackId from URI: only spotify:track:* URIs should be mirrored (skip albums/playlists/artists)
- Do NOT implement the Discover screen
- Do NOT implement content requests
- Do NOT add WorkManager sync yet (still manual sync on app launch for now)

VERIFICATION:
- Unit test: ToggleFavoriteUseCase adds and removes favorites correctly
- Unit test: FavoriteRepository.isFavorite() emits true after add, false after remove
- Unit test: getUnsynced() returns only favorites with synced=false
- UI test: NowPlayingScreen → tap heart → Favorites tab shows the track → tap heart again → removed
- Integration test: add favorite (synced=false) → run sync → verify POST called → synced=true
- Unit test: SpotifyFavoriteSyncService.mirrorAdd with unlinked profile → no Spotify call, no exception
- Unit test: SpotifyFavoriteSyncService.mirrorAdd with linked profile → PUT /v1/me/tracks called with correct trackId
- Unit test: mirrorAdd for a non-track URI (spotify:album:*) → no Spotify call
- Unit test: Spotify API returns 500 → mirrorAdd swallows error, does not rethrow
- Run ./gradlew test → all tests pass
```

---

## Phase 5 – Device Pairing & Sync (Week 7)

### Prompt 5.1 – Backend Device Pairing ✅

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
- Do NOT modify Kids App (next prompts)
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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 5.2 – Backend Delta Sync ✅

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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 5.3 – Kids App Pairing Flow ✅

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
- Run ./gradlew updateDebugScreenshotTest → new PNG generated for PairingScreen @Preview
  (number pad, digit input fields, Connect button)
- Confirm PNG looks correct, then commit it to the repo
```

### Prompt 5.4 – Kids App Sync Manager (WorkManager) ✅

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

---

## Phase 6 – Import, Offline & Samsung Kids (Week 8)

### Prompt 6.1 – Backend Import Service + Liked Songs Pre-population ✅

```
CONTEXT: Phase 6 of KidsTune. We implement the Spotify listening history import to ease
onboarding. Each child has their own Spotify account linked to their ChildProfile (set up
in prompt 2.5). The import fetches each CHILD's own listening history using the
child's profile-level Spotify token — NOT the parent's family-level token.

As part of import, we also pre-populate KidsTune favorites from the child's existing Spotify
Liked Songs ("Lieblingssongs"). Only tracks that are part of the child's allowed content are
imported as favorites — this preserves the safety model. Tracks in Liked Songs that are not
in the allowed content list are silently skipped (they may not be children's content).

GOAL: When this task is done:
- SpotifyImportService in spotify/ package:
  - getImportSuggestions(profileId) → resolves the child's Spotify token via
    SpotifyTokenService.getValidProfileAccessToken(profileId) and calls:
    - GET /v1/me/player/recently-played (limit 50)
    - GET /v1/me/top/artists?time_range=medium_term (limit 50)
    - GET /v1/me/top/artists?time_range=long_term (limit 50)
    - GET /v1/me/playlists (limit 50)
    All calls authenticated with the child's access token (not the family token).
  - Throws ProfileSpotifyNotLinkedException if the profile has no linked Spotify account
  - Deduplicates artists across all sources
  - Groups results into:
    - detectedChildrenContent: items matching children's heuristic (pre-selected)
    - playlists: user playlists containing likely children's content
    - otherArtists: everything else (not pre-selected)
  - Applies age-based pre-selection using the profile's own age_group:
    - Loads known-artists.yml min_age
    - Compares with profile's age_group (TODDLER=0-3, PRESCHOOL=4-6, SCHOOL=7-12)
  - Returns ImportSuggestionsDto (scoped to the single profile, no per-profile flags needed)
- importLikedSongsAsFavorites(profileId): new method on SpotifyImportService:
  - Calls GET /v1/me/tracks (paginated, up to 200 tracks) with child's token
  - For each liked track URI, checks if a ResolvedTrack with that URI exists for this profile
    (via ResolvedTrackRepository or a JOIN query)
  - For matching tracks: creates a Favorite row if one doesn't already exist
  - Returns count of favorites created
  - Silently no-ops if profile Spotify is not linked (no exception)
- POST /api/v1/content/import endpoint (already defined) now fully implemented:
  - Accepts { items: [{ spotifyUri, scope, profileIds[] }] }
  - Creates AllowedContent rows per profile
  - Triggers ContentResolver for each new entry
  - Returns { created: int, profiles: [{ id, name, newContentCount }] }
- POST /api/v1/profiles/{profileId}/import-liked-songs (new, web-dashboard-only endpoint,
  requires session auth — NOT JWT):
  - Calls importLikedSongsAsFavorites(profileId)
  - Returns { imported: int }
  - Called by the import wizard (6.2) after content import completes

REFERENCE: PROJECT_PLAN.md §2.2 (Spotify account model — per-profile tokens), §4.5 (Spotify
Liked Songs sync design), §5.2.2 (import flow — per-profile, uses child's own Spotify history),
§5.2.2 (known-artists.yml format with age ranges), §6.1 Content Management import endpoint.

CONSTRAINTS:
- Do NOT modify Android apps
- Use SpotifyTokenService.getValidProfileAccessToken(profileId) — NOT getValidAccessToken(familyId)
- Genre-based heuristic reuses ContentTypeClassifier from phase 2
- Known-artists.yml reuses KnownChildrenArtists from phase 2
- If profile's Spotify account is not linked: return 409 with error "SPOTIFY_NOT_LINKED"
  for getImportSuggestions; importLikedSongsAsFavorites silently no-ops (no error)
- Safety: only import as favorites tracks that are already in resolved content for this profile —
  never create Favorite rows for content the parent hasn't approved

VERIFICATION:
- Unit test: age-based pre-selection logic for a PRESCHOOL profile:
  - "Bibi & Tina" (min_age 3) → pre-selected
  - "Die drei ??? Kids" (min_age 6) → NOT pre-selected (age 4-6 is borderline — deselect to be safe)
  - "Die drei ???" (min_age 10) → NOT pre-selected
- Unit test: getImportSuggestions with unlinked profile → throws ProfileSpotifyNotLinkedException
- Unit test: importLikedSongsAsFavorites with unlinked profile → returns 0, no exception
- Unit test: importLikedSongsAsFavorites — liked track URI matches a resolved track for profile → Favorite created
- Unit test: importLikedSongsAsFavorites — liked track URI NOT in resolved content → skipped, no Favorite
- Unit test: importLikedSongsAsFavorites — favorite already exists → not duplicated
- Integration test with MockWebServer: mock Spotify responses using CHILD's token →
  verify grouped results correctly use profile token (not family token)
- Integration test: unlinked profile → 409 with SPOTIFY_NOT_LINKED
- Integration test: POST /api/v1/content/import with 3 items, 2 profiles →
  verify 6 AllowedContent rows created, ContentResolver triggered 6 times
- Integration test: importLikedSongsAsFavorites with 5 liked tracks, 3 in resolved content →
  3 Favorite rows created, 2 skipped
- Run ./gradlew test → all tests pass
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 6.2 – Web Dashboard Import Wizard + Liked Songs Import ✅

```
CONTEXT: Phase 6 of KidsTune. Backend import service exists (6.1), including
importLikedSongsAsFavorites(). The web dashboard foundation exists (2.4). We build the
import wizard as a web dashboard page. After content import completes, the wizard also
offers a "Import Lieblingssongs" step that pre-populates the child's KidsTune favorites
from their Spotify Liked Songs.

GOAL: When this task is done:
- ImportWebController (com.kidstune.web) with pages:
  - GET /web/import → step 1: profile selector with checkboxes, "Fetch Suggestions" button
  - POST /web/import/suggestions → HTMX partial: calls SpotifyImportService.getImportSuggestions(),
    returns suggestion cards grouped into:
    - "Detected children's content" section (pre-checked): image, name, "Will add as: Artist",
      per-profile toggle checkboxes (pre-selected based on age heuristic from backend)
    - "Your playlists" section with per-profile checkboxes
    - "Other artists" section (unchecked by default)
    Returns web/fragments/import-suggestions.html partial
  - POST /web/import → bulk-add all checked items: calls ContentService.addContentBulk()
    per selected item; after content import, for each profile that has a linked Spotify account:
    calls SpotifyImportService.importLikedSongsAsFavorites(profileId) and records the count;
    redirect to success page showing:
    "Added X items for Luna (+ Y Lieblingssongs imported as favorites), ..."
- Thymeleaf templates:
  - web/import/step1.html: profile checkboxes, "Fetch Suggestions" HTMX trigger
  - web/import/step2.html: HTMX target div that receives suggestion fragments, Import button,
    step summary ("X items selected for Y profiles")
  - web/import/success.html: confirmation with added counts per profile + favorites imported count
    (only shown if > 0 favorites were imported; if 0, just show content count)
  - web/fragments/import-suggestions.html: HTMX partial for suggestion groups
- Navigation: sidebar link "Importieren" → /web/import already exists in layout

REFERENCE: PROJECT_PLAN.md §4.5 (Spotify Liked Songs sync design), §5.2.2 (import flow with
per-profile age-based pre-selection), §6.1 (SpotifyImportService already implemented in 6.1).

CONSTRAINTS:
- Do NOT modify kids-app
- Do NOT modify existing REST API endpoints or services
- Web controller calls SpotifyImportService directly, never HTTP-loops to own REST API
- Import wizard does NOT need WebSocket live progress — HTMX form submission is sufficient
- Reuse ContentService.addContentBulk() from the backend service layer
- Liked Songs import is best-effort: if it fails or profile has no Spotify linked, silently show 0;
  never fail the whole import because of it

VERIFICATION:
- GET /web/import → profile checkboxes shown
- POST /web/import/suggestions (HTMX) → suggestion groups returned as partial HTML
- Pre-selected items match age heuristic (verify with a SCHOOL profile vs TODDLER profile)
- Submit import → redirect to success page with correct added-item counts per profile
- Success page shows favorites imported count for profiles with Spotify linked
- Success page does not mention favorites for profiles without Spotify linked
- Integration test: ImportWebController mock Spotify suggestions → verify HTMX partial rendered
  with correct pre-selections
- Integration test: import with profile that has Spotify linked → importLikedSongsAsFavorites called
- Integration test: import with profile that has NO Spotify linked → importLikedSongsAsFavorites
  not called (or silently returns 0), success page shows only content count
- Run ./gradlew test → all tests pass
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 6.3 – Kids App Offline Hardening ✅

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

### Prompt 6.4 – Samsung Kids Compatibility ✅

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

### Prompt 6.5 – MediaSession / Media Notification Ownership ✅

```
CONTEXT: Phase 6 of KidsTune. Samsung Kids compatibility is verified (6.4). There is a known
containment gap: when Samsung Kids' time limit expires, its lock screen does not fully block the
notification shade on most One UI versions. Spotify registers its own media notification, and
tapping it fires a PendingIntent that opens Spotify directly — bypassing Samsung Kids' app
whitelist. KidsTune closes this by owning the media notification itself.

GOAL: When this task is done:
- KidsTune registers a MediaSession (androidx.media3) inside a MediaBrowserService:
  - MediaBrowserService runs as a foreground service, started when the kids app starts
    and kept alive while the app is in use
  - Service is declared in AndroidManifest with the MEDIA_CONTENT_CONTROL permission and
    the correct intent filter (MediaBrowserServiceCompat action)
- KidsTune mirrors Spotify's playback state into its own MediaSession:
  - Subscribe to SpotifyAppRemote.PlayerApi.subscribeToPlayerState() in the service
  - On each PlayerState update:
    - Update MediaMetadata: track title, artist, album, duration, artwork (Bitmap via Coil)
    - Update PlaybackState: STATE_PLAYING / STATE_PAUSED / STATE_STOPPED, playback position
    - Playback position calculated as snapshotPosition + elapsedTime for smooth seek bar
  - On App Remote disconnect: set state to STATE_STOPPED, clear metadata
- The media notification is KidsTune's (not Spotify's):
  - Android shows KidsTune's notification in the shade instead of Spotify's
  - Tapping the notification opens KidsTune (via a PendingIntent to MainActivity) — NOT Spotify
  - Play/pause and skip buttons in the notification call back into the service, which then
    calls PlayerApi.resume() / PlayerApi.pause() / PlayerApi.skipNext() on the App Remote
- Artwork loading:
  - Load track image_url as Bitmap using Coil's ImageLoader.execute() (suspend, not compose)
  - Set on MediaMetadata.Builder via putBitmap(METADATA_KEY_ALBUM_ART, bitmap)
  - Cache the last loaded bitmap to avoid re-downloading on repeated state updates for the same track
- Seek bar interaction:
  - MediaSession receives ACTION_SEEK_TO → call PlayerApi.seekTo(position)
- On KidsTune app foreground: connect App Remote, start mirroring
- On KidsTune app background / process death: service continues running, maintains MediaSession

REFERENCE: PROJECT_PLAN.md §5.1.4 (Samsung Kids containment gap, MediaSession implementation notes).

CONSTRAINTS:
- Use androidx.media3 MediaSession, NOT the deprecated MediaSessionCompat
- KidsTune does NOT manage audio focus — Spotify owns playback, KidsTune only mirrors state
- Do NOT attempt to cancel or dismiss Spotify's own MediaSession — Android will use KidsTune's
  because it is the active session; don't fight Spotify's internals
- Do NOT modify backend
- Service must survive the activity being backgrounded (foreground service with ongoing notification)
- Test on a real Samsung device if possible — media session priority behaviour is device-specific

VERIFICATION:
- Play a track via KidsTune → notification shade shows KidsTune's player (not Spotify's)
  with correct track title, artist, artwork
- Tap play/pause in notification → Spotify responds correctly
- Tap the notification body → KidsTune app opens (not Spotify)
- Pause in KidsTune UI → notification updates to paused state within ~200ms
- Skip track → notification updates to new track metadata including artwork
- Kill App Remote connection (force-stop Spotify) → notification clears / shows stopped state
- Unit test: PlayerState subscriber correctly maps to MediaMetadata + PlaybackState
- Unit test: artwork caching — same image_url on consecutive state updates → Coil called once
- Manual test on Samsung device: Samsung Kids time limit expires → swipe down notification shade →
  KidsTune notification visible → tap → KidsTune opens, NOT Spotify
- Run ./gradlew test → all tests pass
```

---

## Phase 7 – Content Requests & Notifications (Weeks 9-10)

**Notification strategy (no parent app, no FCM):**
1. **Web Push (VAPID)** — push notification to parent's phone/browser when a new request arrives.
   Works from any browser that supports the Push API (Chrome, Firefox, Safari 16.4+). The
   parent subscribes once from the dashboard; the subscription endpoint is stored per-family.
2. **Email** — unchanged reliable catch-all; already built in 2.6. Sent immediately on request
   creation and as a daily digest at 19:00.
3. **SSE (Server-Sent Events)** — updates the pending-requests badge on the web dashboard if
   the tab happens to be open. Simpler than WebSocket since the dashboard only needs one-way
   server→client updates.

No WebSocket hub, no multi-layer complexity, no Android parent app.

### Prompt 7.1 – Backend Content Request Workflow ✅

```
CONTEXT: Phase 7 of KidsTune. There is no parent app; parents manage content via the web
dashboard. We implement the content request lifecycle: kids request → parents notified →
approve/reject → content created.

GOAL: When this task is done:
- ContentRequest entity + Liquibase migration (007-content-requests.yaml):
    id (UUID), profile_id (FK), spotify_uri, title, image_url, artist_name,
    content_type (MUSIC/AUDIOBOOK), status (PENDING/APPROVED/REJECTED/EXPIRED),
    created_at, resolved_at, resolved_by (family_id), parent_note, digest_sent_at
- ContentRequestService in requests/ package:
  - createRequest(profileId, spotifyUri, title, imageUrl, artistName, contentType):
    - Validates max 3 PENDING requests per profile (throw 429 if exceeded)
    - Creates ContentRequest with status=PENDING
    - Sends email notification to family.notification_emails (reuse EmailNotificationService)
    - Returns created request DTO
  - approveRequest(requestId, approvedByProfileIds?, note?, contentTypeOverride?):
    - Sets status=APPROVED, resolved_at=now, resolved_by
    - Creates AllowedContent for the requesting profile (or specified profiles)
    - contentTypeOverride: overrides content type (kids can't distinguish MUSIC vs AUDIOBOOK
      from Spotify search; parent corrects it in the approval UI)
    - Triggers ContentResolver to populate albums/tracks
    - Returns updated request DTO
  - rejectRequest(requestId, note?):
    - Sets status=REJECTED, resolved_at=now, parent_note
    - Returns updated request DTO
  - bulkApprove(requestIds[], contentTypeOverride?, note?) / bulkReject(requestIds[], note?)
  - listRequests(familyId, statusFilter?, profileFilter?) → paginated list
  - getPendingCount(familyId) → { profiles: [{ id, name, count }], total: int }
  - expireStaleRequests(): @Scheduled(cron = "0 0 3 * * *")
    - Sets PENDING requests older than 7 days to EXPIRED
  - sendDailyDigest(): @Scheduled(cron = "0 0 19 * * *")
    - For each family with PENDING requests not yet in today's digest:
      builds summary and sends email via EmailNotificationService
      sets digest_sent_at on affected requests
- ContentRequestController (REST):
  - POST /api/v1/profiles/{profileId}/content-requests → createRequest
  - GET  /api/v1/content-requests?familyId=&status=&profileId= → listRequests
  - GET  /api/v1/content-requests/pending-count?familyId= → getPendingCount
  - POST /api/v1/content-requests/{id}/approve → approveRequest
  - POST /api/v1/content-requests/{id}/reject  → rejectRequest
  - POST /api/v1/content-requests/bulk-approve → bulkApprove
  - POST /api/v1/content-requests/bulk-reject  → bulkReject
- One-click approve link: GET /web/approve/{token} (public, no login):
  - Token is a signed JWT (familyId + requestId, 7-day expiry) embedded in the email
  - Redirects to web dashboard requests page on success

REFERENCE: PROJECT_PLAN.md §5.1.6 (request limits, auto-expiry), §5.2.3 (notification
strategy), §6.1 Content Requests API.

CONSTRAINTS:
- No WebSocket, no Android modifications
- ContentResolver from phase 4 handles content resolution after approval
- Email template for request notification already exists from 2.6 — reuse it

VERIFICATION:
- Integration test: create request → PENDING in DB → email dispatched
- Integration test: 4th request for same profile → 429 status
- Integration test: approve → AllowedContent created → ContentResolver triggered
- Integration test: reject with note → status REJECTED, parent_note stored
- Integration test: expire job → requests older than 7 days become EXPIRED
- Integration test: daily digest → email sent for family with 3 pending requests; digest_sent_at set
- Integration test: bulk approve 3 requests → all APPROVED
- Integration test: one-click approve token → valid token approves request; expired token rejected
- Run ./gradlew test → all tests pass
- Run the backend locally: cd backend && ./gradlew bootRun → app starts healthy
```

### Prompt 7.2 – Backend SSE + Web Push Notifications ⬅️

```
CONTEXT: Phase 7 of KidsTune. Content request workflow exists (7.1). We add two real-time
notification channels for the web dashboard: SSE (badge updates when tab is open) and
Web Push via VAPID (push notification to parent's phone/browser on new requests).

GOAL: When this task is done:
- SSE endpoint for dashboard badge updates:
  - GET /web/sse/requests?familyId= (requires dashboard session auth)
  - Returns text/event-stream; emits JSON event { "pendingCount": N } whenever a request
    is created, approved, rejected, or expired for the family
  - Uses Spring WebFlux Flux<ServerSentEvent<String>> with a Sinks.Many per family
  - SseRegistry singleton: registerSink(familyId), emit(familyId, count), cleanup on disconnect
  - ContentRequestService calls sseRegistry.emit() after every state change
  - Dashboard JS (htmx + vanilla fetch):
    - Opens EventSource on page load, listens for events
    - Updates the pending-requests badge count in the nav without a full page reload
- Web Push (VAPID) notifications:
  - VapidConfig: generates or loads VAPID key pair from application.yml on startup
    (kidstune.vapid.public-key, kidstune.vapid.private-key — generate once, store as env vars)
  - PushSubscription entity + Liquibase migration (008-push-subscriptions.yaml):
    id, family_id, endpoint (TEXT), p256dh (TEXT), auth (TEXT), created_at, user_agent
    One family can have multiple subscriptions (multiple browsers/devices)
  - PushController:
    - GET  /web/push/vapid-public-key → returns VAPID public key (for SW registration)
    - POST /web/push/subscribe   → saves PushSubscription for current family session
    - DELETE /web/push/unsubscribe → removes subscription by endpoint
  - PushNotificationService.sendRequestNotification(familyId, request):
    - Loads all PushSubscription rows for the family
    - Sends Web Push message to each endpoint using the java-webpush library
      (groupId: nl.martijndwars, artifactId: web-push, version: 5.x)
    - Payload: JSON { "title": "Neuer Musikwunsch", "body": "{childName} möchte {title}",
      "url": "/web/requests" }
    - Expired/gone endpoints (410/404) are automatically deleted
  - ContentRequestService calls pushNotificationService.sendRequestNotification() after createRequest
  - Dashboard service worker (static/sw.js):
    - Registered by dashboard layout JS; subscribes to push on first dashboard load (with
      user gesture or on notification permission grant)
    - On push event: shows notification with title, body, click → opens /web/requests
    - On notificationclick: focuses existing tab or opens new one

REFERENCE: Web Push Protocol, VAPID RFC 8292.

CONSTRAINTS:
- No Android modifications
- No WebSocket
- Web Push is best-effort: failure to send (e.g., subscription expired) must not break
  the request workflow
- Use the java-webpush library (already battle-tested), not a raw HTTP/2 implementation

VERIFICATION:
- Integration test: subscribe → create request → verify push payload sent to mock endpoint
- Integration test: 410 from push endpoint → subscription deleted
- Integration test: SSE sink emits correct count after approve/reject
- Unit test: VapidConfig loads key pair from config; generates new pair if absent
- Manual: open dashboard in browser → create request from Postman → browser notification
  appears; badge count updates without page reload
- Run ./gradlew test → all tests pass
- Run the backend locally → app starts healthy
```

### Prompt 7.3 – Kids App Discover Screen

```
CONTEXT: Phase 7 of KidsTune. Backend content requests work (7.1). The Discover screen already
exists with full mock UX from Prompt 3.4. We now wire it to real backend endpoints: replace mock
data with live Spotify search, real content request POSTs, and polling-based approval detection.

KEY DESIGN PRINCIPLE: Children can search and find ANY Spotify content (tracks, albums, artists,
playlists). There is NO play button — only a Request button. Playing becomes possible only after
the parent approves the request and the device syncs. The safety constraint is on playback, not
discovery.

GOAL: When this task is done:
- DiscoverScreen in kids-app/ui/discover/:
  - Search box with 72dp height, large text, microphone button for voice input
    (Android SpeechRecognizer for speech-to-text)
  - IDLE STATE (no query typed): GET /api/v1/spotify/suggestions → returns curated age-appropriate
    content from known-artists.yml on the backend; shown as large tiles with Request buttons.
    This gives pre-readers something to tap without needing to type.
  - ACTIVE SEARCH: GET /api/v1/spotify/search?q=... replaces idle tiles with live Spotify results
    (max 10 items, explicit content filtered by backend). Results are large tiles, each with a
    Request button — NO play button anywhere on this screen.
  - DiscoverTile model extended with scope: ContentScope.
    Backend search results carry the Spotify item type; map to ContentScope:
      Spotify "track" → TRACK, "album" → ALBUM, "artist" → ARTIST, "playlist" → PLAYLIST.
    Each result tile shows a scope badge (same pill style as BrowseScreen):
      TRACK → "Song", ALBUM → "Album", ARTIST → "Künstler", PLAYLIST → "Playlist"
    Idle suggestions from known-artists.yml are all ARTIST scope.
  - ALREADY-APPROVED FILTER: Before rendering any results (both idle suggestions and active search),
    cross-check each result's Spotify URI against LocalContentEntry in Room. Any item whose
    spotify_uri already exists in the profile's Room DB is silently dropped — it is already
    playable in the Music/Audiobooks tabs, so a Request button would be misleading.
    Pure local Room lookup; no extra network call needed.
  - Each result has a "🙏 Ich will das!" button → POST /api/v1/profiles/{id}/content-requests
    (or queue offline in OfflineQueue)
  - Request button DISABLED when 3 pending requests exist, with friendly message:
    "Du hast schon 3 Wünsche offen – warte bis Mama/Papa geantwortet hat!"
  - "My wishes" section below search:
    - Shows pending requests from GET /api/v1/content-requests?profileId= with kid-friendly
      time context:
      < 1 hour: "Mama/Papa schauen sich das an"
      1-24 hours: "Gestern gewünscht"
      > 24 hours: "Vor ein paar Tagen gewünscht"
    - Clock icon 🕐, no spinner
    - Rejected items: shown with ❌ and parent note (if any) for 24 hours, then hidden
    - Expired items: silently removed
  - Approval detection via polling: WorkManager's existing 15-min sync detects newly APPROVED
    requests (content appears in Room). DiscoverViewModel observes Room contentCount to detect
    when new content arrives and shows a celebration animation (confetti + cheering sound) if
    a previously-pending request is now APPROVED. New tile gets "NEW" badge for 24h.
  - Search rate-limited: 1 query per 5 seconds (debounce on input, toast on rapid retry)
- DiscoverViewModel: manages search state, pending requests, Room observation for approvals
- HomeScreen: Discover button (🔍) already navigates to DiscoverScreen

REFERENCE: PROJECT_PLAN.md §5.1.6 (Discover screen wireframe, free search design, request
limits, pending UX, time context strings, auto-expiry behavior).

CONSTRAINTS:
- Idle suggestions: GET /api/v1/spotify/suggestions (new backend endpoint, returns curated list
  from known-artists.yml – implement alongside this prompt if not already done)
- Search: GET /api/v1/spotify/search?q=...&limit=10 via backend proxy (requires internet)
- Content requests: POST /api/v1/profiles/{profileId}/content-requests (or queue offline)
- No WebSocket listener; approval detection is poll-based via WorkManager sync
- Do NOT play requested content – there is NO play button on this screen

VERIFICATION:
- UI test: search "Frozen" → results shown → tap Request → "My wishes" section shows pending item
- UI test: 3 pending requests → 4th request button disabled with message
- UI test: rejected request shows ❌ with note
- Unit test: time context strings ("Mama/Papa schauen sich das an" for < 1h, etc.)
- Unit test: search rate limiting (2 queries within 5s → second blocked)
- Unit test: already-approved filter — Room has entry with URI "spotify:album:abc" → search result
  with same URI is dropped from rendered list; result with different URI is kept
- Unit test: DiscoverViewModel detects Room count change → celebration animation triggered
- Run ./gradlew test → all tests pass
- Run ./gradlew updateDebugScreenshotTest → new PNGs generated for DiscoverScreen @Preview
  functions (search idle, search results, pending requests, approval celebration)
- Confirm PNGs look correct, then commit them to the repo
```

---

## Phase 8 – Admin Data Tables & Polish (Weeks 11-12)

### Prompt 8.1 – Web Dashboard Admin Data Tables

```
CONTEXT: Phase 8 of KidsTune. All web dashboard parent features exist (phases 2.4-2.6, 6.2). We add an admin section
with read access and CRUD operations for all persisted entities – for debugging, data correction,
and direct operational control without needing a DB client.

GOAL: When this task is done:
- AdminWebController (com.kidstune.web.admin) with pages for each entity:
  Family:
    - GET /web/admin/families → table: id (truncated), spotify_user_id (masked, last 6 chars),
      created_at, profile count; no edit (OAuth-managed)
  ChildProfile (cross-family admin view):
    - GET /web/admin/profiles → paginated table of ALL profiles across ALL families (50/page):
      family id, name, avatar, age_group, content count, created_at
    - GET /web/admin/profiles/{id}/edit → edit form (name, avatar icon/color, age_group)
    - POST /web/admin/profiles/{id} → save via ProfileService.updateProfile()
    - POST /web/admin/profiles/{id}/delete → HTMX confirmation → hard delete
  AllowedContent:
    - GET /web/admin/content → paginated table (50/page): profile name, title, artist, scope badge,
      type badge, spotify_uri (truncated), created_at; column header click = sort
    - POST /web/admin/content/{id}/edit → editable fields: content_type override, scope (not URI)
    - POST /web/admin/content/{id}/delete → HTMX confirmation → delete; cascades to ResolvedAlbum/Track
  ResolvedAlbum + ResolvedTrack (read-only + re-resolve):
    - GET /web/admin/resolved → table: allowed_content title, album count, last resolved_at;
      expandable rows (HTMX hx-get) showing album list per entry
    - GET /web/admin/resolved/{entryId}/albums → HTMX partial: album list for entry with track count
    - GET /web/admin/resolved/albums/{albumId}/tracks → HTMX partial: track list for album
    - POST /web/admin/resolved/{entryId}/re-resolve → triggers ContentResolver.resolveAsync(entryId),
      returns success flash message; resolver runs in background as usual
  Favorite:
    - GET /web/admin/favorites → paginated table: profile name, track title, artist, added_at,
      synced status, Spotify-mirrored badge (shown if profile has linked Spotify);
      filter by profile via dropdown
    - POST /web/admin/favorites/{id}/delete → HTMX confirmation → delete
    Note: the per-profile favorites tab on the profile content page
    (GET /web/profiles/{id}/content?tab=favorites) is also implemented here as a tab
    alongside the existing content list, showing the child's current favorites with
    added_at and a delete button (same HTMX pattern as content delete)
  ContentRequest:
    - GET /web/admin/requests → paginated table: profile, title, status badge, requested_at,
      resolved_at, parent_note; filter by status dropdown
    - POST /web/admin/requests/{id}/expire → manually set status=EXPIRED
    - POST /web/admin/requests/{id}/delete → HTMX confirmation → hard delete
  PairedDevice:
    - GET /web/admin/devices → table: device_name, device_type badge, profile name, last_seen_at,
      created_at
    - POST /web/admin/devices/{id}/delete → HTMX confirmation → delete (effectively unpairs)
- Shared components:
  - Reusable pagination fragment (previous/next links with page number display)
  - Reusable HTMX confirmation modal fragment (message + confirm + cancel buttons)
  - Column sort links (append ?sort=field&dir=asc|desc to current URL)
- Admin section linked from sidebar as "Admin ▸" with sub-items in a collapsible group
- Danger Zone page at GET /web/admin/danger-zone (§5.2.3):
  - Shows a red-bordered "Danger Zone" card explaining what will be deleted
  - Single button "Wipe All Data and Start From Scratch" triggers a two-step HTMX confirmation:
    Step 1: button replaced with text input "Type DELETE to confirm" + [Confirm] button
    Step 2: POST /web/admin/danger-zone/wipe (only if input == "DELETE")
  - Wipe endpoint runs in a @Transactional block, deletes in FK-safe order:
    ContentRequest → Favorite → ResolvedTrack → ResolvedAlbum → AllowedContent →
    PairedDevice → ChildProfile → Family
  - On success: redirects to /web/register (fresh start)
  - On failure: shows error page with exception message (transaction rolled back, data intact)
  - The wipe does NOT drop schema or Liquibase changelog history — data rows only
  - Danger Zone is linked at the bottom of the Admin sidebar section with a ⚠️ icon

REFERENCE: PROJECT_PLAN.md §4.1 (all entity definitions, cascade rules), §4.2 (content scope),
§5.2.3 (Danger Zone full spec including UX wireframe).
CLAUDE.md: cascade delete rules (profile → content → resolved → favorites, requests).

CONSTRAINTS:
- Admin section uses the SAME email/password session auth as the rest of /web/** (no extra password)
- Use existing services where they have suitable methods; use repositories directly only for
  cross-family read queries that no service provides (e.g., list all profiles across all families)
- ResolvedAlbum and ResolvedTrack are read-only – no edit forms, only delete via parent AllowedContent
- Do NOT modify REST API or Android apps

VERIFICATION:
- GET /web/admin/families → families listed with masked spotify_user_id
- GET /web/admin/content → paginated; clicking column header → results re-sorted
- Edit AllowedContent type override → saved, visible in list
- Delete AllowedContent → confirm modal → deleted; verify ResolvedAlbum rows also gone from DB
- GET /web/admin/resolved → content tree; expand row via HTMX → albums listed; expand album → tracks listed
- POST re-resolve → ContentResolver triggered; resolved_at updated (may need short delay to verify)
- GET /web/admin/favorites → filter by profile → shows only that profile's favorites
- GET /web/admin/requests → all statuses shown; filter by PENDING → only pending shown
- GET /web/admin/devices → all paired devices listed
- GET /web/admin/danger-zone → Danger Zone card shown with red border; button visible
- Wipe flow: click button → text input appears → type "DELETE" → confirm → redirect to /web/register
  → verify DB is empty (login fails, /web/register loads fresh)
- Wipe with wrong text ("delete" lowercase) → confirm button disabled or shows validation error
- Run ./gradlew test → all tests pass
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

---

## Phase 8 (continued) – Polish, Hardening & Documentation

### Prompt 8.2 – Kids App Animations

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
- Run ./gradlew updateDebugScreenshotTest → update existing reference PNGs to capture polished
  animations states (e.g., FavoriteButton filled state, confetti frame, transition states)
- Review diffs against previous reference PNGs, confirm intentional changes, commit updated PNGs
```

### Prompt 8.3 – Kids App Edge Cases

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
- Do NOT change backend
- Keep existing functionality working – error screens are fallbacks only

VERIFICATION:
- Unit test: SpotifyRemoteManager connection errors correctly detected per type
- UI test: each error screen renders with correct illustration and text
- UI test: storage full error shown when Room write throws IOException
- Run ./gradlew test → all tests pass
- Run ./gradlew updateDebugScreenshotTest → new PNGs generated for each error screen
  (SpotifyNotInstalled, SpotifyNotLoggedIn, NoNetwork, StorageFull)
- Confirm PNGs look correct (friendly illustrations, correct text), then commit them to the repo
```

### Prompt 8.4 – Kids App Accessibility Audit

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
- Do NOT modify backend

VERIFICATION:
- Custom touch target assertion passes for all screens
- All UI tests still pass with new contentDescriptions
- ACCESSIBILITY.md documents: contrast ratios, TalkBack results, known limitations
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
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
```

### Prompt 8.6 – End-to-End Tests

```
CONTEXT: Phase 8 of KidsTune. All features implemented and hardened. We write E2E tests
covering critical user journeys.

GOAL: When this task is done:
- Maestro YAML test suites in e2e-tests/ directory:
  Test 1 – Fresh Setup:
    - Open web dashboard in browser → login via Spotify → create profile "Luna" → add "Bibi & Tina" artist
    - Launch kids app → enter pairing code → select Luna → verify content tiles appear
  Test 2 – Content Sync:
    - Web dashboard: add new album for Luna
    - Kids app: wait for sync (trigger manually) → verify new album tile appears
  Test 3 – Approval Flow:
    - Kids app: navigate to Discover → search "Frozen" → tap Request
    - Check email: approval email received → click approve link → request approved
    - Kids app: verify celebration animation → new content playable
  Test 4 – Offline Resilience:
    - Kids app: verify content from cache → airplane mode → browse and navigate → all works
- Test execution instructions in e2e-tests/README.md

REFERENCE: PROJECT_PLAN.md §8.2 (E2E testing with Maestro).

CONSTRAINTS:
- Maestro tests cover kids app flows; web dashboard flows are browser-based (document manual steps)
- Tests may require manual Spotify login (not automatable)
- Document any manual steps required before test execution

VERIFICATION:
- All 4 Maestro tests pass on emulators or real devices
- e2e-tests/README.md has clear execution instructions
- Run the backend locally: cd backend && ./gradlew bootRun --args='--spring.profiles.active=local' → app starts, curl http://localhost:8080/actuator/health → {"status":"UP"}
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
  - How to add content for children (web dashboard walkthrough)
  - How the approval workflow works (web dashboard + email approve link)
  - Offline playback tips
  - Troubleshooting: common issues and fixes
    - Spotify not connecting: check login, check premium, check SDK version
    - Sync not working: check network, check backend logs, force sync
    - Email notifications not arriving: check SMTP config, check spam folder
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
