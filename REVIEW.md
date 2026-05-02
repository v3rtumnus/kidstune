# KidsTune – Code Review

## Part 1: Bugs & Issues

### Bugs (clearly broken behaviour)

---

#### B-1 · `SyncController.java:43` — unhandled `DateTimeParseException`

**File:** `backend/src/main/java/at/kidstune/sync/SyncController.java`

```java
Instant sinceInstant = Instant.parse(since);  // no try/catch
```

If the kids app (or any caller) sends a malformed `since` query parameter, `Instant.parse` throws `DateTimeParseException`. The global exception handler has no mapping for this exception and returns **500 Internal Server Error** instead of **400 Bad Request**. This can be triggered accidentally on the first delta-sync if the app sends a default or empty value.

**Fix:** Wrap in try/catch and throw a `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`, or add a `@ExceptionHandler(DateTimeParseException.class)` to the global handler.

---

#### B-2 · `DiscoverScreen.kt` — rejected requests have no dismiss button

**File:** `kids-app/app/src/main/java/at/kidstune/kids/ui/screens/DiscoverScreen.kt`

`PendingRequestCard` receives an `onDismiss` callback but never wires it to any UI element — no button, no swipe-to-dismiss gesture. A rejected request renders the ❌ icon and parent note permanently until the ViewModel is reloaded. A child is stuck staring at it with no way to clear it.

**Fix:** Add a dismiss icon button to `PendingRequestCard` that calls `onDismiss(request.id)`, or auto-dismiss rejected cards after a short delay.

---

### Issues (suboptimal, not broken)

---

#### I-1 · `ContentRequestService.java:270` — N+1 queries in `getPendingCount`

**File:** `backend/src/main/java/at/kidstune/requests/ContentRequestService.java`

```java
List<ChildProfile> profiles = profileRepository.findByFamilyId(familyId);
profiles.stream().map(p -> ...
    requestRepository.countByProfileIdAndStatus(p.getId(), PENDING)  // N queries
)
```

For a family with N children this fires N+1 DB queries. `getPendingCount` is called on every SSE badge update and on every dashboard page load, so this scales linearly with family size.

**Fix:** Add a `countByProfileIdInAndStatus(List<String> profileIds, ContentRequestStatus status)` to `ContentRequestRepository` and replace the per-profile loop with a single `GROUP BY profile_id` query, or use the existing `countByProfileIdInAndStatus` already present in `ContentRequestRepository`.

---

#### I-2 · `pending-requests.html:41,132` — raw ISO-8601 timestamps

**File:** `backend/src/main/resources/templates/web/fragments/pending-requests.html`

```html
<small class="text-muted" th:text="${item.request().requestedAt}"></small>
```

Renders as `2026-05-02T14:30:00Z` — a machine timestamp shown to German-speaking parents. The `resolvedAt` field in the history fragment has the same issue.

**Fix:** Use Thymeleaf's `#temporals` utility with a German locale pattern:
```html
th:text="${#temporals.format(item.request().requestedAt, 'dd.MM.yyyy, HH:mm')}"
```

---

#### I-3 · `ContentResolver.java:109` — `resolveAllAsync` not guarded against concurrent calls

**File:** `backend/src/main/java/at/kidstune/resolver/ContentResolver.java`

`resolveAllRunning` (volatile boolean) and `progressTotal`/`progressCompleted` (AtomicIntegers) are set at the top of `resolveAllAsync` without a compare-and-set guard. Two admin-triggered "Resolve All" operations fired in quick succession would both start running, clobbering each other's progress counters and causing the progress bar to show incorrect data.

**Fix:** Use `AtomicBoolean.compareAndSet(false, true)` to gate entry, returning immediately (or throwing) if a resolve-all is already in progress.

---

#### I-4 · Silent note truncation in the reject flow

**Files:** `backend/src/main/java/at/kidstune/requests/ContentRequestService.java` (lines ~162, ~199)

`rejectRequest` and `approveRequestWithOptions` silently truncate the parent note to 500 characters. There is no character counter in the rejection form and no validation error returned to the caller. A parent writing a detailed note does not know it was cut.

**Fix:** Either validate the note length at the controller level and return `400 Bad Request` if exceeded, or add a live character counter (and `maxlength` attribute) to the rejection form.

---

#### I-5 · `DiscoverScreen.kt:661` — PIN-pad timeout doesn't reset on digit input

