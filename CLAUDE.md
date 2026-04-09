# CLAUDE.md – KidsTune Project

## Project Overview

KidsTune is a kids app + backend + web dashboard system providing children a safe, controlled Spotify listening experience. Parents manage content whitelists per-child-profile via a responsive web dashboard; kids see only approved audio content via a simplified Android UI running inside Samsung Kids on repurposed Samsung smartphones.

## Architecture

```
kids-app (Kotlin/Compose) ──REST+WS──→ backend (Spring Boot 4 / Java 21)
                                            │          │
                                        MariaDB    Web Dashboard (Thymeleaf/HTMX)
                                     (existing)   ← accessible from any browser
```

- **Monorepo** with three Gradle modules: `backend/`, `kids-app/`, `shared/`
- `shared/` is a Kotlin module with DTOs, enums, and API route constants used by the kids-app
- Backend connects to an existing MariaDB instance (not containerized with the app)
- Kids App stores a **pre-resolved content tree** locally in Room for full offline support
- Web Dashboard is served by the backend at `/web/**` using Thymeleaf + HTMX + Bootstrap 5
- **Package root:** `at.kidstune` (backend) and `at.kidstune.kids` (kids-app)

## Tech Stack

### Backend (`backend/`)
- **Spring Boot 4.0.4** / Java 21 / Spring Framework 7
- **Spring WebFlux** for reactive REST + WebSocket + SSE
- **Spring Security 7** with JWT device tokens
- **MariaDB** via Spring Data JPA (existing instance, not managed by Docker Compose)
- **Liquibase** for DB migrations (YAML changelog format)
- **Caffeine** for in-memory Spotify metadata caching
- **Web Push / VAPID** for browser push notifications (no FCM required)
- **Spring Mail** for email notifications with one-click approve links
- **Gradle Kotlin DSL** for build

### Android App (`kids-app/`)
- **Kotlin 2.x** / target SDK 35
- **Jetpack Compose** with Material 3
- **MVI architecture** (Model-View-Intent) with unidirectional data flow
- **Hilt** for dependency injection
- **Room** for local database (offline content cache in kids-app)
- **Ktor Client** for HTTP (backend REST API)
- **Coil 3** for image loading + disk caching
- **Compose Navigation** (type-safe)
- **Spotify App Remote SDK** (kids-app only, for playback control)

### Shared Module (`shared/`)
- Pure Kotlin module (no Android dependencies)
- DTOs, enums (`ContentType`, `ContentScope`, `RequestStatus`), API route constants
- Referenced by both Android apps via Gradle `includeBuild`

## Key Concepts

### Per-Profile Content
AllowedContent is scoped to a `profile_id`, NOT `family_id`. Each child has their own whitelist. All endpoints for content management are nested under `/api/v1/profiles/{profileId}/content`.

### Content Scopes
When content is added, it has a `scope` that determines what is allowed:
- `TRACK` – single track
- `ALBUM` – all tracks in album
- `PLAYLIST` – all tracks in playlist (re-resolved periodically)
- `ARTIST` – ALL content by artist (most powerful, includes future releases)

### Pre-Resolved Content Tree
The backend resolves content into concrete albums and tracks immediately on add (via Spotify Web API), storing results in `ResolvedAlbum` + `ResolvedTrack` tables. The sync endpoint delivers the complete tree to kids' devices. The Kids App stores this in Room (`LocalContentEntry` → `LocalAlbum` → `LocalTrack`). **The Kids App never calls Spotify API at runtime** – everything comes from the local Room DB.

### Offline-First Kids App
The Kids App must work fully offline after initial sync. Every tap (browse → album → track → play) reads only from the local Room DB. The Spotify App Remote SDK plays tracks using URIs from Room, and Spotify streams from its own offline cache. Favorites and content requests queue locally and upload when connectivity returns.

### Content Requests & Notifications
Content requests have a lifecycle: `PENDING → APPROVED | REJECTED | EXPIRED`. Max 3 pending requests per profile (enforced by backend with 429). Requests expire after 7 days automatically via backend cron job.

Parent notifications use **three channels**:
- **Channel 1:** Email via Spring Mail — sent immediately on request creation; contains a one-click `/web/approve/{token}` link (public, no login needed)
- **Channel 2:** Web Push via VAPID — pushed to parent's browser/phone if they have subscribed (no FCM, works on any browser with Push API support)
- **Channel 3:** SSE badge update — real-time `pendingCount` push to any open dashboard tab via `/web/sse/requests`

A daily digest cron at 19:00 sends summary email for all requests pending > 4 hours with `digest_sent_at IS NULL` (catch-all). `approve_token` is a UUID on `ContentRequest`, single-use, expires with the request after 7 days.

