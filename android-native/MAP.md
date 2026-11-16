# Mapa de código de Stone (Android)

Generado por `tools/update_map.py`. Lee este mapa, abre solo el archivo que necesitas y,
si es grande, busca con grep la función antes de leerlo entero.

## app/
- `MainActivity.kt` (132) Volume keys drive Stone's own overlay;

## app/automation
- `AccessMode.kt` (55) What a token lets an agent do.
- `ActivityLog.kt` (65) Keeps Ajustes › Modo API › Actividad on the phone, so the last calls and their errors are still there after Stone is closed and an agent can read them
- `ApiMode.kt` (153) User switches for the MCP server (Ajustes › Modo API).
- `ApiTokens.kt` (96) Access tokens of Ajustes › Modo API › Seguridad.
- `AuthThrottle.kt` (29) Slows down token guessing:
- `AutomationHost.kt` (71) What the running app offers to automation.
- `McpHttp.kt` (114) One parsed HTTP/1.1 request;
- `McpSelfTest.kt` (50) Result of calling Stone's own MCP address the way an agent would.
- `McpServer.kt` (292) Model Context Protocol server exposing [StoneAutomation] actions as tools.
- `MetadataBatch.kt` (126) One item of a batch:
- `MetadataEdits.kt` (177) Tag, lyrics and cover edits made by agents.
- `RelayClient.kt` (127) Keeps Stone reachable at a fixed public link through the Stone relay (see ../relay).
- `RequestStats.kt` (91) Requests per minute for Ajustes › Modo API › Analítica:
- `StoneAction.kt` (83) How dangerous an action is;
- `StoneActions.kt` (129) Catalogue of Stone actions, grouped by domain:
- `StoneAgentActions.kt` (37) Tools that help agents help themselves:
- `StoneAutomation.kt` (165) A CRITICAL action waiting for the user's answer on screen.
- `StoneBulkActions.kt` (81) Many songs at once:
- `StoneGuide.kt` (91) Discovery help for agents:
- `StoneJobs.kt` (122) Background batches for agents.
- `StoneLibraryEditActions.kt` (161) Bulk tools:
- `StoneLookActions.kt` (100) Appearance and settings actions:
- `StoneMediaActions.kt` (218) Queue, equalizer, video and library-view actions (added in 1.6.7).
- `StoneMetadataActions.kt` (143) Tags, lyrics and covers of songs and videos:
- `StoneSettingsActions.kt` (118) Favourites by id, playlist management, audio levels and the remaining Ajustes switches.
- `StoneSolutions.kt` (104) Dictionary of solutions for agents, like a skill they can look up:

## app/data/library
- `ArtworkLoader.kt` (180) Only visible artwork is loaded.
- `AudioMetadataReader.kt` (34) Port of Stone's metadata reader, without its React Native bridge.
- `AudioTagEditor.kt` (158) New front cover (JPEG/PNG bytes) or null to keep the current one.
- `AudioTagWriters.kt` (261) Pure tag builders (no I/O) so they can be unit tested.
- `CoverDownloader.kt` (76) Downloads a cover image from the internet (http/https) and returns it as a JPEG of at most 1600 px, ready to embed in a file.
- `DeviceMediaScanner.kt` (84) Runtime permission needed to read every song and video indexed by Android.
- `EditJournal.kt` (73) What a tag, lyrics or cover edit replaced, so it can be undone.
- `EmbeddedLyricsReader.kt` (88) Lyrics embedded in the file:
- `FolderScanner.kt` (76) Uses Stone's selected SAF folder;
- `ImportedLibraryStore.kt` (39) Legacy app-private media index kept so imports from older releases remain playable.
- `LibraryCache.kt` (62) Disposable index only;
- `LibraryRepository.kt` (105) Whole-device scanning is on and Android granted the media permission.
- `LibraryUserStore.kt` (70) One database read, with no zero-count entries retained in the UI state.
- `LrcLibClient.kt` (65) Lyrics found online:
- `LyricsIndex.kt` (77) Which songs have lyrics, for the "LETRAS" badge, the "Con letras" list and sorting.
- `LyricsMatcher.kt` (60) What to search for a song:
- `LyricsOverrides.kt` (45) User-entered lyrics are durable data and intentionally live outside the disposable library cache.
- `LyricsTextCleaner.kt` (27) Cleans lyrics copied from a web page:
- `MediaFileIo.kt` (175) Shared file access for the tag editors.
- `Mp4Atoms.kt` (196) Pure MP4 box logic for metadata editing.
- `Mp4MetadataWriter.kt` (177) Streams an MP4/M4V edit without loading the media into memory.
- `NewFilesNotifier.kt` (96) "Notificación de nuevos archivos":
- `NotificationScheduler.kt` (62) Ajustes › Notificaciones:
- `PlaylistCoverStore.kt` (72) Copies selected playlist artwork into app-private storage so it remains available later.
- `RenameTemplate.kt` (50) Values a rename template can use for one item.
- `SongActions.kt` (125) Shares several songs or videos in one chooser.
- `SongDispositionStore.kt` (27) Ids whose file was removed from the device;
- `SongFileDeleter.kt` (30) Removes the physical media file.
- `SongJson.kt` (57) Stable representation shared by the library cache and legacy app-private media index.
- `TagSuggestions.kt` (32) A possible set of tags for a song, with how sure Stone is that it is the same song (0-1).
- `TitleCleaner.kt` (41) Leaves only the real song title in names copied from YouTube and similar sites:
- `VideoMetadata.kt` (285) Comma separated keywords, stored as the MP4 `keyw` tag.
- `WebPacer.kt` (86) The service said "too many requests" (HTTP 429);

