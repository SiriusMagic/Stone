# Índice de migración de Stone

Este documento es el mapa verificable entre Stone 1.3.2 (`frontend/`) y la reescritura
Android (`android-native/`). **Leído** significa que se contrastó el comportamiento original;
**traducido** significa que existe una implementación Kotlin compilable. No significa que ya
haya sido validada en un teléfono real.

## Versiones

| Versión | Alcance | Estado |
| --- | --- | --- |
| 1.3.3 | Inicio, biblioteca, selector SAF, búsqueda, agrupación, portada y mini reproductor | Traducido; APK preview02 disponible |
| 1.3.4 | Reproductor, letras, favorito y fila | Traducido; pendiente validación física |
| 1.3.5 | Herramientas de canción: etiquetas, ecualizador, velocidad, compartir, tono, temporizador, ahorro, ocultar y borrar | Traducido; pendiente validación física |
| 1.3.6 | Favoritos, playlists, historial, vídeos y restauración | Traducido; pendiente validación física |
| 1.4.1 | Persistencia, migración, fila y copias de seguridad | Traducido; pendiente validación física |
| 1.4.2 | Ajustes, temas, idioma español/inglés y preferencias de audio | APK preview generado; paridad aún incompleta |
| 1.4.3 | Siguiente lote de paridad; modos de reproducción y mejoras de biblioteca | En curso; APK al llegar a 30 cambios |
| 1.5.0 | Animación interactiva del pingüino en el inicio | Planeado; ver [`STONE-1.5.0-PLAN.md`](STONE-1.5.0-PLAN.md) |
| 1.6.0 | Optimización continua de RAM, CPU, batería y tamaño | Planeado; ver [`STONE-1.6.0-PERFORMANCE.md`](STONE-1.6.0-PERFORMANCE.md) |

## Archivos ya contrastados

| Fuente Stone 1.3.2 | Destino Android | Estado |
| --- | --- | --- |
| `app/library.tsx`, `src/features/library/LibraryPage.tsx` | `feature/library/LibraryScreen.kt` y `components/*` | Leído y traducido (1.3.3) |
| `src/features/library/local-library.ts` | `data/library/FolderScanner.kt`, `LibraryRepository.kt`, `LibraryCache.kt` | Leído y traducido (1.3.3) |
| `src/components/Artwork.tsx` | `ui/components/StoneArtwork.kt`, `data/library/ArtworkLoader.kt` | Leído y traducido (1.3.3) |
| `src/components/MiniPlayer.tsx` | `feature/player/MiniPlayer.kt` | Leído y traducido (1.3.3) |
| `app/player.tsx` | `feature/player/PlayerScreen.kt`, `PlayerProgress.kt` | Leído; núcleo visual y transporte traducidos (1.3.4) |
| `src/features/player/LyricsChip.tsx` | `LyricsParser`, `PlayerScreen.kt` | Leído y traducido (1.3.4) |
| `src/features/player/LyricsSheet.tsx` | `feature/player/LyricsSheet.kt` | Leído y traducido (1.3.4) |
| `src/features/player/LyricsAddFlow.tsx` | `LyricsSheet.kt`, `LyricsOverrides.kt` | Leído y traducido (1.3.4) |
| `src/features/library/library-lyrics.ts` | `core/LyricsParser`, `FolderScanner.kt` | Leído y traducido (1.3.4) |
| `android/.../StoneMediaMetadataModule.kt` (USLT) | `data/library/EmbeddedLyricsReader.kt` | Leído y traducido (1.3.4) |
| `src/features/player/QueueSheet.tsx`, `QueueRow.tsx` | `feature/player/QueueSheet.kt` | Leído y traducido, con pie fijo corregido (1.3.4) |
| `src/contexts/AudioContext.tsx` (reproducción) | `playback/PlaybackViewModel.kt`, `StonePlaybackService.kt` | Leído parcialmente; reproducción y fila traducidas |

## Pendientes por familia de origen

| Archivos de referencia | Versión prevista | Estado |
| --- | --- | --- |
| `app/player.tsx` (menús avanzados), `RingtoneEditor.tsx`, `EnergySaverScreen.tsx`, `PlaybackToolsContext.tsx` | 1.3.5 | Leídos y traducidos |
| `FavoritesPlaylistScreen.tsx`, `NewPlaylistDialog.tsx`, colecciones e historial | 1.3.6 | Leídos y traducidos |
| `QuickDownload*.tsx`, `local-downloads.ts`, `download-api.ts` | Sin versión asignada | No traducidos por decisión expresa; solo icono/aviso |
| `Hidden*.tsx`, `RecentDeletedModal.tsx`, `DeletedItemActionSheet.tsx` | 1.3.6 | Restauración básica traducida; gestión avanzada pendiente |
| `PlaybackSettingsScreen.tsx`, `NotificationSettingsScreen.tsx`, `ThemeSheet.tsx`, `LanguageSheet.tsx`, `translations.ts` | 1.4.2 | Parcial; consulta la comparación actualizada |
| `app/onboarding/*`, `app/+native-intent.tsx`, `Feedback*.tsx`, `SettingsSheet.tsx` | Futuro lote | Pendientes; consulta la comparación actualizada |
| `src/native/StoneRingtone.ts`, `StoneShare.ts`, módulos Android de etiquetas/ecualizador | 1.3.5 | Leídos y traducidos; volumen avanzado pendiente |

## Regla de control

La referencia única es Stone 1.3.2. Lica Player y Sren no se usan como fuente. Cada lote debe
compilar, ejecutar pruebas, producir APK instalable y actualizar este índice. El paquete de
prueba continúa separado para no sobrescribir Stone ni sus datos.