### One-Time Profile Binding
Each kids' device is permanently bound to one child profile at first launch. No profile switching. Profile can only be reassigned via the Web Dashboard (Devices → Reassign Profile).

## Project Structure

```
kidstune/
├── backend/                        # Spring Boot 4 backend
│   ├── src/main/java/at/kidstune/
│   │   ├── config/                 # SecurityConfig, CacheConfig, WebFlux config
│   │   ├── auth/                   # Spotify OAuth, JWT device tokens, pairing
│   │   ├── common/                 # Shared utilities, base classes
│   │   ├── content/                # AllowedContent CRUD, scope resolution, content type heuristic
│   │   ├── device/                 # PairedDevice CRUD, device token management
│   │   ├── family/                 # Family registration, settings
│   │   ├── favorites/              # Favorites CRUD
│   │   ├── health/                 # Custom Actuator health indicators
│   │   ├── notification/           # Email notifications, daily digest cron
│   │   ├── profile/                # ChildProfile CRUD
│   │   ├── push/                   # Web Push / VAPID subscription management
│   │   ├── requests/               # Content request workflow
│   │   ├── resolver/               # Background job: resolves content → ResolvedAlbum/Track
│   │   ├── spotify/                # Spotify Web API client wrapper
│   │   ├── sse/                    # SSE endpoints for dashboard badge updates
│   │   ├── sync/                   # Sync endpoints (full + delta with pre-resolved trees)
│   │   └── web/                    # Thymeleaf web dashboard controllers + admin/
│   ├── src/main/resources/
│   │   ├── db/changelog/           # Liquibase YAML changelogs
│   │   ├── known-artists.yml       # Configurable children's artist list with age ranges
│   │   └── application.properties
│   └── src/test/
│       ├── java/at/kidstune/       # Tests mirror main structure
│       └── resources/spotify-fixtures/  # Recorded Spotify API JSON responses
│
├── kids-app/                       # Kids-facing Android app
│   └── app/src/main/java/at/kidstune/kids/
│       ├── connectivity/           # Network state observer
│       ├── di/                     # Hilt modules
│       ├── data/local/             # Room: LocalContentEntry, LocalAlbum, LocalTrack, LocalFavorite
│       ├── data/local/entities/    # Room entity classes
│       ├── data/preferences/       # DataStore / SharedPreferences wrappers
│       ├── data/remote/            # Ktor API client + WebSocket client + DTOs
│       ├── data/repository/        # Repositories combining local + remote
│       ├── domain/model/           # ContentTile, NowPlaying, etc.
│       ├── domain/usecase/         # GetContentTiles, ToggleFavorite, RequestContent
│       ├── navigation/             # Compose Navigation graph + type-safe routes
│       ├── playback/               # Spotify App Remote SDK wrapper, MediaSession
│       ├── sync/                   # WorkManager sync + offline queue
│       └── ui/
│           ├── components/         # Reusable Compose components
│           ├── screens/            # Screen composables (Home, Browse, Player, Discover, etc.)
│           ├── theme/              # Material 3 theme, colors, typography
│           └── viewmodel/          # ViewModels for each screen
│
├── shared/                         # Shared Kotlin module (DTOs, constants)
│   └── src/main/kotlin/at/kidstune/shared/
│       ├── model/                  # DTOs: ContentDto, ProfileDto, SyncPayloadDto, WebSocketMessage
│       └── constants/              # ApiRoutes, ContentType, ContentScope, RequestStatus
│
├── docker-compose.yml
├── Jenkinsfile
├── README.md
├── LICENSE
├── settings.gradle.kts
├── build.gradle.kts
├── CLAUDE.md                       # This file
└── PROJECT_PLAN.md                 # Detailed project plan
```

## Coding Conventions

### Backend (Java / Spring Boot)

- **Root package:** `at.kidstune` (not `com.kidstune`)
- **Package structure:** Feature-based packages (`content/`, `profile/`, `auth/`), not layer-based
- **Naming:**
  - Entities: `AllowedContent`, `ChildProfile` (no `Entity` suffix)
  - Repositories: `ContentRepository extends JpaRepository<>`
  - Services: `ContentService` (business logic, injected into controllers)
  - Controllers: `ContentController` (thin, delegates to service)
  - DTOs: in `shared/` module as Kotlin data classes, used via Jackson
- **REST conventions:**
  - All endpoints under `/api/v1/`
  - Return `ResponseEntity<>` with appropriate HTTP status codes
  - Validation via `@Valid` + Jakarta Bean Validation annotations
  - Error responses as `{ "error": "message", "code": "ERROR_CODE" }`