## app/data/persistence
- `CrashLog.kt` (43) Keeps the last crash on the phone ("Stone se detuvo"), so it can be read and sent from Ajustes › Diagnóstico.
- `LookImages.kt` (42) Private copies of images chosen in Personalizado (background, avatar, shortcut icon).
- `PlaybackDataStore.kt` (52) Optional extracted cover used to rebuild system media artwork after restart.
- `SecretBox.kt` (53) Encrypts API secrets (agent tokens, the relay key) before they are stored, with an AES-GCM key that lives in the Android Keystore and never leaves it.
- `StoneDatabase.kt` (91) Versioned source of truth for user-created Stone data.
- `StoneSettings.kt` (147) Last custom duration chosen in the sleep timer (minutes).

## app/feature/library
- `LibraryOrdering.kt` (35)
- `LibraryScreen.kt` (250)
- `LibraryViewModel.kt` (195) Refreshes which songs have lyrics (background, cached per file).
- `LibraryViewState.kt` (13) Library order shared by the sort sheet and automation (API mode), so both stay in sync.
- `QuickActions.kt` (42) Single source of truth for home shortcuts.

## app/feature/library/components
- `AlbumCard.kt` (57)
- `ApiActivityTab.kt` (92) Actividad tab:
- `ApiAnalytics.kt` (144) Time windows:
- `ApiGeneralTab.kt` (67) General tab:
- `ApiLinkTab.kt` (53) Enlace tab:
- `ApiModeConnect.kt` (63) Port, "Probar conexión" and ready-to-paste client configurations.
- `ApiModeLinkCard.kt` (57) The two-click part of API mode:
- `ApiModeSettings.kt` (102) Ajustes › Modo API:
- `ApiModeShared.kt` (126) Tabs of Ajustes › Modo API.
- `ApiModeToolList.kt` (70) Herramientas tab:
- `ApiSecurityTab.kt` (81) Seguridad tab:
- `ApiTokenEditor.kt` (142) Full-screen editor of one token:
- `ApiTokenRow.kt` (108) One token:
- `ArtistCard.kt` (54)
- `AudioLevelSettings.kt` (112) Ajustes › Niveles de audio:
- `CrashReportCard.kt` (41) Ajustes › Diagnóstico:
- `FeedbackFormParts.kt` (117) Visual pieces of the feedback form;
- `FeedbackReport.kt` (166)
- `GenreCards.kt` (44) Card colours;
- `HomeNavigationRail.kt` (71)
- `LandscapeLibraryHeading.kt` (39)
- `LayoutSelector.kt` (52) Compact / list / "Grande" switch, icons only;
- `LegalScreens.kt` (59) Where "Calificarnos" sends people until Stone is on Google Play.
- `LibraryContent.kt` (240) Callbacks used by long-press selection in the library lists.
- `LibraryEmptyState.kt` (23)
- `LibraryHeader.kt` (97)
- `LibraryLayoutFrame.kt` (28) One content call site keeps the library alive when its surrounding layout changes.
- `LibraryScanSettings.kt` (62) Whole-device scanning (MediaStore + permission) and automatic rescans on open.
- `LibrarySearch.kt` (44)
- `LibrarySelection.kt` (87) "N seleccionado" header with back and select-all, shown after a long press.
- `LibrarySheets.kt` (59)
- `LibrarySongRow.kt` (72)
- `LibraryTabs.kt` (62) Videos holds every video;
- `LookArtworkShape.kt` (55) Title and hint of each player artwork shape, in the order shown in Ajustes.
- `LookBackgroundEditor.kt` (67) Background photo options:
- `LookColorEditor.kt` (58) Mixer for the free accent colour:
- `LookColorPalette.kt` (51) Touch palette:
- `LookSettings.kt` (120) Ajustes › Personalizado:
- `LookSettingsParts.kt` (92) Small building blocks shared by the Personalizado screens.
- `NotificationSettings.kt` (109) Ajustes › Notificaciones, in one place:
- `PlaylistCards.kt` (93) Icon and tint in front of a smart playlist title.
- `PlaylistLibrary.kt` (117)
- `RecentlyDeletedSheet.kt` (116) Trash:
- `ReportScreenshot.kt` (58) Captures what the user is looking at when they ask to report a problem, so they don't have to take a screenshot and attach it from the gallery.
- `SettingsMiscDetails.kt` (74)
- `SettingsParts.kt` (42)
- `SettingsPlaybackDetails.kt` (187)
- `SettingsScreens.kt` (245) Languages Stone ships complete translations for (names in their own language).
- `SettingsShortcutsDetail.kt` (180) A tap moves once;
- `SongGridCard.kt` (40) Song card of the "Grande" layout:
- `SystemDiagnosticsDetail.kt` (225)
- `ThemeExtras.kt` (49) Top of "Temas y personalización":
- `TransitionSettings.kt` (40) Reproducción sin pausas and Crossfade switches;
- `VideoControlSettings.kt` (80)
- `VideoGridCard.kt` (222) Round check used on selected cards.
- `VideoLibraryContent.kt` (123)
- `VideoLibraryLayout.kt` (16) Presentation used by the portrait video library.
- `VideoSelection.kt` (59) Long-press selection for video grids/lists (Videos, Shorts, Movies).
- `WidgetSettings.kt` (61) Ajustes › Widgets:

## app/feature/onboarding
- `OnboardingScreen.kt` (91) First launch, drawn over the app so the library underneath is already loading:

## app/feature/player
- `ActivityLookup.kt` (13) The Activity behind a Compose context (rotation, brightness, picture-in-picture).
- `AudioTagEditorSheet.kt` (157) "Editar etiquetas":
- `CompactPlayerControls.kt` (88)
- `LandscapePlayerPanel.kt` (81)
- `LandscapeVideoChrome.kt` (171) Which right-hand panel is open over the landscape video.
- `LandscapeVideoPreviewPanel.kt` (310) Video-specific preview used by the horizontal library layout.
- `LandscapeVinylPlayer.kt` (194) One full turn of the record moves the song this far (like scratching a real vinyl).
- `LibraryPlaybackPane.kt` (73)
- `LyricPhrase.kt` (45) One phrase on a single line, left aligned.
- `LyricsAutoSearch.kt` (67) "Buscar automáticamente":
- `LyricsClock.kt` (91) The song's lyrics:
- `LyricsFileSync.kt` (44) Copies saved lyrics into the song file itself (MP3 USLT, FLAC LYRICS, M4A ©lyr), so they travel with the file when it is shared or opened in another p
- `LyricsSearchBrowser.kt` (118) Search engines offered as chips;
- `LyricsSheet.kt` (226)
- `LyricsWebExtractor.kt` (60) Finds the lyrics on the page open in the lyrics browser.
- `MediaWriteGate.kt` (35) Runs a write action on a media file, first asking Android for write consent when the file comes from the device-wide MediaStore index (Android 11+).
- `MiniPlayer.kt` (135)
- `MiniPlayerOptionsPanel.kt` (62) Right-side options panel used only by the horizontal mini player.
- `PlaybackControls.kt` (33)
- `PlayerArtwork.kt` (57) Cover in the portrait player, in the shape chosen in Ajustes › Personalizado:
- `PlayerLyricChip.kt` (89) Compact lyric window under the player controls:
- `PlayerProgress.kt` (72)
- `PlayerScreen.kt` (256) Horizontal swipe changes tracks;
- `PlayerSheetsHost.kt` (87) Shared by the full player and the home dock;
- `PlayerSheetsState.kt` (37) Item whose shared options menu is open;
- `PlayerToolsSheets.kt` (397) Song currently played as audio only;
- `PortraitVideoChrome.kt` (120) Portrait video controls:
- `QueueSheet.kt` (244) Outlined "LETRAS" label in front of the subtitle of songs that have lyrics.
- `RecommendationsSheet.kt` (80) Songs to play after [current]:
- `SleepTimerSheet.kt` (134) Preset durations offered in the sheet (0 = off).
- `SongTint.kt` (31) Colour taken from the current song's artwork, used by the queue and option sheets.
- `TagEditorParts.kt` (97) Full-screen editor frame:
- `TagSuggestDialog.kt` (82) Results of "Completar etiquetas":
- `VideoControlPreferences.kt` (83) Secondary actions that can be shown below a video preview or player.
- `VideoControlRow.kt` (114) Renders the user-configurable secondary video actions in their saved order.
- `VideoGestures.kt` (221) Handles vertical brightness/volume adjustments and horizontal track changes.
- `VideoOptionsSheet.kt` (199) Video ⋮ menu.
- `VideoPlayers.kt` (127) Controls stay visible;
- `VideoPreferences.kt` (67) How the video is fitted inside its available bounds.
- `VideoSidePanels.kt` (144) ⋮ menu of the landscape video:
- `VideoTagEditorSheet.kt` (157) "Editar información de video":
- `VideoTimeline.kt` (141) A bounded, YouTube-like preview strip.

