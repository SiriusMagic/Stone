# Trabajo sobre Stone Kotlin

- Para gastar pocos tokens: lee primero `MAP.md` (un renglón por archivo), abre solo el archivo que
  necesitas y, si pasa de ~200 líneas, busca la función con grep y lee ese tramo.
- Archivos visuales cerca de 150-250 líneas; si uno crece, divídelo por tema y vuelve a generar el
  mapa con `python3 tools/update_map.py`.

- Referencia exclusiva: Stone en `../frontend/`, sus recursos y `../backend/`.
- Leer `README.md` y `../docs/MIGRACION-KOTLIN.md` antes de trasladar funciones.
- Carpetas según `ARQUITECTURA.md` (carpeta = package). Evitar archivos monolíticos.
- No dejar código ni ajustes que la app no muestre: si no se usa, se borra.
- Texto visible en `res/values/strings.xml`; colores compartidos en `ui/theme`.
- Preservar el identificador de prueba hasta completar equivalencia y migración de datos.
- Servicio único de reproducción. No iniciar escaneos desde un temporizador periódico.
- Conservar el índice anterior si un escaneo falla o se cancela.
- Pruebas: `./gradlew :core:test :app:testDebugUnitTest :app:assemblePreview :app:lintDebug`.
- Comparar las capturas de `app/build/screenshots/` con Stone antes de entregar cambios del inicio.
- Documentar funciones pendientes; no sustituir la app original por una prueba incompleta.