- **Database:**
  - Liquibase changelogs in YAML format in `src/main/resources/db/changelog/`
  - Master file: `db.changelog-master.yaml` includes individual changelogs
  - Naming: `NNN-description.yaml` (e.g., `001-initial-schema.yaml`)
  - All tables use `utf8mb4` charset
  - Timestamps stored as `DATETIME(6)` (microsecond precision) in UTC
- **Testing:**
  - JUnit 5 for all tests
  - Testcontainers with MariaDB for integration tests
  - MockWebServer for Spotify API mocking
  - Test fixtures in `src/test/resources/spotify-fixtures/`
  - Test class naming: `*Test` for unit, `*IntTest` for integration
  - Integration tests annotated with `@SpringBootTest` + `@Testcontainers`

### Android Apps (Kotlin / Compose)

- **Architecture:** MVI with `ViewModel` + `StateFlow`
  ```kotlin
  // Every screen has:
  data class SearchState(val query: String, val results: List<Result>, val loading: Boolean, val error: String?)
  sealed interface SearchIntent { data class UpdateQuery(val q: String) : SearchIntent; data object Search : SearchIntent }
  class SearchViewModel : ViewModel() {
      private val _state = MutableStateFlow(SearchState())
      val state: StateFlow<SearchState> = _state.asStateFlow()
      fun onIntent(intent: SearchIntent) { ... }
  }
  ```
- **Compose:**
  - Screens are `@Composable` functions, not classes
  - Screen functions receive `state: StateType` and `onIntent: (IntentType) -> Unit`
  - Preview functions for every screen with mock data: `@Preview @Composable fun SearchScreenPreview()`
  - Use `Modifier` as first optional parameter for all composables
- **Naming:**
  - Screens: `HomeScreen.kt`, `BrowseScreen.kt`
  - ViewModels: `HomeViewModel.kt`
  - Room entities: `LocalContentEntry`, `LocalAlbum`, `LocalTrack` (prefix `Local` to distinguish from backend/shared DTOs)
  - DAOs: `ContentDao`, `FavoriteDao`
  - Repositories: `ContentRepository`, `FavoriteRepository`
  - Use cases: `GetContentTilesUseCase`, `ToggleFavoriteUseCase`
- **Room:**
  - Single database class: `KidstuneDatabase`
  - Entities in `data/local/entities/`
  - DAOs in `data/local/`
  - Migrations handled via Room auto-migrations where possible
- **Testing:**
  - ViewModels tested with JUnit 5 + Turbine (for Flow testing) + MockK
  - Room DAOs tested with Robolectric + in-memory database
  - Compose UI tested with `createComposeRule()`
  - Test naming: `fun \`should do X when Y\`()` (backtick-style descriptive names)
- **Kids App specifics:**
  - Minimum touch target: **72dp** (not the standard 48dp)
  - No text-only navigation – every button needs an icon or image
  - Color scheme: blue/purple = music, green/teal = audiobooks, pink/red = favorites
  - All network operations go through repositories that read from Room first, sync in background
  - **Never show a loading spinner that blocks interaction** – show cached data with shimmer overlay

### Shared Module (Kotlin)

- Pure Kotlin (no Android or Spring dependencies)
- All DTOs are `@Serializable` data classes (kotlinx.serialization)
- Enums: `ContentType`, `ContentScope`, `RequestStatus`, `AgeGroup`
- API routes as string constants: `object ApiRoutes { const val PROFILES = "/api/v1/profiles" ... }`
- No business logic – only data classes and constants

## Common Pitfalls (for Claude Code)

1. **Spring Boot 4 uses Spring Framework 7 + Jakarta EE 11.** All `javax.*` imports must be `jakarta.*`. Spring Security 7 has breaking API changes from 6.x – check Spring Boot 4 migration guide.

2. **MariaDB is not PostgreSQL.** No `JSONB` type – use `JSON` (which is `LONGTEXT` with validation). No `ON CONFLICT` – use `INSERT ... ON DUPLICATE KEY UPDATE`. ENUM types work differently.

3. **Spotify App Remote SDK is Android-only** and not available on Maven Central. It must be included as a local AAR file in `kids-app/libs/`. Follow Spotify's Android SDK documentation for setup.

4. **Room entities are NOT the same as backend entities or shared DTOs.** The Kids App has its own local schema (`LocalContentEntry`, `LocalAlbum`, `LocalTrack`) optimized for offline browsing. Map between DTO and Room entity in the repository layer.

5. **Liquibase, not Flyway.** Changelogs are YAML format. Do not use Flyway SQL migration files.

6. **The kids-app NEVER calls Spotify Web API.** All Spotify data comes pre-resolved from the backend via the sync endpoint and is stored in Room. The only Spotify interaction on the kids' device is playback via the App Remote SDK using track URIs from Room.