## app/playback
- `AndroidVolumeMapper.kt` (33) Conversion between Android's discrete STREAM_MUSIC levels and Stone's native 0..100% display.
- `AudioDiagnostics.kt` (213) Read-only audio tracing used while investigating volume changes.
- `AudioGenreAnalyzer.kt` (50) On-demand metadata classifier.
- `DiagnosticsReportExporter.kt` (33)
- `GenreEqualizer.kt` (45) Equalizer by genre:
- `PerformanceDiagnostics.kt` (88) /proc exposes this count without allocating a stack trace for every thread.
- `PlaybackEqualizer.kt` (89) Read-only state used by the audio investigation;
- `PlaybackViewModel.kt` (702) [gain] is a software attenuation applied on top of Android's volume.
- `PlaybackVolumeBoost.kt` (163) A percentage above 100 maps to a logarithmic gain;
- `RequestedItemGuard.kt` (26) Keeps the song the user just tapped as the current one while the player loads the new list.
- `StoneEcoEngine.kt` (140) Lightweight playback policy.
- `StonePlaybackService.kt` (494) Sole owner of playback;
- `StoneWidgetProvider.kt` (109) Home-screen player (4×2):
- `SystemArtworkOverlay.kt` (114) Builds the artwork sent to Android's media controls without changing the artwork embedded in, or next to, the user's media file.
- `TrackTransitions.kt` (120) Song-to-song transitions on Stone's single player (no second decoder):

## app/ui
- `AutomationBridge.kt` (160) Registers the running UI as the automation host (so agents use the same view models as the buttons) and shows the confirmation dialog for actions that
- `ImmersiveSystemBarsController.kt` (416) The single owner of system-bar visibility for Stone's Activity and Compose dialog windows.
- `ShakeDetector.kt` (53) Detects a deliberate shake from linear acceleration, not from the constant pull of gravity.
- `StoneApp.kt` (439) Mini player button:
- `VolumeOverlay.kt` (110) Compact volume card.

## app/ui/components
- `SleepTimerIndicator.kt` (101) Shows the active sleep timer using the sun by day and the moon by night.
- `StoneArtwork.kt` (28)
- `StoneAvatar.kt` (17) The chosen mascot (same as the home-screen icon), or the image the user picked in Personalizado.
- `StoneIcon.kt` (53)

## app/ui/theme
- `LauncherIcon.kt` (69) Stone's mascots.
- `StoneBackdrop.kt` (64) Theme backdrop:
- `StoneLook.kt` (140) Accent colour that replaces Stone's gold everywhere (lines, highlights, active icons).
- `StoneTheme.kt` (106) Accent ("dorado" by default);

## core/
- `HomeLayout.kt` (39) Pure window policy:
- `LyricTimeline.kt` (33) Where playback is inside the lyrics:
- `Lyrics.kt` (106) One word of an enhanced LRC line (`<mm:ss.xx>word`);
- `RefreshPolicy.kt` (21) Event-triggered only.
- `Song.kt` (28) Optional artwork extracted from a Stone package.
