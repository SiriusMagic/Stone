# Stone — contexto de entrega

Este documento conserva el estado técnico necesario para continuar Stone después de
recrear el entorno de desarrollo.

## Repositorio y ramas

- Repositorio: `https://github.com/SiriusMagic/Stone.git`
- Rama principal de trabajo: `main`
- Rama de respaldo histórica: `stone-current-backup-2026-11-01`
- Rama de migración local publicada: `stone-1.5.0-local-complete`
- El estado que debe usarse para continuar es siempre `origin/main`, salvo que se indique
  expresamente otra rama.
- Para ver el commit exacto de la entrega: `git log -1 --oneline --decorate`.

## Rutas importantes

- Proyecto Android nativo: `android-native/`
- Código Kotlin de la aplicación: `android-native/app/src/main/`
- Núcleo sin Android: `android-native/core/`
- Documentación de diseño horizontal: `android-native/DISENO-HORIZONTAL.md`
- Notas de rendimiento: `android-native/RENDIMIENTO.md` y `docs/STONE-1.6.0-PERFORMANCE.md`
- APK debug rastreado por Git: `android-native/app/build/outputs/apk/debug/app-debug.apk`
- APK optimizado de menor tamaño: `android-native/Stone-1.6.0-optimized.apk`

## Compilación desde un entorno limpio

Requisitos usados en Codex Cloud:

- JDK 21.
- Android SDK con API 35 y Build Tools 35.0.0.
- No se requiere Node, Expo, Metro, ADB ni un teléfono para compilar.

Desde `android-native/`:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`. El APK optimizado
se genera con:

```sh
./gradlew :app:assemblePreview
```

Las carpetas `build/`, `.gradle/`, informes y resultados temporales están ignoradas por Git
y se pueden regenerar. El APK debug indicado arriba sí está rastreado para poder descargarlo
directamente desde GitHub.

## Estado funcional actual

- Existe un único servicio de reproducción Media3: `StonePlaybackService`.
- El volumen base de 0–100 % se sincroniza con `AudioManager.STREAM_MUSIC`; la amplificación
  superior usa el componente de amplificación existente de Stone.
- El modo inmersivo global se controla desde `ImmersiveSystemBarsController`, no desde cada
  pantalla. Incluye biblioteca, playlists, reproductores, vídeos y Ajustes; respeta teclado,
  diálogos y barras transitorias mostradas mediante gesto.
- La biblioteca horizontal y vertical usa las mismas tarjetas de canciones. La canción actual
  queda resaltada con fondo y marco amarillo.
- El reproductor de vídeo oculta o muestra los controles con un toque sobre el vídeo. Se
  eliminaron definitivamente el bloqueo de controles, sus métodos de desbloqueo, la preferencia
  asociada, el botón y la pulsación larga antigua.
- Se conservan los gestos independientes del vídeo para brillo, volumen, avance/retroceso y
  navegación entre canciones.
- Las portadas de playlists, edición de metadatos, reportes, mascota, diagnóstico de audio y
  controles de vídeo permanecen dentro de la app nativa.

## Archivos responsables de las últimas correcciones

- `app/src/main/java/com/zirakusa/stone/nativeapp/ui/ImmersiveSystemBarsController.kt`:
  responsable único de barras del sistema e insets.
- `app/src/main/java/com/zirakusa/stone/nativeapp/feature/library/components/LibrarySongRow.kt`:
  tarjeta reutilizada para canciones verticales y horizontales.
- `app/src/main/java/com/zirakusa/stone/nativeapp/feature/player/PlayerScreen.kt`:
  reproductor de vídeo y toque para visibilidad de controles.
- `app/src/main/java/com/zirakusa/stone/nativeapp/feature/player/LandscapeVideoPreviewPanel.kt`:
  vista previa de vídeo de la biblioteca.
- `app/src/main/java/com/zirakusa/stone/nativeapp/feature/player/VideoControlPreferences.kt`:
  acciones configurables del vídeo, sin acción de bloqueo.
- `app/src/main/java/com/zirakusa/stone/nativeapp/data/persistence/StoneSettings.kt`:
  preferencias persistentes, incluida la política inmersiva global.

## Verificación

La última entrega se verificó con `core:test`, `testDebugUnitTest`, `lintDebug` y
`assembleDebug`. La validación visual final de gestos, barras y audio debe hacerse en un
teléfono Android; este entorno no dispone de un dispositivo físico.

No se deben subir canciones, vídeos, bases de datos del usuario, credenciales ni carpetas de
compilación. El repositorio contiene código, documentación, pruebas y los APK publicados.