**File:** `kids-app/app/src/main/java/at/kidstune/kids/ui/screens/DiscoverScreen.kt`

```kotlin
LaunchedEffect(Unit) {
    delay(30_000)
    onTimeout()
}
```

The 30-second timer starts once when the PIN-pad overlay enters the composition and never resets. A parent who is handed the phone and takes more than 30 seconds to enter their PIN (distracted, slow typist) is silently timed out mid-entry with no countdown indicator.

**Fix:** Use `LaunchedEffect(digits)` so the timer resets on each keypress, or show a visible countdown that resets on input.

---

### Design / UX Gaps

---

#### G-1 · No "Hörbücher" category on `HomeScreen`

**File:** `kids-app/app/src/main/java/at/kidstune/kids/ui/screens/HomeScreen.kt`

`ChapterListScreen` exists and the spec calls for a distinct green/teal colour scheme for audiobooks, but there is no navigation path from `HomeScreen` to an audiobooks browse view. All approved content — music and audiobooks alike — funnels into the single "Musik" browse view. Either the feature is incomplete (the nav entry point is missing) or `ChapterListScreen` is dead code that should be removed.

---

#### G-2 · Requests history tabs share a single content div

**File:** `backend/src/main/resources/templates/web/requests/index.html`

The "Genehmigt", "Abgelehnt", and "Abgelaufen" tab buttons all use `hx-target="#tab-history"`. After an HTMX swap Bootstrap's tab-highlight and the actual div content can get out of sync: clicking "Genehmigt" then immediately "Abgelehnt" replaces the content but the first tab's `active` class is not necessarily removed because HTMX updates the content div, not the tab nav items.

**Fix:** Give each history tab its own target div, or use `hx-on::after-request` to manually update the active tab class after each HTMX response.

---

---

## Part 2: Potential Improvements & Further Features

### Dashboard UX

---

#### P-1 · Devices page shows "last seen" but not "last sync"

**File:** `backend/src/main/resources/templates/web/devices/index.html:59`

The devices table shows `lastSeenAt` (updated by `LastSeenFilter` on every authenticated request), but there is no dedicated "last synced" timestamp. A device checking its profile assignment or uploading a favorite updates `lastSeenAt`, so a device that hasn't actually synced content in days may still show a recent "last seen" timestamp. Parents can't tell whether new content has actually been delivered.

**Improvement:** Add a `last_sync_at` column to `paired_device` and update it in `SyncController` on each successful full or delta sync. Surface it in the devices table alongside "last seen".

---

#### P-2 · Resolution failures are invisible to parents

**File:** `backend/src/main/java/at/kidstune/resolver/ContentResolver.java:168–173`

When Spotify API calls fail during content resolution, the error is logged but `AllowedContent.resolvedAt` is never set. The content just sits in an unresolved state forever. The parent has no idea why an album they added isn't showing up on the device.

**Improvement:** Add a `resolution_error` column (nullable `VARCHAR(500)`) to `allowed_content`. Store the error message there on failure. Surface it in the content list (`content/index.html`) as a small warning icon or badge with a tooltip.

---

#### P-3 · No compound index on `(profile_id, status)` for `content_request`

**File:** `backend/src/main/resources/db/changelog/001-initial-schema.yaml`

The schema has separate single-column indexes on `profile_id` and on `status`, but the most common query pattern filters on **both** — e.g. `WHERE profile_id IN (...) AND status = 'PENDING'`. MariaDB will pick one index and scan the rest, rather than using a tight compound index.

**Improvement:** Add a compound index `(profile_id, status)` (in that order) in a new Liquibase changelog. The existing single-column indexes can stay for other query shapes.

---

#### P-4 · Insights range view error is swallowed silently

**File:** `backend/src/main/resources/templates/web/insights/range.html:55`

```js
.catch(function(e) { console.error('Range load failed', e); });
```

If the `/range-data` fetch fails (Spotify disconnected, network blip), the chart never renders and the user sees only the placeholder skeleton indefinitely. The error goes to the browser console only.

**Improvement:** Replace the `.catch` handler with something that swaps the placeholder for a visible error message — e.g. the same `alert alert-warning` pattern already used for the "no Spotify account" case.

---

#### P-5 · No cross-profile aggregate view in Insights

**File:** `backend/src/main/resources/templates/web/insights/today.html`

