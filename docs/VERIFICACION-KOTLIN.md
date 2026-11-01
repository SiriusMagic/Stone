# Verificación de la primera versión Kotlin

Fecha: 31 de octubre de 2026.

## Stone Kotlin 1.3.6: biblioteca personal y vídeos

- Catorce pruebas completadas sin fallos: seis de núcleo y ocho de aplicación.
- Las pruebas nuevas verifican creación y reapertura de playlists, incorporación de canciones,
  historial, conteos y restauración lógica sin tocar archivos físicos.
- `:app:lintDebug`: 0 errores y 39 advertencias no bloqueantes.
- `:app:assemblePreview`: APK optimizado de 2.967.173 bytes.
- Identidad comprobada: paquete `com.zirakusa.stone.kotlin.preview`, `versionCode 5`,
  `versionName 1.3.6`, Android mínimo 8.0.
- Firma APK v2 verificada con el certificado de las previews anteriores:
  `f055a099c9fb0965bfc4f82ff32ab076fb8f6359a3d986bc90dccb93728c540b`.
- Requieren dispositivo: escaneo SAF de vídeos reales, superficie de vídeo, aceleración del
  códec disponible, reproducción en segundo plano y persistencia tras cierre forzado.

Ver [STONE-1.3.6.md](STONE-1.3.6.md) e [INDICE-MIGRACION.md](INDICE-MIGRACION.md).

## Stone Kotlin 1.3.5: herramientas de canción

- Doce pruebas completadas sin fallos: seis de núcleo y seis de aplicación.
- La prueba nueva confirma que ocultar y borrar son estados lógicos duraderos y no eliminan
  el archivo físico.
- `:app:lintDebug`: 0 errores y 31 advertencias no bloqueantes.
- `:app:assemblePreview`: APK optimizado de 2.512.288 bytes.
- Identidad comprobada: paquete `com.zirakusa.stone.kotlin.preview`, `versionCode 4`,
  `versionName 1.3.5`, Android mínimo 8.0.
- Firma APK v2 verificada con el mismo certificado SHA-256 de las previews anteriores:
  `f055a099c9fb0965bfc4f82ff32ab076fb8f6359a3d986bc90dccb93728c540b`.
- Requieren validación física: efecto audible del ecualizador, temporizador largo, selector
  de compartir, escritura ID3 según el proveedor SAF y permiso/recorte del tono de llamada.

Ver [STONE-1.3.5.md](STONE-1.3.5.md) e [INDICE-MIGRACION.md](INDICE-MIGRACION.md).

## Stone Kotlin 1.3.4: reproductor, letras y fila

- Once pruebas completadas sin fallos: seis de núcleo y cinco de interfaz.
- Parser LRC verificado con desplazamiento, fracciones, varias marcas por línea y búsqueda
  de la línea activa.
- `:app:lintDebug`: 0 errores y 19 advertencias no bloqueantes.
- `:app:assemblePreview`: APK optimizado de 2.463.560 bytes.
- Identidad comprobada: paquete `com.zirakusa.stone.kotlin.preview`, `versionCode 3`,
  `versionName 1.3.4`, Android mínimo 8.0.
- Firma APK v2 verificada. Certificado SHA-256:
  `f055a099c9fb0965bfc4f82ff32ab076fb8f6359a3d986bc90dccb93728c540b`, el mismo de
  las previews anteriores, por lo que se puede actualizar la prueba sin desinstalarla.
- Sigue pendiente comprobar en teléfono: lectura SAF de letras hermanas, metadatos USLT,
  autodesplazamiento durante una canción real, reordenamiento largo y controles de fila.

Ver [STONE-1.3.4.md](STONE-1.3.4.md) e [INDICE-MIGRACION.md](INDICE-MIGRACION.md).

## Revisión preview02: inicio de Stone

- Cinco pruebas de interfaz Compose/Robolectric completadas sin fallos: estado vacío,
  búsqueda y cola, aviso de descargas pendientes, controles del mini reproductor y pantalla de 360 dp.
- Las cinco pruebas de reglas de biblioteca siguen pasando (10 pruebas en total).
- Capturas del inicio vacío, biblioteca cargada y mini reproductor generadas desde Kotlin y revisadas.
- Revisión Android: 0 errores, 16 advertencias (dependencias, recursos y recomendaciones existentes).
- Paquete de prueba y firma iguales a preview01; `versionCode` aumenta de 1 a 2.
- El usuario confirmó que preview01 funciona en su teléfono. La revisión preview02 todavía
  requiere esa comprobación física; las pruebas de esta revisión se ejecutaron en Robolectric.

Ver [INICIO-PREVIEW02.md](INICIO-PREVIEW02.md) para los cambios y funciones pendientes.

## Comprobaciones de preview01

- `:core:test`: 5 pruebas, 0 fallos. Cubren exclusión de escaneos simultáneos,
  reanudaciones dentro del intervalo mínimo, actualización manual, reintentos y huellas de archivos.
- `:app:assembleDebug`: compilación Android completada.
- `:app:assemblePreview`: APK optimizado generado, aproximadamente 2 MB; firma APK v2 verificada.
  Es una versión parcial: su tamaño no representa todavía el de Stone con todas las funciones.
  Requiere Android 8.0 o posterior y se instala como `com.zirakusa.stone.kotlin.preview`.
- `:app:lintDebug`: 0 errores y 13 advertencias. Son avisos de versiones de dependencias,
  servicio de sesión multimedia exportado, política de copias, icono heredado, un texto
  sin usar y sugerencias de sintaxis Kotlin. No se han suprimido los avisos.
- TypeScript de la referencia conservada: pasa. Su comando completo `npm run check`
  falla por los límites de longitud de archivos existentes; estos archivos no se modificaron
  durante el inicio de la migración Kotlin.

## Límites de esta verificación

No se ha ejecutado la app en un teléfono ni un emulador. Compilar no verifica por sí solo
el selector SAF, los controles Bluetooth, las notificaciones ni el consumo de batería.
El registro `MIGRACION-KOTLIN.md` contiene las pruebas manuales y las funciones pendientes.
No se ha publicado un repositorio nuevo ni una release en GitHub.
