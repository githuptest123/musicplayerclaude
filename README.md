# 🎵 Music Player for Android

A full-featured local music player built with Java/Android — no Android Studio needed!
Built via GitHub Actions → download APK directly to your phone.

---

## ✅ Features

- 🎵 Play local music files (MP3, FLAC, AAC, OGG, WAV, M4A, OPUS)
- ⏯ Play / Pause / Next / Previous
- ⏩ **+10 seconds skip** — tap once or **hold to keep skipping**
- ⏪ **−10 seconds skip** — tap once or **hold to keep skipping**
- 🎚 Drag the progress bar to seek anywhere in the song
- 🔀 Shuffle mode
- 🔁 Repeat: Off / All / One
- 📁 Folder browser
- 📋 Playlist support
- 🖼 Album art display with color palette
- 🎛 5-band Equalizer with presets
- 😴 Sleep Timer (5 min → 1 hour)
- 🎤 **Lyrics** — manual, sidecar .lrc files, or **OpenAI Whisper auto-transcription**
  - 🌍 Detects non-English songs and asks: translate to English or keep original
- 🌙 Dark / Light theme toggle
- 🔔 Notification with media controls
- 🎧 Background playback
- 📱 Lock screen controls
- 🔌 Auto-pause when headphones unplugged

---

## 🚀 HOW TO BUILD (No Android Studio needed!)

### Step 1 — Create a GitHub account
Go to https://github.com and sign up if you don't have an account.

### Step 2 — Create a new repository
1. Click the **+** button → **New repository**
2. Name it: `MusicPlayer`
3. Set it to **Public**
4. Click **Create repository**

### Step 3 — Upload all these files
You have two options:

**Option A — GitHub web interface (easiest):**
1. On your new repo page, click **uploading an existing file**
2. Drag and drop ALL the project files maintaining the folder structure
3. Click **Commit changes**

**Option B — GitHub Desktop app:**
1. Download GitHub Desktop from https://desktop.github.com
2. Clone your new repo
3. Copy all these files into the folder
4. Commit and Push

### Step 4 — Enable GitHub Actions
1. Go to your repo → click **Actions** tab
2. If prompted, click **"I understand my workflows, go ahead and enable them"**

### Step 5 — Trigger the build
The build starts automatically when you push files. Or:
1. Go to **Actions** → **Build APK** → **Run workflow** → **Run workflow**

### Step 6 — Download the APK
After ~3-5 minutes the build finishes:
1. Go to **Actions** → click the latest completed run
2. Scroll down to **Artifacts** → click **MusicPlayer-Debug-APK**
3. Download the ZIP → extract it → you get `app-debug.apk`

**OR** every push to main also creates a **Release** with the APK attached directly!
Go to **Releases** on your repo → download `app-debug.apk`

### Step 7 — Install on your Android phone
1. On your phone: **Settings → Security → Unknown Sources** → Enable
   (On newer Android: Settings → Apps → Special app access → Install unknown apps → Files/Browser → Allow)
2. Transfer the APK to your phone (email it to yourself, or use USB)
3. Tap the APK file → **Install**
4. Open **Music Player** 🎉

---

## 🎤 Setting up Auto-Lyrics (optional)

Auto-lyrics uses OpenAI's Whisper API to transcribe your songs in real time.

1. Get an API key at https://platform.openai.com/api-keys
2. In the app: **Settings → OpenAI API Key** → paste your key
3. Open any song → tap the **lyrics button** (💬 icon)
4. If the song isn't in English, the app will ask if you want it translated

**Cost:** Whisper costs ~$0.006/minute of audio. A 4-minute song ≈ $0.024.

**Free alternatives:**
- Add lyrics manually: tap the lyrics button → **Add / Edit**
- Create a `.lrc` or `.txt` file with the same name as your song in the same folder

---

## 📁 Project Structure

```
MusicPlayer/
├── .github/workflows/build.yml       ← GitHub Actions (auto-builds APK)
├── app/
│   ├── build.gradle                  ← App dependencies
│   └── src/main/
│       ├── AndroidManifest.xml       ← Permissions & components
│       ├── java/com/musicplayer/app/
│       │   ├── MainActivity.java     ← Song list + search
│       │   ├── NowPlayingActivity.java ← Player screen
│       │   ├── FolderBrowserActivity.java
│       │   ├── PlaylistActivity.java
│       │   ├── EqualizerActivity.java
│       │   ├── SettingsActivity.java
│       │   ├── adapters/SongAdapter.java
│       │   ├── models/Song.java
│       │   ├── models/Playlist.java
│       │   ├── services/MusicService.java  ← Background playback
│       │   ├── services/HeadsetReceiver.java
│       │   ├── services/SleepTimerReceiver.java
│       │   └── utils/
│       │       ├── MusicLibrary.java  ← Scans device for music
│       │       ├── LyricsManager.java ← Lyrics + Whisper API
│       │       └── PreferenceManager.java
│       └── res/
│           ├── layout/               ← All screen layouts
│           ├── drawable/             ← Icons & shapes
│           ├── values/               ← Colors, strings, themes
│           ├── menu/                 ← Navigation menus
│           ├── anim/                 ← Animations
│           └── xml/preferences.xml  ← Settings screen
├── build.gradle
├── settings.gradle
└── gradle/wrapper/gradle-wrapper.properties
```

---

## 🎮 Controls Guide

| Control | Action |
|---------|--------|
| Tap ⏮ / ⏭ | Previous / Next song |
| Tap ⏪ / ⏩ | Skip −10s / +10s |
| **Hold** ⏪ / ⏩ | **Continue skipping** every 300ms |
| Drag progress bar | Seek to any position |
| Swipe album art left | Next song |
| Swipe album art right | Previous song |
| Tap 🔀 | Toggle shuffle |
| Tap 🔁 | Cycle repeat (Off → All → One) |
| Tap 💬 | Open/close lyrics panel |
| Tap 🎛 | Open equalizer |
| Tap ⏰ | Set sleep timer |

---

## 🐛 Troubleshooting

**"No music found"** → Make sure you granted storage permission. Go to phone Settings → Apps → Music Player → Permissions → Allow storage.

**Build fails** → Check the Actions tab for error details. Most common issue: make sure ALL files are uploaded with correct folder paths.

**APK won't install** → Make sure "Install unknown apps" is enabled for the browser/files app you're using to open it.

**Lyrics not loading** → Check your OpenAI API key in Settings. Make sure you have internet. Files >25MB can't be auto-transcribed (add lyrics manually instead).