The Insights dashboard shows one card per child, but there is no family-level summary: total listening time across all children today, most-played track across all profiles, or which child is currently active. For parents with 2–3 children this overview would be more useful than scrolling through individual cards.

**Improvement:** Add a collapsed "Gesamt" summary section at the top of `today.html` (can be an HTMX fragment, same auto-refresh cadence) showing total listening time and a small per-profile bar chart.

---

#### P-6 · No duplicate warning when adding content

**File:** `backend/src/main/java/at/kidstune/content/ContentService.java`

`ContentRepository.existsByProfileIdAndSpotifyUri` is checked on the import path, but the single-add flow in the dashboard returns `201 Created` on a duplicate (the service just saves a second identical row, or the unique constraint fires a 500). Parents get no helpful message when they try to add an album a child already has.

**Improvement:** In `addContent`, check for an existing entry with the same `profile_id + spotify_uri` and return `200 OK` with the existing record (idempotent), or a `409 Conflict` with a clear message. Show it as a non-blocking toast in the dashboard.

---

### Kids App UX

---

#### P-7 · No pull-to-refresh on any content screen

**Files:** `BrowseScreen.kt`, `AlbumGridScreen.kt`, `TrackListScreen.kt`, `DiscoverScreen.kt`

None of the `LazyColumn`/`LazyGrid` screens implement `PullRefreshIndicator`. After coming back online, a child has no gesture to trigger a sync — they have to navigate all the way back to home and wait for the background `WorkManager` job.

**Improvement:** Wrap the `LazyColumn` in each content screen with `PullRefreshIndicator` and wire the pull gesture to a `ForceSync` intent in the ViewModel. Can reuse the existing sync infrastructure; just needs a user-triggered entry point.

---

#### P-8 · Stale-content indicator is not actionable

**File:** `kids-app/app/src/main/java/at/kidstune/kids/ui/screens/HomeScreen.kt:197–213`

The yellow dot shown when `isStaleContent == true` is purely decorative. There is no tap handler on it. A child (or a parent supervising) cannot trigger a sync by tapping the indicator; the only way to refresh is to wait for the next `WorkManager` schedule.

**Improvement:** Make the stale-content dot (and the offline cloud icon) tappable, sending a `TriggerSync` intent to `HomeViewModel`. Show a brief "Wird aktualisiert…" snackbar as feedback.

---

#### P-9 · PIN-pad timeout has no countdown and does not reset on input

**File:** `kids-app/app/src/main/java/at/kidstune/kids/ui/screens/DiscoverScreen.kt:661`

The 30-second inactivity timeout fires without any visible countdown. Additionally, `LaunchedEffect(Unit)` means the timer never resets when the user presses a digit key — a parent who types slowly can be ejected mid-entry.

**Improvement:** Use `LaunchedEffect(digits)` so the timer resets on every keypress. Optionally add a small circular countdown indicator in the corner of the overlay (visible only in the last 10 seconds) to warn the parent before the timeout fires.

---

#### P-10 · Rejected requests have no dismiss gesture (related to B-2)

**File:** `kids-app/app/src/main/java/at/kidstune/kids/ui/screens/DiscoverScreen.kt:481–527`

Already noted as bug B-2 (the `onDismiss` callback is wired but never triggered). As an improvement beyond the fix: consider a **swipe-to-dismiss** gesture on the `PendingRequestCard` for rejected/expired entries, consistent with common Android list patterns and more intuitive for kids than a small ✕ button.

---

#### P-11 · No "request pending" nudge after device comes online

**File:** `kids-app/app/src/main/java/at/kidstune/kids\sync\SyncWorker.kt`

When the device syncs and a previously-PENDING request has transitioned to APPROVED, the kids app correctly shows the "NEU" badge and confetti celebration on the Discover screen — but only if the child is actively looking at that screen. If the child is on the Home or Browse screen, there is no notification that new content has arrived.

**Improvement:** After a delta sync that returns newly-approved content, emit a snackbar or animated badge on the Home screen ("Neuer Inhalt freigeschaltet! 🎉") that deep-links to the Discover screen's "Meine Wünsche" section.

---

### Backend

---

#### P-12 · No per-scope re-resolution TTL — playlists re-resolve only once per day

**File:** `backend/src/main/java/at/kidstune/resolver/ContentResolver.java:182`

