# Mapa de código de Stone (Android)

Generado por `tools/update_map.py`. Lee este mapa, abre solo el archivo que necesitas y,
si es grande, busca con grep la función antes de leerlo entero.

## app/
- `MainActivity.kt` (138) Volume keys drive Stone's own overlay;

## app/automation
- `ApiMode.kt` (98) Stone's Modo MCP, run by Arka (same developer, through [HubLinkProvider]):
- `HubLink.kt` (80) How Arka runs Stone's Modo MCP.

## app/automation/actions
- `AutomationHost.kt` (71) What the running app offers to automation.
- `MetadataBatch.kt` (125) One item of a batch:
- `MetadataEdits.kt` (177) Tag, lyrics and cover edits made by agents.
- `StoneAction.kt` (85) How dangerous an action is;
- `StoneActions.kt` (129) Catalogue of Stone actions, grouped by domain:
- `StoneAgentActions.kt` (48) Tools that help agents help themselves:
- `StoneAutomation.kt` (175) A CRITICAL action waiting for the user's answer on screen.
- `StoneBulkActions.kt` (81) Many songs at once:
- `StoneGuide.kt` (95) Discovery help for agents:
- `StoneJobs.kt` (125) Background batches for agents.
- `StoneLibraryEditActions.kt` (161) Bulk tools:
- `StoneLookActions.kt` (100) Appearance and settings actions:
- `StoneMediaActions.kt` (218) Queue, equalizer, video and library-view actions (added in 1.6.7).
- `StoneMetadataActions.kt` (143) Tags, lyrics and covers of songs and videos:
- `StoneSettingsActions.kt` (118) Favourites by id, playlist management, audio levels and the remaining Ajustes switches.
- `StoneSolutions.kt` (104) Dictionary of solutions for agents, like a skill they can look up:

## app/automation/activity
- `ActivityLog.kt` (71) Keeps the agents' activity on the phone, so the last calls and their errors are still there after Stone is closed and an agent can read them with ston

## app/automation/auth
- `AccessMode.kt` (76) What a token lets an agent do.
- `ApiTokens.kt` (65) The one token Stone serves:
- `AuthContext.kt` (22) Who is making the request being executed:
- `AuthThrottle.kt` (29) Slows down token guessing:

## app/automation/relay
- `ReconnectPolicy.kt` (26) When the phone should dial the relay again.
- `RelayClient.kt` (214) Keeps Stone reachable at a fixed public link through the Stone relay (see ../relay).
- `RelayGuard.kt` (84) Safety rules for requests that arrive through the internet relay.

## app/automation/server
- `McpHttp.kt` (119) One parsed HTTP/1.1 request;
- `McpServer.kt` (353) Model Context Protocol server exposing [StoneAutomation] actions as tools.
- `ServerMetrics.kt` (87) What the MCP server and the relay link actually do, since Stone started, to measure before optimising:

## app/data/library
- `NotificationAlarmReceiver.kt` (21) Fires the scheduled summary / reminder, and restores the alarms after a reboot or update.

## app/data/library/artwork
- `ArtworkLoader.kt` (180) Only visible artwork is loaded.
- `CoverDownloader.kt` (78) Downloads a cover image from the internet (https only) and returns it as a JPEG of at most 1600 px, ready to embed in a file.

## app/data/library/lyrics
- `EmbeddedLyricsReader.kt` (91) Lyrics embedded in the file:
- `LrcLibClient.kt` (66) Lyrics found online:
- `LyricsIndex.kt` (77) Which songs have lyrics, for the "LETRAS" badge, the "Con letras" list and sorting.
- `LyricsMatcher.kt` (61) What to search for a song:
- `LyricsOverrides.kt` (39) User-entered lyrics are durable data and intentionally live outside the disposable library cache.
- `LyricsTextCleaner.kt` (27) Cleans lyrics copied from a web page:

## app/data/library/notifications
- `NewFilesNotifier.kt` (96) "Notificación de nuevos archivos":
- `NotificationScheduler.kt` (51) Ajustes › Notificaciones:

## app/data/library/scan
- `DeviceMediaScanner.kt` (84) Runtime permission needed to read every song and video indexed by Android.
- `FolderScanner.kt` (77) Uses Stone's selected SAF folder;
- `ImportedLibraryStore.kt` (39) Legacy app-private media index kept so imports from older releases remain playable.
- `LibraryCache.kt` (62) Disposable index only;
- `LibraryRepository.kt` (111) Whole-device scanning is on and Android granted the media permission.
- `NotMusic.kt` (16) Files other apps leave in Android's media index that are not music, such as their automatic backups ("AutoBackup_20261115_205503821", album "All data
- `SongJson.kt` (57) Stable representation shared by the library cache and legacy app-private media index.