7. **Content is per-profile, not per-family.** AllowedContent has `profile_id` FK. When adding content for "all children", create one AllowedContent row per profile.

8. **Dashboard auth is email/password — completely independent of Spotify.** The web dashboard login (`/web/login`) uses `Family.email` + `Family.password_hash` (BCrypt). Spotify tokens are stored separately and used only for Spotify API calls. Two Spotify token levels exist: `Family.spotify_refresh_token` (parent's account, nullable — connected via Settings, used for content search/resolution) and `ChildProfile.spotify_refresh_token` (each child's own account, nullable — connected per-profile, used for import). `SpotifyTokenService` has parallel methods: `getValidAccessToken(familyId)` for parent context, `getValidProfileAccessToken(profileId)` for child import context. The Spotify App Remote SDK on a child's device controls the Spotify app already logged in with the child's own account — no backend token exchange for playback. Never use the family Spotify token for per-child import operations. Never gate dashboard login on Spotify connectivity.

9. **Email + Web Push + SSE — not FCM or Android push.** When a content request is created: (1) Spring Mail sends email to all `family.notification_emails` with a one-click `/web/approve/{token}` link (public, no login); (2) VAPID Web Push sends a notification to subscribed browsers/phones; (3) SSE on `/web/sse/requests` updates the pending badge if the dashboard tab is open. No Android parent app, no Firebase. Daily digest cron at 19:00 sends summary of all open requests.

10. **Jetpack Compose uses Material 3.** Do not import Material 2 (`androidx.compose.material`). Use `androidx.compose.material3` throughout.

11. **Coil 3, not Coil 2.** Import path is `coil3.*`, not `coil.*`. Compose integration is `coil3.compose.AsyncImage`.

12. **Package root is `at.kidstune`, not `com.kidstune`.** All backend Java classes are in `at.kidstune.*`; all kids-app Kotlin classes are in `at.kidstune.kids.*`; shared module is `at.kidstune.shared.*`. Do not create classes under `com.kidstune`.

13. **VAPID keys must be persisted across restarts.** If `KIDSTUNE_VAPID_PUBLIC_KEY` / `KIDSTUNE_VAPID_PRIVATE_KEY` env vars are absent, the backend generates a new key pair on every startup. Browser push subscriptions become invalid after every restart. Generate keys once and set them as env vars (the first startup logs the generated keys for easy copy-paste).

## Build & Run

### Backend
```bash
cd backend
./gradlew bootRun                    # Dev mode (expects MariaDB on localhost:3306)
./gradlew test                       # Run all tests (Testcontainers spins up MariaDB)
./gradlew bootBuildImage             # Build Docker image
docker compose up -d                 # Run in Docker
```

### Android Apps
```bash
cd kids-app
./gradlew assembleDebug              # Build debug APK
./gradlew test                       # Run unit + Robolectric tests
./gradlew connectedAndroidTest       # Run instrumented tests on device

# Web dashboard is part of the backend build – no separate build step needed
```

### Full Project
```bash
# From root:
./gradlew test                       # Runs all tests across all modules
```

## Task Execution Guidelines

When implementing a phase or feature:

1. **Start with the shared module** if new DTOs or constants are needed
2. **Then implement backend** – entities, repository, service, controller, tests
3. **Then implement Android** – Room entities (if needed), repository, use case, ViewModel, UI, tests
4. **Always write tests alongside implementation**, not after
5. **Run the existing test suite** before and after making changes to ensure nothing breaks
6. **Each commit should compile and pass all tests** – no broken intermediate states
7. **When creating a new Liquibase changelog**, increment the number prefix and add it to the master changelog

## Environment Variables

```
# Backend (docker-compose.yml or .env)
SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:3306/kidstune
SPRING_DATASOURCE_USERNAME=kidstune
SPRING_DATASOURCE_PASSWORD=<secret>
SPOTIFY_CLIENT_ID=<from developer.spotify.com>
SPOTIFY_CLIENT_SECRET=<from developer.spotify.com>
KIDSTUNE_JWT_SECRET=<random 256-bit key, e.g. openssl rand -hex 32>
KIDSTUNE_BASE_URL=https://kidstune.altenburger.io

# VAPID keys for Web Push — generate once and persist.
# If omitted, ephemeral keys are generated on startup and logged.
KIDSTUNE_VAPID_PUBLIC_KEY=<base64url EC public key>
KIDSTUNE_VAPID_PRIVATE_KEY=<base64url EC private key>

# SMTP for email notifications
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<sender address>
SPRING_MAIL_PASSWORD=<app password>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```
