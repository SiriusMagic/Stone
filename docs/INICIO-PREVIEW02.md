# Stone Kotlin preview02 — recuperar el inicio de Stone

Esta revisión parte de las capturas 119909.jpg (Stone original) y 119910.jpg (primera prueba Kotlin)
aportadas por el propietario. La referencia de comportamiento y medidas es el código de Stone
en `frontend/src/features/library/`, no una plantilla de Material Design.

## Cambios

- Pingüino original, guardado dentro de la app: no necesita red para mostrarse.
- Misma fuente de iconos Ionicons: lupa, carpeta, escaneo, descarga, orden y ajustes.
- Búsqueda oculta inicialmente, visible al tocar la lupa; conserva filtrado y reproducción.
- Pestañas en el orden original: Videos, Canciones, Playlists, Carpetas, Artistas, Álbumes.
  Pastillas de 36 dp, fondo oscuro y selección azul `#339CFF`.
- Estado vacío con nota musical, texto y botones «Elegir carpeta Stone» (morado)
  y «Escanear carpetas» (amarillo), siguiendo medidas y espaciados originales.
- Filas con portada de 72 dp, título y artista/álbum; carpeta, artista y álbum abren su colección.
- Mini reproductor oscuro/dorado con portada, controles y acceso a la cola.
- Ordenación por título, artista, álbum o duración, con dirección ascendente/descendente.
- Portadas cargadas solo para filas visibles: dos lecturas simultáneas como máximo,
  miniaturas de hasta 256 px y caché de 4 MiB.

## Funciones que siguen pendientes

Videos, playlists y descargas conservan su posición en el inicio, pero explican que todavía
no están disponibles en esta prueba. No descargan ni crean listas ficticias. Ajustes permite
seleccionar carpeta y escanear; los demás ajustes y el botón de favoritos del mini reproductor
siguen pendientes. La pantalla de reproducción completa aún requiere equivalencia visual.

La restauración visual del inicio no equivale a una migración funcional completa de Stone.
El registro completo permanece en `MIGRACION-KOTLIN.md`.

## Comprobación y actualización

Se añaden pruebas Compose/Robolectric para el inicio vacío, la búsqueda, la cola enviada al
reproductor, acciones pendientes, controles del mini reproductor y encabezado a 360 dp.
Las capturas se generan a partir de los componentes Kotlin ejecutados en Robolectric;
no son capturas de un teléfono físico ni dibujos externos.

Capturas: [inicio vacío](capturas/stone-home-empty.png),
[biblioteca con canciones](capturas/stone-home-songs.png) y
[mini reproductor](capturas/stone-home-miniplayer.png).

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assemblePreview
```

El paquete se mantiene en `com.zirakusa.stone.kotlin.preview` y sube a `versionCode=2`.
Usando la misma firma de desarrollo, puede actualizar preview01 sin cambiar el formato de
la biblioteca ni su preferencia de carpeta. El paquete original de Stone sigue separado.

Los avisos de licencia de Ionicons y react-native-vector-icons están junto a este documento.
