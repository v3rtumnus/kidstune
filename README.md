# KidsTune

A self-hosted system giving children a safe, parent-controlled Spotify listening experience. Parents manage content whitelists per child via a web dashboard; children see only approved music and audiobooks on a dedicated Android app running inside Samsung Kids.

---

## Architecture

```
┌──────────────────────┐     ┌────────────────────────┐
│  KidsTune Kids       │     │  KidsTune Web Dashboard │
│  (Android App)       │     │  (Any Browser)          │
│                      │     │                         │
│  - Browse content    │     │  - Content curation     │
│  - Audio playback    │     │  - Approval queue       │
│  - Favorites         │     │  - Profile management   │
│  - Content requests  │     │  - Device management    │
│                      │     │  - Email notifications  │
│  Spotify App Remote  │     │                         │
│  SDK (playback)      │     │  Served by the backend  │
└──────────┬───────────┘     └───────────┬─────────────┘
           │                             │
           │    REST + WebSocket         │ (no separate server)
           └──────────────┬──────────────┘
                          │
                ┌─────────▼──────────┐
                │  KidsTune Backend  │
                │  (Docker)          │
                │                    │
                │  Spring Boot 4     │
                │  MariaDB           │
                │  Thymeleaf + HTMX  │
                │  Spring Mail       │
                │  WebSocket + SSE   │
                └────────────────────┘
```

**Key design decisions:**

- The kids' phone has Spotify installed (logged in with the child's own account) but the child never opens it — Samsung Kids only shows KidsTune Kids. Spotify runs as a background service exclusively.
- The backend pre-resolves all allowed content into albums and tracks. The Kids App stores this in a local Room database and works fully offline after the first sync.
- Content is per child profile (not per family). Each child has their own whitelist.
- Dashboard auth (email + password) is fully decoupled from Spotify OAuth.

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| JDK | 21 | Temurin recommended |
| Android SDK | 35 | Android Studio or standalone |
| MariaDB | 10.6+ | Existing instance — not containerized with the app |
| Docker + Docker Compose | Latest | For running the backend |
| Spotify Developer App | — | Free registration at developer.spotify.com |
| Spotify Premium account | — | Required per person for App Remote SDK playback control |

Each person who will use KidsTune (parents + each child) needs their own Spotify Premium account.

---

## Quick Start

### 1. Clone the repository

```bash
git clone git@github.com:v3rtumnus/kidstune.git
cd kidstune
```

### 2. Create the database

Connect to your existing MariaDB instance and run:

```sql
CREATE DATABASE kidstune CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'kidstune'@'%' IDENTIFIED BY 'your-secure-password';
GRANT ALL PRIVILEGES ON kidstune.* TO 'kidstune'@'%';
FLUSH PRIVILEGES;
```

### 3. Register a Spotify Developer App

See [Spotify Developer App Setup](#spotify-developer-app-setup) below for full step-by-step instructions.

### 4. Configure environment variables

Create a `.env` file at the repository root (next to `docker-compose.yml`):

```env
# Database
DB_PASSWORD=your-secure-password

# Spotify Developer App credentials (from developer.spotify.com)
SPOTIFY_CLIENT_ID=your_client_id_here
SPOTIFY_CLIENT_SECRET=your_client_secret_here

# JWT secret for device tokens — generate a random 256-bit key:
#   openssl rand -hex 32
JWT_SECRET=your_64_char_hex_string_here

# Public base URL of the backend (used in email links and OAuth callbacks)
KIDSTUNE_BASE_URL=https://kidstune.example.com

# SMTP for email notifications
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your@gmail.com
SPRING_MAIL_PASSWORD=your_app_password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

> **Note:** For local testing you can set `KIDSTUNE_BASE_URL=http://localhost:8090` and skip the mail config — email delivery will fail silently but everything else works.

### 5. Start the backend

```bash
docker compose up -d
```

Verify it started:

```bash
curl http://localhost:8090/actuator/health
# → {"status":"UP"}
```

The web dashboard is now available at `http://localhost:8090/web` (or your configured base URL).

### 6. Build the Kids App APK

```bash
cd kids-app
./gradlew assembleRelease
# APK: kids-app/app/build/outputs/apk/release/app-release.apk
```

### 7. Complete setup via web dashboard

Open `http://localhost:8090/web` in a browser and follow the on-screen steps:

1. Register a family account (email + password)
2. Connect your Spotify account (Settings → Connect Spotify Account)
3. Create child profiles
4. Import or add content for each child
5. Pair the kids' device (Devices → Add Device → enter pairing code on the phone)

---

## Spotify Developer App Setup

### 1. Create the app

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard)
2. Log in with your Spotify account
3. Click **Create app**
4. Fill in the form:
   - **App name:** KidsTune (or any name)
   - **App description:** Family music controller
   - **Website:** your backend URL (or leave blank)
   - **Redirect URIs:** Add both of these:
     - `https://your-domain.com/api/v1/auth/spotify/callback`
     - `https://your-domain.com/web/settings/spotify-callback`
     - `https://your-domain.com/web/profiles/spotify-callback`
   - **APIs used:** Check **Web API** and **Android**
5. Accept the Terms of Service and click **Save**

> For local testing, also add `http://localhost:8090/...` variants of the redirect URIs.

### 2. Find your credentials

On the app dashboard, click **Settings** to find:
- **Client ID** — copy this to `SPOTIFY_CLIENT_ID` in your `.env`
- **Client Secret** — click **View client secret**, copy to `SPOTIFY_CLIENT_SECRET`

### 3. Add users (Development Mode)

Spotify Developer apps in Development Mode allow up to 25 users. Every Spotify account that connects to KidsTune (parents + each child) must be added to this list:

1. In your app dashboard click **Settings → User Management**
2. Click **Add new user**
3. Enter the Spotify username and email for each person
4. Click **Add**

> If you skip this step, OAuth will return a 403 error when any account tries to connect.

### 4. Required OAuth scopes

KidsTune requests these scopes automatically during the OAuth flow — no manual configuration needed. For reference:

**Parent account:** `user-read-playback-state user-modify-playback-state user-library-read user-read-recently-played playlist-read-private streaming`

**Child accounts (import):** `user-library-read user-library-modify user-read-recently-played user-top-read playlist-read-private`

---

## Samsung Kids Device Setup

This section explains how to set up a repurposed Samsung smartphone as a dedicated kids' music device.

### Prerequisites

- Samsung smartphone with One UI 4.0+ (Samsung Kids built-in)
- Spotify installed from the Play Store, logged in with the **child's own** Spotify Premium account
- KidsTune Kids APK installed (see below)
- Backend running and accessible from the device's network

### Step 1 – Install Spotify and log in

Do this **outside Samsung Kids** (in the normal Android launcher):

1. Install Spotify from the Google Play Store
2. Log in with the **child's own** Spotify account (not the parent's)
3. Open Spotify and download the playlists/albums the child will listen to for offline use

> This step must happen outside Samsung Kids. After Samsung Kids is configured, the child cannot access Spotify directly.

### Step 2 – Sideload the KidsTune Kids APK via ADB

Enable USB debugging on the device: **Settings → Developer Options → USB Debugging**

Connect the device via USB and run:

```bash
# Install the APK
adb install -r kids-app/app/build/outputs/apk/release/app-release.apk

# Verify installation
adb shell pm list packages | grep kidstune
```

Alternatively, copy the APK to the device via USB or a file manager and tap it to install (requires "Install from unknown sources" to be enabled in Settings → Security).

### Step 3 – Pair the device

1. Open the KidsTune web dashboard → **Devices → Add Device**
2. Click **Generate Pairing Code** — a 6-character code is shown (valid for 5 minutes)
3. Open KidsTune Kids on the Samsung device
4. Enter the pairing code when prompted
5. Back in the web dashboard → **Devices**, assign a child profile to the newly paired device

After pairing, KidsTune Kids downloads the content tree and shows the home screen.

### Step 4 – Configure Samsung Kids

1. Open **Settings → Digital Wellbeing → Samsung Kids** (exact path varies by One UI version)
2. Set a Samsung Kids PIN (separate from the device unlock PIN — keep this safe)
3. Tap **Add apps** → find **KidsTune Kids** → add it
4. **Do NOT add Spotify** to the allowed apps list — Spotify must only run as a background service
5. Configure daily play time limits if desired
6. Optionally enable **Bedtime** for automatic lock-out

> **Why not add Spotify?** If Spotify appears in Samsung Kids, the child can open it directly and access unrestricted content, bypassing all KidsTune controls.

### Step 5 – Verify end-to-end

Launch Samsung Kids and run through this checklist:

| # | Check | Expected result |
|---|-------|-----------------|
| 1 | Open Samsung Kids | KidsTune Kids tile is visible |
| 2 | Tap KidsTune Kids | App opens to Home screen |
| 3 | Tap an album → tap a track | Music plays |
| 4 | Press Home inside Samsung Kids | Mini-player bar persists |
| 5 | Re-open KidsTune Kids | Playback state restored |
| 6 | Reboot device → open Samsung Kids → KidsTune Kids | App relaunches, saved position available |
| 7 | Try to navigate to Spotify from Samsung Kids | Not possible |

---

## Adding Content for Children (Web Dashboard Walkthrough)

### Option A – Import from child's listening history

1. Go to **Import** in the sidebar
2. Select a child profile
3. Click **Import from [Child]'s Spotify history** — requires the child's Spotify account to be connected (Settings → Profiles → Link Spotify for that profile)
4. The dashboard shows detected children's content from the child's listening history, pre-sorted by play count
5. Check the items you want to add and click **Import selected**

This is the fastest way to populate content for a child who already uses Spotify.

### Option B – Search and add manually

1. Go to **Profiles** → select a child → click **Manage Content**
2. Click **Add Content**
3. Use the search box to find an artist, album, playlist, or track
4. Click the **+** button next to any result
5. Choose a **scope**:
   - **Artist** — all content by this artist (including future releases)
   - **Album** — all tracks in this album
   - **Playlist** — all tracks in this playlist (re-synced daily)
   - **Track** — single track only
