# Trabajo sobre Stone Kotlin

- Referencia exclusiva: Stone en `../frontend/`, sus recursos y `../backend/`.
- Leer `README.md` y `../docs/MIGRACION-KOTLIN.md` antes de trasladar funciones.
- Organizar por responsabilidad: core, data, playback, feature, ui. Evitar archivos monolíticos.
- Texto visible en `res/values/strings.xml`; colores compartidos en `ui/theme`.
- Preservar el identificador de prueba hasta completar equivalencia y migración de datos.
- Servicio único de reproducción. No iniciar escaneos desde un temporizador periódico.
- Conservar el índice anterior si un escaneo falla o se cancela.
- Pruebas: `./gradlew :core:test :app:testDebugUnitTest :app:assemblePreview :app:lintDebug`.
- Comparar las capturas de `app/build/screenshots/` con Stone antes de entregar cambios del inicio.
- Documentar funciones pendientes; no sustituir la app original por una prueba incompleta.