## app/data/library/tags
- `AudioMetadataReader.kt` (35) Port of Stone's metadata reader, without its React Native bridge.
- `AudioTagEditor.kt` (94) New front cover (JPEG/PNG bytes) or null to keep the current one.
- `AudioTagWriters.kt` (261) Pure tag builders (no I/O) so they can be unit tested.
- `EditJournal.kt` (73) What a tag, lyrics or cover edit replaced, so it can be undone.
- `MediaFileIo.kt` (175) Shared file access for the tag editors.
- `Mp4Atoms.kt` (196) Pure MP4 box logic for metadata editing.
- `Mp4MetadataWriter.kt` (177) Streams an MP4/M4V edit without loading the media into memory.
- `TagSuggestions.kt` (34) A possible set of tags for a song, with how sure Stone is that it is the same song (0-1).
- `TitleCleaner.kt` (43) Leaves only the real song title in names copied from YouTube and similar sites:
- `VideoMetadata.kt` (284) Comma separated keywords, stored as the MP4 `keyw` tag.

## app/data/library/user
- `LibraryUserStore.kt` (70) One database read, with no zero-count entries retained in the UI state.
- `PlaylistCoverStore.kt` (72) Copies selected playlist artwork into app-private storage so it remains available later.
- `RenameTemplate.kt` (50) Values a rename template can use for one item.
- `SongActions.kt` (125) Shares several songs or videos in one chooser.
- `SongDispositionStore.kt` (27) Ids whose file was removed from the device;
- `SongFileDeleter.kt` (30) Removes the physical media file.

## app/data/library/web
- `WebPacer.kt` (83) The service said "too many requests" (HTTP 429);

## app/data/persistence
- `CrashLog.kt` (43) Keeps the last crash on the phone ("Stone se detuvo"), so it can be read and sent from Ajustes › Diagnóstico.
- `LookImages.kt` (42) Private copies of images chosen in Personalizado (background, avatar, shortcut icon).
- `PlaybackDataStore.kt` (52) Optional extracted cover used to rebuild system media artwork after restart.
- `SecretBox.kt` (53) Encrypts API secrets (agent tokens, the relay key) before they are stored, with an AES-GCM key that lives in the Android Keystore and never leaves it.
- `StoneDatabase.kt` (91) Versioned source of truth for user-created Stone data.
- `StoneSettings.kt` (147) Last custom duration chosen in the sleep timer (minutes).

## app/feature/library
- `LibraryOrdering.kt` (35)
- `LibraryScreen.kt` (254)
- `LibraryViewModel.kt` (188) Refreshes which songs have lyrics (background, cached per file).
- `LibraryViewState.kt` (13) Library order shared by the sort sheet and automation (MCP mode), so both stay in sync.
- `QuickActions.kt` (42) Single source of truth for home shortcuts.

## app/feature/library/components
- `AlbumCard.kt` (57)
- `ArtistCard.kt` (54)
- `GenreCards.kt` (44) Card colours;
- `HomeNavigationRail.kt` (71)
- `LandscapeLibraryHeading.kt` (39)
- `LayoutSelector.kt` (53) Compact / list / "Grande" switch, icons only;
- `LibraryContent.kt` (242) Callbacks used by long-press selection in the library lists.
- `LibraryEmptyState.kt` (23)
- `LibraryHeader.kt` (97)
- `LibraryLayoutFrame.kt` (28) One content call site keeps the library alive when its surrounding layout changes.
- `LibrarySearch.kt` (44)
- `LibrarySelection.kt` (87) "N seleccionado" header with back and select-all, shown after a long press.
- `LibrarySheets.kt` (59)
- `LibrarySongRow.kt` (72)
- `LibraryTabs.kt` (62) Videos holds every video;
- `PlaylistCards.kt` (93) Icon and tint in front of a smart playlist title.
- `PlaylistLibrary.kt` (117)
- `RecentlyDeletedSheet.kt` (116) Trash:
- `SongGridCard.kt` (41) Song card of the "Grande" layout:

## app/feature/library/video
- `VideoGridCard.kt` (223) Round check used on selected cards.
- `VideoLibraryContent.kt` (126)
- `VideoLibraryLayout.kt` (16) Presentation used by the portrait video library.
- `VideoSelection.kt` (60) Long-press selection for video grids/lists (Videos, Shorts, Movies).