6. Click **Add to [child's name]**

The backend immediately resolves the content into albums and tracks in the background. The kids' device picks up the new content on the next sync (within 15 minutes, or immediately on next app open).

### Adding content to multiple children

Use **Add Content → Add to all profiles** to add the same item to every child at once. This creates one `AllowedContent` row per profile, so each child independently tracks their own favorites and requests.

---

## Approval Workflow

When a child taps the **Discover** tab and requests a new piece of content:

1. **Child submits a request** in the Discover tab (max 3 pending requests per profile)
2. **Email sent instantly** to all addresses in Settings → Notification Emails
   - Email contains the content title, child's name, and a one-click **[Approve]** button
   - The approve link works without logging in — just click it
3. **Dashboard badge updates** in real time if the dashboard is open in a browser
4. **Daily digest at 19:00** sends a summary of all open requests if the original email was missed

**To approve a request:**
- Click **[Approve]** in the notification email — done, no login required
- Or open the web dashboard → **Requests** → click **Approve** next to any pending request

**What happens after approval:**
- The backend adds the content to the child's whitelist and resolves it immediately
- The Kids App receives a WebSocket push (if online) and shows a celebration animation
- The new content appears on the home screen within seconds

**Pending requests expire after 7 days** if not reviewed. The child can re-request the same content after expiry.

---

## Offline Playback

KidsTune is designed to work fully offline after the initial setup:

| Data | Stored where | Survives offline |
|------|-------------|-----------------|
| Content tree (artists, albums, tracks) | Room database on device | Yes |
| Cover art | Coil disk cache (200 MB LRU) | Yes (if previously viewed) |
| Favorites | Room database | Yes |
| Audio files | Spotify's internal cache | Yes (if downloaded in Spotify) |
| Playback position | Room database | Yes |

**Tips for reliable offline playback:**

1. **Download content in Spotify before going offline.** Open Spotify on the child's device (outside Samsung Kids), find the playlists/albums the child listens to, and download them. KidsTune streams through Spotify's own offline cache — content must be pre-downloaded.

2. **Sync before a trip.** Open KidsTune Kids while on WiFi to trigger a sync. The WorkManager sync runs every 15 minutes automatically when online.

3. **Check downloaded content periodically.** Spotify may remove cached files after long periods without listening. Refresh downloads monthly if the device is mostly offline.

---

## Troubleshooting

### Spotify not connecting / "Spotify not connected" error

- Verify Spotify is installed on the kids' device and the child is logged in. Do this outside Samsung Kids.
- Verify the child's Spotify account is Premium (App Remote SDK requires Premium).
- Verify Spotify has at least one downloaded playlist (App Remote SDK requires content to be available offline if the device has no internet).
- Make sure Spotify is **not** in the Samsung Kids allowed apps list — it should run as a background service only.
- If Spotify was recently updated, restart the kids' device.

### Sync not working / content not appearing

- Check the kids' device has internet access (Wi-Fi or mobile data).
- Force a sync: close and reopen KidsTune Kids (sync runs on every app launch).
- Check backend logs: `docker compose logs -f kidstune-backend`
- Verify the backend is reachable from the device: open a browser on the device and navigate to `https://your-domain.com/actuator/health` — should return `{"status":"UP"}`.
- If the backend is unreachable, check your router/firewall rules and Docker port mapping.

### Email notifications not arriving

- Check the spam folder first.
- Verify SMTP settings in your `.env` file are correct.
- Test SMTP connectivity: `docker compose exec kidstune-backend curl -v smtp://your-smtp-host:587`
- For Gmail: use an App Password (not your main password) — generate one at myaccount.google.com/apppasswords.
- Check backend logs for mail errors: `docker compose logs kidstune-backend | grep -i mail`

### Samsung Kids issues

**KidsTune Kids not visible in Samsung Kids:**
- Re-add the app: Settings → Digital Wellbeing → Samsung Kids → Add apps

**App resets to pairing screen after device restart:**
- This is normal only on the very first launch. If it happens repeatedly, the device token may have been cleared. Re-pair via the web dashboard → Devices → Add Device.

**Music stops and doesn't resume:**
- Spotify may have lost audio focus to another app (call, notification). KidsTune detects this and shows a paused state. Tap the play button to resume.
- If Spotify crashed, close Samsung Kids, restart Spotify outside Samsung Kids, then re-enter Samsung Kids.

**Samsung Kids won't exit (parental override):**
- Use the Samsung Kids PIN to exit. If you forgot the PIN: Settings → Digital Wellbeing → Samsung Kids → use your Samsung account to reset.

### Backend won't start

```bash
# Check container status and logs
docker compose ps
docker compose logs kidstune-backend

# Common issues:
# - MariaDB not reachable: check SPRING_DATASOURCE_URL points to the correct host/port
# - Missing env vars: verify your .env file has all required variables
# - Port conflict: check nothing else is running on port 8090
```

### Web dashboard login fails

- Verify you registered at `/web/register` first — the backend starts with no accounts.
- Dashboard auth is email + password, not Spotify — if your Spotify token expired, you can still log in.
- If you lost your password: use the Admin Danger Zone at `/web/admin/danger-zone` to wipe all data and start over, or directly update the `password_hash` in the database.

---

## CI/CD (Jenkins)

The repository includes a `Jenkinsfile` for path-based automated builds:

- Changes in `backend/` → runs tests + builds JAR + redeploys Docker container
- Changes in `kids-app/` or `shared/` → runs tests + builds release APK (archived as Jenkins artifact)

The pipeline polls the repository every 5 minutes. You can also trigger builds manually with `FORCE_BACKEND` or `FORCE_KIDS_APP` parameters.

To download the latest APK from Jenkins, go to the **Kids App** build stage → **Last Successful Artifacts**.

---

## Known Limitations

- **Spotify Premium required** for every user (parent + each child). Free accounts cannot use the App Remote SDK.
- **Spotify Developer Mode** allows up to 25 users. Sufficient for any family setup, but not for public distribution.
- **Android only.** The Kids App is Android-specific (Samsung Kids is Samsung/Android). iOS is out of scope.
- **Samsung Kids recommended** for containment. The app works on any Android device, but without Samsung Kids there is no enforced containment.
- **No video.** KidsTune only exposes music and audiobooks. Video content from Spotify is stripped from all results.
- **German audiobook heuristic.** The content type classifier is tuned for German-language children's content (Hörspiel, Folge N naming patterns). Adjust `known-artists.yml` to add titles relevant to your household.

---

## Future Roadmap

- **Sleep timer** — countdown + fade-out in the player screen
- **Per-profile listening time limits** — daily quota enforced in-app, more granular than Samsung Kids limits
- **Playback history for parents** — see what each child listened to and for how long
- **Home Assistant integration** — expose playback state as an HA sensor, control via automations
- **Push notifications to parent phone** — Web Push via existing VAPID infrastructure (no FCM needed)
- **Content recommendations** — suggest new content based on allowed artists via Spotify's recommendations API

---

## License

MIT License — see [LICENSE](LICENSE).
