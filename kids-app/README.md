# KidsTune Kids App

Android app providing children with a safe, parent-controlled Spotify listening experience inside Samsung Kids.

## Samsung Kids Setup

Step-by-step instructions for deploying KidsTune Kids on a Samsung device running Samsung Kids.

### Prerequisites

- Samsung smartphone with **Samsung Kids** (One UI 4.0+)
- **Spotify** app (free or Premium — Premium required for App Remote playback control)
- **KidsTune Kids** APK (debug or release build)
- The child's Spotify account credentials (each child has their own account)
- Backend running and paired device token ready (from the web dashboard → Devices)

### Setup Steps

#### 1. Install Spotify

Install Spotify from the Play Store and log in with the **child's own Spotify account**. Do this outside Samsung Kids (in the normal Android launcher).

#### 2. Download offline content in Spotify

While still outside Samsung Kids, open Spotify and download the playlists/albums the child listens to for offline use. KidsTune streams through Spotify's own cache — the child must not need internet access for playback to work.

#### 3. Install KidsTune Kids

Install the KidsTune Kids APK (via `adb install` or direct APK transfer). Launch it once and complete the device pairing flow:

1. Open the web dashboard → **Devices → Add Device**
2. Copy the 6-character pairing code shown
3. Open KidsTune Kids and enter the code
4. Select the child profile to bind this device to

After pairing, KidsTune Kids will download the content tree and show the Home screen.

#### 4. Add KidsTune Kids to Samsung Kids

1. Open **Settings → Digital Wellbeing → Samsung Kids** (path may vary by One UI version)
2. Tap **Add apps** → find **KidsTune Kids** → add it
3. **Do NOT add Spotify** to the allowed apps list — Spotify must run only as a background service. If Spotify appears in Samsung Kids the child can access it directly and bypass KidsTune's content restrictions.

#### 5. Configure PIN and time limits

1. Set a Samsung Kids PIN (separate from the device unlock PIN)
2. Configure daily play time limits if desired
3. Optionally enable **Bedtime** for automatic lock-out

#### 6. Verify end-to-end

Launch Samsung Kids and verify the following checklist:

| # | Check | Expected |
|---|-------|----------|
| 1 | Open Samsung Kids | KidsTune Kids tile is visible |
| 2 | Tap KidsTune Kids | App opens directly to Home screen |
| 3 | Tap an album → play a track | Music plays via Spotify background service |
| 4 | Press Home inside Samsung Kids | Mini-player bar retains state |
| 5 | Re-open KidsTune Kids | Playback continues / can be resumed |
| 6 | Reach time limit (or pause Samsung Kids from parent) | App pauses cleanly, position saved |
| 7 | Resume Samsung Kids | App reconnects to Spotify, child can continue |
| 8 | Reboot device → open Samsung Kids → KidsTune Kids | App relaunches, resumes from saved position |
| 9 | Try to navigate to Spotify from inside Samsung Kids | Not possible — Spotify is not in the allowed list |
| 10 | Try to exit to the normal Android launcher | Samsung Kids blocks this |

---

### How Samsung Kids Containment Works

```
Normal launcher                Samsung Kids launcher
─────────────────              ──────────────────────────────────
Spotify (logged in)  ←──────  KidsTune Kids (only allowed app)
         │                              │
         │  App Remote SDK (IPC)        │ controls Spotify
         └──────────────────────────────┘
                    ▲
              audio playback
```

- **Spotify runs as a background service** — Samsung Kids does not show it to the child, but it remains running so the Spotify App Remote SDK (embedded in KidsTune) can control playback via IPC.
- **KidsTune Kids is the only visible app** inside Samsung Kids. The child can only do what KidsTune's UI allows.
- **Audio focus** is managed entirely by the Spotify app. If another sound (notification, alarm) temporarily takes audio focus, Spotify will automatically pause and resume — KidsTune's UI reflects this via the `playerStateFlow` subscription.

### Activity Lifecycle & State Preservation

KidsTune correctly handles the Samsung Kids activity lifecycle:

| Event | What happens |
|-------|--------------|
| Samsung Kids time limit pause | `onStop` → playback position flushed to Room |
| Parent temporarily pauses Samsung Kids | `onStop` → playback position flushed to Room |
| Samsung Kids resumed | `onStart` → `SpotifyRemote.connect()` re-established (idempotent) |
| Samsung Kids full restart | `onDestroy` → `onStart` → fresh Spotify connection; position restored from Room |
| Device reboot | WorkManager reschedules content sync on next boot |

`MainActivity` is declared with `launchMode="singleTask"` so Samsung Kids can never create a second instance of the activity.

### Troubleshooting

**Music does not play / "Spotify not connected" error**

- Verify Spotify is installed and the child is logged in (do this outside Samsung Kids).
- Verify Spotify has at least one downloaded playlist/album (required for offline).
- Spotify must NOT be in the Samsung Kids allowed apps list — it should only run as a background service.

**App resets to pairing screen after Samsung Kids restart**

- This is expected only on the very first launch. After pairing, the device token is stored in SharedPreferences and persists across restarts.
- If the device token was cleared (e.g., factory reset), re-pair via the web dashboard → Devices.

**Time limit reached but position is not restored on resume**

- Position is flushed to Room on every `onStop`. On `onStart`, KidsTune reconnects to Spotify but does not automatically resume playback — the child taps Play to continue from the saved position. This is intentional (children should consciously resume after a break).