## app/feature/onboarding
- `OnboardingScreen.kt` (91) First launch, drawn over the app so the library underneath is already loading:

## app/feature/player
- `ActivityLookup.kt` (13) The Activity behind a Compose context (rotation, brightness, picture-in-picture).
- `CompactPlayerControls.kt` (88)
- `LandscapePlayerPanel.kt` (81)
- `LandscapeVinylPlayer.kt` (194) One full turn of the record moves the song this far (like scratching a real vinyl).
- `LibraryPlaybackPane.kt` (79)
- `MiniPlayer.kt` (135)
- `MiniPlayerOptionsPanel.kt` (65) Right-side options panel used only by the horizontal mini player.
- `PlayerArtwork.kt` (57) Cover in the portrait player, in the shape chosen in Ajustes › Personalizado:
- `PlayerProgress.kt` (72)
- `PlayerScreen.kt` (269) Horizontal swipe changes tracks;
- `SongTint.kt` (32) Colour taken from the current song's artwork, used by the queue and option sheets.

## app/feature/player/lyrics
- `LyricPhrase.kt` (45) One phrase on a single line, left aligned.
- `LyricsAutoSearch.kt` (67) "Buscar automáticamente":
- `LyricsClock.kt` (91) The song's lyrics:
- `LyricsFileSync.kt` (45) Copies saved lyrics into the song file itself (MP3 USLT, FLAC LYRICS, M4A ©lyr), so they travel with the file when it is shared or opened in another p
- `LyricsSearchBrowser.kt` (118) Search engines offered as chips;
- `LyricsSheet.kt` (227)
- `LyricsWebExtractor.kt` (60) Finds the lyrics on the page open in the lyrics browser.
- `PlayerLyricChip.kt` (89) Compact lyric window under the player controls:

## app/feature/player/sheets
- `PlayerSheetsHost.kt` (92) Shared by the full player and the home dock;
- `PlayerSheetsState.kt` (37) Item whose shared options menu is open;
- `PlayerToolsSheets.kt` (399) Song currently played as audio only;
- `QueueSheet.kt` (243) Outlined "LETRAS" label in front of the subtitle of songs that have lyrics.
- `RecommendationsSheet.kt` (80) Songs to play after [current]:
- `SleepTimerSheet.kt` (134) Preset durations offered in the sheet (0 = off).

## app/feature/player/tags
- `AudioTagEditorSheet.kt` (157) "Editar etiquetas":
- `MediaWriteGate.kt` (35) Runs a write action on a media file, first asking Android for write consent when the file comes from the device-wide MediaStore index (Android 11+).
- `TagEditorParts.kt` (97) Full-screen editor frame:
- `TagSuggestDialog.kt` (82) Results of "Completar etiquetas":
- `VideoTagEditorSheet.kt` (157) "Editar información de video":

## app/feature/player/video
- `LandscapeVideoChrome.kt` (173) Which right-hand panel is open over the landscape video.
- `LandscapeVideoPreviewPanel.kt` (312) Video-specific preview used by the horizontal library layout.
- `PortraitVideoChrome.kt` (121) Portrait video controls:
- `VideoControlPreferences.kt` (83) Secondary actions that can be shown below a video preview or player.
- `VideoControlRow.kt` (114) Renders the user-configurable secondary video actions in their saved order.
- `VideoGestures.kt` (222) Handles vertical brightness/volume adjustments and horizontal track changes.
- `VideoOptionsSheet.kt` (200) Video ⋮ menu.
- `VideoPlayers.kt` (129) Controls stay visible;
- `VideoPreferences.kt` (67) How the video is fitted inside its available bounds.
- `VideoSidePanels.kt` (145) ⋮ menu of the landscape video:
- `VideoTimeline.kt` (142) A bounded, YouTube-like preview strip.

## app/feature/settings
- `AudioLevelSettings.kt` (115) The «in range» green of the loudness readings.
- `LegalScreens.kt` (59) Where "Calificarnos" sends people until Stone is on Google Play.
- `LibraryScanSettings.kt` (62) Whole-device scanning (MediaStore + permission) and automatic rescans on open.
- `McpByArka.kt` (52) Ajustes › Modo MCP:
- `NotificationSettings.kt` (109) Ajustes › Notificaciones, in one place:
- `SettingsMiscDetails.kt` (74)
- `SettingsParts.kt` (42)
- `SettingsPlaybackDetails.kt` (187)
- `SettingsScreens.kt` (248) Languages Stone ships complete translations for (names in their own language).
- `SettingsShortcutsDetail.kt` (180) A tap moves once;
- `SystemDiagnosticsDetail.kt` (226)
- `TransitionSettings.kt` (40) Reproducción sin pausas and Crossfade switches;
- `VideoControlSettings.kt` (80)
- `WidgetSettings.kt` (61) Ajustes › Widgets:

## app/feature/settings/feedback
- `CrashReportCard.kt` (41) Ajustes › Diagnóstico:
- `FeedbackFormParts.kt` (117) Visual pieces of the feedback form;
- `FeedbackReport.kt` (166)
- `ReportScreenshot.kt` (58) Captures what the user is looking at when they ask to report a problem, so they don't have to take a screenshot and attach it from the gallery.

## app/feature/settings/look
- `LookArtworkShape.kt` (55) Title and hint of each player artwork shape, in the order shown in Ajustes.
- `LookBackgroundEditor.kt` (67) Background photo options:
- `LookColorEditor.kt` (58) Mixer for the free accent colour:
- `LookColorPalette.kt` (51) Touch palette:
- `LookSettings.kt` (120) Ajustes › Personalizado:
- `LookSettingsParts.kt` (92) Small building blocks shared by the Personalizado screens.
- `ThemeExtras.kt` (50) Top of "Temas y personalización":

## app/playback
- `ExternalOpen.kt` (59) «Abrir con Stone» from another app (Luma, a file manager):
- `StonePlaybackService.kt` (497) Sole owner of playback;
- `StoneWidgetProvider.kt` (109) Home-screen player (4×2):

## app/playback/audio
- `AndroidVolumeMapper.kt` (33) Conversion between Android's discrete STREAM_MUSIC levels and Stone's native 0..100% display.
- `AudioGenreAnalyzer.kt` (50) On-demand metadata classifier.
- `GenreEqualizer.kt` (45) Equalizer by genre:
- `PlaybackEqualizer.kt` (89) Read-only state used by the audio investigation;
- `PlaybackVolumeBoost.kt` (164) A percentage above 100 maps to a logarithmic gain;

## app/playback/control
- `PlaybackViewModel.kt` (709) [gain] is a software attenuation applied on top of Android's volume.
- `RequestedItemGuard.kt` (26) Keeps the song the user just tapped as the current one while the player loads the new list.
- `StoneEcoEngine.kt` (132) Lightweight playback policy.
- `SystemArtworkOverlay.kt` (108) Builds the artwork sent to Android's media controls without changing the artwork embedded in, or next to, the user's media file.
- `TrackTransitions.kt` (120) Song-to-song transitions on Stone's single player (no second decoder):

## app/playback/diagnostics
- `AudioDiagnostics.kt` (213) Read-only audio tracing used while investigating volume changes.
- `DiagnosticsReportExporter.kt` (33)
- `PerformanceDiagnostics.kt` (88) /proc exposes this count without allocating a stack trace for every thread.

## app/ui
- `AutomationBridge.kt` (161) Registers the running UI as the automation host (so agents use the same view models as the buttons) and shows the confirmation dialog for actions that
- `StoneApp.kt` (470) Mini player button:

## app/ui/components
- `SleepTimerIndicator.kt` (101) Shows the active sleep timer using the sun by day and the moon by night.
- `StoneArtwork.kt` (28)
- `StoneAvatar.kt` (17) The chosen mascot (same as the home-screen icon), or the image the user picked in Personalizado.
- `StoneIcon.kt` (53)

## app/ui/system
- `ImmersiveSystemBarsController.kt` (416) The single owner of system-bar visibility for Stone's Activity and Compose dialog windows.
- `ShakeDetector.kt` (53) Detects a deliberate shake from linear acceleration, not from the constant pull of gravity.
- `VolumeOverlay.kt` (110) Compact volume card.

## app/ui/theme
- `LauncherIcon.kt` (70) Stone's mascots.
- `StoneBackdrop.kt` (64) Theme backdrop:
- `StoneLook.kt` (140) Accent colour that replaces Stone's gold everywhere (lines, highlights, active icons).
- `StoneTheme.kt` (105) Accent ("dorado" by default);

## core/
- `HomeLayout.kt` (39) Pure window policy:
- `LyricTimeline.kt` (33) Where playback is inside the lyrics:
- `Lyrics.kt` (106) One word of an enhanced LRC line (`<mm:ss.xx>word`);
- `RefreshPolicy.kt` (21) Event-triggered only.
- `Song.kt` (28) Optional artwork extracted from a Stone package.
- `VoicePlay.kt` (49) What to play when an assistant asks:
