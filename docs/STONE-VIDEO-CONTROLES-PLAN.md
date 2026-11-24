# Plan: más funciones en los controles de vídeo

Estado: propuesta, pendiente de aprobación. Nada de esto está implementado.

## Dónde vive hoy el reproductor de vídeo

| Zona | Archivo |
| --- | --- |
| Barra superior (atrás, velocidad, ⋮) | `android-native/app/src/main/java/com/zirakusa/stone/nativeapp/feature/player/VideoPlayerChrome.kt` |
| Fila de iconos configurable | `feature/player/VideoControlRow.kt` + registro en `VideoControlPreferences.kt` |
| Ajustes › Controles de vídeo (orden y visibilidad) | `feature/settings/VideoControlSettings.kt` |
| Menú ⋮ (escala, repetición, subtítulos, pista de audio, lista, info, borrar) | `feature/player/VideoOptionsSheet.kt` |
| Gestos (brillo, volumen, avance, 2x manteniendo) | `feature/player/PlayerScreen.kt` y `PlayerToolsSheets.kt` |
| Preferencias de vídeo (escala, aceleración) | `feature/player/VideoPreferences.kt` |

Para añadir un control nuevo basta con: 1) un valor en `VideoControlId`, 2) su definición en
`VideoControlRegistry`, 3) el callback en `VideoControlRow`, 4) la acción real en
`VideoPlayerChrome`. Ajustes lo muestra solo, porque recorre el registro.

## Funciones propuestas, de más fácil a más compleja

### Fase 1 (rápidas, sin dependencias nuevas)

1. **Captura de fotograma**: botón que guarda el cuadro actual en `Pictures/Stone` y lo
   comparte. Usa `MediaMetadataRetriever.getFrameAtTime` con la posición del reproductor.
2. **Bloqueo de pantalla**: icono candado que ignora toques y gestos hasta que se mantenga
   pulsado 1 s. Evita pausas accidentales viendo acostado. Estado en `PlayerSheetsState`.
3. **Saltos ±10 s como iconos**: hoy solo existen por gesto (doble toque). Añadir al registro
   para quien prefiera botones; segundos configurables (5/10/15/30) en Ajustes › Controles.
4. **Repetir fragmento (A‑B)**: dos toques fijan A y B; el reproductor vuelve a A al llegar a B.
   Implementación en `PlaybackViewModel` con un `Job` que vigila `currentPosition`, igual que
   el temporizador. Útil para estudiar música o idiomas.
5. **Silenciar**: alterna volumen 0 y el anterior; reutiliza `setVolumePercent`.

### Fase 2 (medianas)

6. **Velocidad por mantener configurable**: hoy es 2.0X fijo; ofrecer 1.5X / 2X / 3X en
   Ajustes › Controles de vídeo. Cambio en `PlayerScreen` donde se detecta el gesto largo.
7. **Zoom con pellizco**: `transformable` sobre el `PlayerView`, escala 1x‑3x y arrastre;
   doble toque vuelve a 1x. Se guarda por sesión, no por archivo.
8. **Marcadores de tiempo por vídeo**: lista de posiciones con nombre guardada en
   `StoneDatabase` (clave `video.bookmarks.<id>`), accesible desde ⋮ y con icono opcional en
   la fila. Saltar, renombrar, borrar.
9. **Continuar donde quedó**: recordar la posición de cada vídeo largo (> 10 min) y ofrecer
   "Seguir desde 12:34" al abrirlo. Ya existe persistencia de cola; falta por vídeo.
10. **Pista de audio y subtítulos en la fila**: hoy están en ⋮; promoverlos a iconos
    opcionales del registro para películas con varias pistas.

### Fase 3 (grandes)

11. **Subtítulos externos (.srt / .vtt)**: buscar un archivo hermano con el mismo nombre al
    cargar el vídeo y añadirlo como `SubtitleConfiguration`. Ajustes de tamaño y fondo.
12. **Retraso de audio/subtítulos**: deslizador ±5 s. Media3 no lo expone directo; requiere
    un `Renderer` propio o reconstruir la pista con desplazamiento. Evaluar coste.
13. **Ecualizador en vídeo**: el ecualizador de audio ya existe; falta mostrarlo en el menú ⋮
    y respetar el modo de audio actual.
14. **Reproducción en segundo plano con imagen flotante mejorada**: PiP ya existe; añadir
    controles de salto y cerrar desde la ventana flotante (`RemoteAction` en `PictureInPictureParams`).

## Exposición al Modo MCP

Cada control nuevo debería tener su acción en `automation/StoneActions.kt` (o un archivo
`StoneVideoActions.kt` si crece): `video.screenshot`, `video.set_ab_loop`, `video.bookmark_add`,
`video.set_zoom`, con riesgo REVERSIBLE salvo las que escriben archivos (captura, marcadores:
PERSISTENT con confirmación).

## Orden sugerido de entrega

1. Fase 1 completa en una sola versión (1.6.1): son cinco controles pequeños que caben en la
   fila configurable y se prueban en el emulador.
2. Fase 2 en dos entregas: (6, 7) y después (8, 9, 10), porque los marcadores y "continuar"
   comparten almacenamiento.
3. Fase 3 según prioridad del usuario; subtítulos externos primero.

## Reglas al implementar

- Cada archivo visual cerca de 100 líneas; dividir antes de 150 (`VideoPlayerChrome.kt`
  ya tiene 140: el siguiente control nuevo obliga a sacar `Transport` y `RoundButton` a
  `VideoTransport.kt`).
- Textos en `res/values/strings.xml` y `values-en/strings.xml`.
- Un solo servicio de reproducción; el bucle A‑B y "continuar" viven en el ViewModel.
- Validar con `./gradlew :app:testDebugUnitTest :app:lintDebug` con 0 avisos.
