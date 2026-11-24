Effective 16 November 2026.

Stone is a music and video player that runs on your device. This policy explains what data the app handles. "We" means the developer of Stone, based in Ecuador.

## Summary
- Stone has no accounts, no ads, and no trackers or analytics.
- We do not receive your library, playlists or history: they stay on your device.
- We do not sell or share personal information.
- Information only leaves the device when you turn on a feature that needs it (see "Features that use the internet").

## Data stored on your device
- Your library index: file name, title, artist, album, folder, duration and artwork of your songs and videos.
- Your playlists, favourites, recent playback history and play counts.
- Your settings: theme, language, equalizer, sleep timer, home shortcuts, colours, background photo and similar.
- Lyrics you save or import.
- From MCP mode: access tokens (name, mode and last use), the session activity log, and backups of the tags, lyrics and covers an assistant changes, so they can be undone.
This data lives in the app's private storage and is erased when you uninstall the app or clear its data in Android settings.

## Android permissions
- Music and videos (media read access): to find and play your files.
- Notifications and foreground service: for playback controls and, if you turn it on, to tell you about new files. The notice is created on the phone; we do not use server-sent notifications.
- Audio settings: for volume, the equalizer and sound modes.
- Modify system settings: only if you choose to set a song as your ringtone.
- All files access (optional, only if you grant it in Android): so tag edits do not ask for permission file by file. Stone does not use it to read other kinds of files.
- Internet: only for the features described below.
Stone edits or deletes files only when you ask, or when an assistant you gave a token with write access asks.

## Features that use the internet
- MCP mode (off by default). When on, Stone opens a server on your phone so the AI assistants you give a token can read or control Stone according to that token's mode. With the public link, requests go through Stone's relay, hosted on Cloudflare. The relay forwards requests and responses in transit and does not store their content; it keeps a random device identifier and a hash of its key. Cloudflare, as infrastructure provider, may process technical data such as IP addresses. Each assistant receives what it requests (for example titles, artists, tags, lyrics and playback state) and handles it under its own privacy policy. You can change a token's mode, revoke it or turn MCP mode off at any time.
- Covers from a URL: when an assistant asks to use an image from the internet as a cover, Stone downloads it from that address.
- Automatic lyrics search: Stone sends the song's title, artist, album and duration to lrclib.net to find its lyrics. LRCLIB is a third-party service with its own policy.
- Lyrics browser: opens a search engine or lyrics site (Google, Letras.com, Genius or Musixmatch) inside Stone with the title and artist. Those sites may store cookies in the built-in browser and apply their own policies.
- Reports and suggestions: Stone prepares the report and you choose which app sends it. Nothing is sent automatically.
- Links (for example "Rate us"): open your browser on a third-party site, which applies its own policy.

## Children
Stone is not directed to children under 13 and we do not knowingly collect personal data from children.

## Your rights
- Ecuador: the Organic Law on Personal Data Protection grants you the rights of access, rectification and update, deletion, objection, portability and not to be subject to automated decisions.
- United States: state privacy laws, such as California's CCPA/CPRA, grant the rights to know, correct and delete your data, and not to be discriminated against for exercising them. We do not sell or share personal information.
Because we do not keep your data on our servers, you control it from the device: you can edit or delete it in the app or by clearing the app's data. For any request or question, use the contact channel.

## Security
Each MCP mode token has its own mode: Read only, Read and write (critical actions, such as deleting a playlist, ask for your confirmation) or Full access (nothing asks). Use Full access only with assistants you trust. Whoever has a link can act with its token's mode: do not publish it, and revoke it if it leaks. No system is completely secure.

## Changes
If this policy changes, we will publish the new version in the app with its effective date.

## Contact
github.com/SiriusMagic/Stone (Issues section).