```java
@Scheduled(cron = "0 0 4 * * *")
public void reResolveArtistsAndPlaylists() { ... }
```

ARTIST and PLAYLIST entries are both re-resolved on a single daily schedule at 04:00. Playlists can change frequently (especially Spotify's editorial playlists); a 24-hour stale window means a playlist updated at 05:00 won't be visible on devices until the following night.

**Improvement:** Split the scheduled job into two with different cadences: playlists every 6 hours (or trigger re-resolution when a playlist is played from the Insights event stream), artists once daily. Could be two `@Scheduled` methods or a configurable cron via `application.properties`.

---

#### P-13 · No custom business metrics

**File:** `backend/src/main/java/at/kidstune/` (globally)

The backend has three custom Spring Actuator health indicators (`ContentResolverHealthIndicator`, `SseHealthIndicator`, `SpotifyHealthIndicator`), which is good. However, there are no custom **Micrometer** metrics on business operations: sync payload sizes, resolution latency, content request throughput, or Spotify API error rates. These would be valuable for diagnosing production issues.

**Improvement:** Inject `MeterRegistry` into `SyncService`, `ContentResolver`, and `SpotifyTokenService`. Add counters for sync calls (labelled by full/delta), a timer for resolution duration per scope, and a counter for Spotify API failures vs successes.

---

#### P-14 · Insights range view loads Chart.js from an external CDN

**File:** `backend/src/main/resources/templates/web/insights/range.html:45`

```html
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
```

All other JS dependencies (htmx, bootstrap) are served via WebJars (local). Chart.js is the only exception. This introduces an external network dependency and makes the dashboard non-functional offline. It also bypasses any Content-Security-Policy that would block external scripts.

**Improvement:** Add `org.webjars:chartjs` to `backend/build.gradle.kts` and serve it via the same WebJars path as the other dependencies.

---

### New Features Worth Considering

---

#### F-1 · QR code for device pairing

**Current state:** Device pairing requires typing a 6-digit code shown on the dashboard into the kids' device.

**Proposal:** Generate a QR code alongside the 6-digit code on the pairing page (`devices/pairing-code.html`). The kids app scans it with the camera instead of requiring the parent to type. The QR code encodes the same pairing code URI. Libraries: `com.google.zxing` on the backend to generate the QR as a PNG data URL; the Android `CameraX` + `ML Kit Barcode Scanning` API for scanning. This would make first-time setup significantly smoother.

---

#### F-2 · Listening time limits / daily schedule

**Current state:** Once content is approved, a child can listen indefinitely at any time.

**Proposal:** Add an optional `listening_schedule` JSON column to `child_profile` (e.g. `{"maxMinutesPerDay": 90, "allowedHours": "07:00-21:00"}`). The backend emits a `PLAYBACK_RESTRICTED` WebSocket message when the limit is reached or outside allowed hours. The kids app disables playback and shows a friendly "Genug für heute! 🌙" screen. This is a frequent parental concern and would significantly increase the app's usefulness.

---

#### F-3 · Content expiry / time-limited approvals

**Current state:** Once content is approved it stays forever unless a parent manually removes it.

**Proposal:** Add an optional `expires_at` field to `AllowedContent`. The backend already has the re-resolution cron infrastructure; adding expiry checks alongside it would be straightforward. Parents could approve a "trending" album for 30 days without having to remember to remove it. The dashboard's approve-with-options flow (`/web/requests/{id}/approve-options`) is a natural place to expose this.

---

#### F-4 · "Suggest this to siblings" from a Discover result

**Current state:** When a request is approved for one child, siblings don't see it.

**Proposal:** After approving a request, the dashboard's "approve" response fragment could include a "Auch für Geschwister?" nudge with one-click buttons per sibling profile. The backend already has `approveForAllProfiles`; this is purely a dashboard UX addition that surfaces the existing endpoint more prominently.

---

#### F-5 · Offline-capable push notifications for parents

**Current state:** VAPID Web Push notifications are sent to subscribed browsers. These work when the browser is running, but not as true mobile background notifications on iOS (which requires a PWA install).

**Proposal:** Add a Web App Manifest and service worker to the dashboard so parents can install it as a PWA on their home screen. The existing VAPID infrastructure would then deliver background push notifications to iOS and Android without Firebase. The service worker can also cache the dashboard shell for offline access.

