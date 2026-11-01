# Pingüino Music — Product Requirements

## Overview
A mobile music player app (Expo / React Native, Android-first APK) with a friendly penguin mascot. The app guides users through a language picker and a "Reproduce tu música" welcome screen, then lands on a tabbed library (Playlists, Carpetas, Artistas, Álbumes) with playback controls.

## Core Flows (implemented)
1. **First-run onboarding**
   - `/onboarding/language` — pick from Español, English, Português, Français, Italiano, Deutsch. Yellow `Empezar` CTA. Persists choice in `@app/lang`.
   - `/onboarding/welcome` — "Reproduce tu música" hero with mascot + decorative floating tiles. CTA `Permitir acceso` marks `@app/onboarded=true` and routes to library.
   - Subsequent launches skip onboarding and go straight to `/library`.

2. **Library (`/library`)**
   - Sticky top bar: mascot logo, search, sort, language toggle.
   - Horizontal chip filters (Playlists / Carpetas / Artistas / Álbumes), single-row scrolling chrome.
   - Shows a teaser track on first launch + a yellow `Escanear carpetas` CTA. Tapping it runs a simulated scan (0→100%) and loads the full demo library (10 royalty-free tracks).
   - Each tab regroups the same tracks by Playlist (flat list), Folder, Artist, or Album.
   - Search input filters by title/artist/album.
   - Sort sheet: Title / Artist / Recent.
   - Language sheet: switch language at any time.

3. **Playback**
   - Tapping a row plays it and queues all current results.
   - Persistent floating mini-player at bottom (cover + title + status + play/pause). Tap to open full player.
   - Full player (`/player` modal): cover, title, artist, scrubbable progress bar with seek, prev/next, play/pause, shuffle, repeat (off → all → one). Auto-advances on track end.
   - Uses **expo-audio** (`useAudioPlayer` + `useAudioPlayerStatus`) with remote royalty-free MP3s (SoundHelix).

## Languages
ES, EN, PT, FR, IT, DE — full string coverage for every visible label.

## Demo Data
10 tracks across 5 albums, 5 artists, 2 folders. All cover art uses Unsplash CDN.

## Out of Scope (current iteration)
- Native MediaStore / file-system scanning (works only in APK build, deferred until user requests it).
- Saved/custom playlist editing (CRUD).
- Background audio + lock-screen controls (requires native build).
- Streaming services.

## Smart Business Enhancement (next)
Add a "Reproducción del día" daily-mix card at the top of Playlists (auto-rotating selection) — increases session length & retention without extra content costs.

## Test Credentials
N/A — no authentication. See `/app/memory/test_credentials.md`.
