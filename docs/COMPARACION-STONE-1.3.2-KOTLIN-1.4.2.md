# Comparación de Stone 1.3.2 con Stone Kotlin 1.4.2

Inventario para continuar la migración más adelante. Es una comparación estática del código; **no certifica equivalencia ni pruebas en un teléfono**.

## Referencias

- Aplicación Kotlin comparada: `android-native/`, versión `1.4.2`, APK de vista previa.
- Código de referencia conservado en `frontend/`; mapa de la app: `frontend/src/features/library/MAP.md`.
- La referencia de producto anotada para Stone 1.3.2 es [`legado/stone` en el repositorio original](https://github.com/SiriusMagic/lica-player/tree/002c1f01f7b8ef636aa24ad07dc40bb2417bb6e0/legado/stone).
- `frontend/` también contiene cambios locales posteriores. Cuando se retome el trabajo, verificar cada diferencia contra ese commit/árbol de 1.3.2 y separar explícitamente lo que pertenezca a 1.3.3.

## Significado de los estados

- **Implementado en código:** existe un recorrido nativo reconocible; falta validarlo en dispositivo y no implica igualdad visual exacta.
- **Parcial:** existe una parte, pero faltan opciones, pantallas, datos o interacciones del original.
- **Pendiente:** no se encontró un flujo equivalente en la app Kotlin actual.
- **Fuera de alcance por ahora:** se dejó intencionalmente sin portar; no debe tratarse como función terminada.

## Inventario de equivalencia

| Área de Stone 1.3.2 | Lo que hace la referencia | Stone Kotlin 1.4.2 | Diferencia / pendiente |
| --- | --- | --- | --- |
| Inicio y bienvenida | Selección de idioma, consentimiento/enlaces y permiso inicial | Abre directamente la biblioteca | **Pendiente:** onboarding, textos legales/enlaces y recorrido de primer uso. |
| Idiomas | Español, inglés, portugués, francés, italiano y alemán (`frontend/src/i18n/translations.ts`) | Español e inglés en recursos Android | **Parcial:** faltan portugués, francés, italiano y alemán; revisar textos incrustados que no usan recursos. |
| Temas | Negro, gris y claro | Negro, gris y claro | **Implementado en código; parcial visual:** cotejar colores de cada componente, especialmente hojas del reproductor que aún usan colores fijos. |
| Encabezado | Buscar, elegir carpeta, escanear, descargas, ordenar y ajustes | Esos accesos están visibles | Descargas solo informa que no están disponibles; los demás requieren validación visual y de accesibilidad. |
| Biblioteca de audio | Canciones y exploración por carpetas/artistas/álbumes | Incluye esas pestañas, búsqueda, agrupación y orden por título/artista/álbum/duración | **Parcial:** el orden del original también incluye recientes/más reproducidas; falta revisar modos de vista y metadatos faltantes. |
| Gestos de biblioteca | Deslizamiento horizontal para cambiar pestañas (`LibraryPage.tsx`) | Pestañas desplazables horizontalmente; no se encontró gesto equivalente sobre la lista | **Pendiente:** navegación por deslizamiento y su comportamiento al desplazarse verticalmente. |
| Filas y acciones de canción | Tocar una fila reproduce; herramientas principales en el reproductor y acciones separadas en ocultos/eliminados | Tocar una fila reproduce; herramientas en el reproductor | **Cercano en flujo básico.** Comparar artwork, dimensiones, truncado y estado visual de pista activa; las acciones de ocultos/eliminados sí son parciales (ver abajo). |
| Videos | Pestaña, navegación/agrupación de carpetas y reproducción | Escáner admite MP4, MKV, MOV y WebM; existe pestaña y reproductor Media3 | **Parcial:** lista principalmente plana; faltan equivalencia de carpetas de video, selector de pistas/subtítulos y validación real de códecs. No se añadió VLC/MX Player. |
| Formatos escaneados | Audio MP3, M4A, AAC, WAV, FLAC, OGG; video MP4, MKV, MOV, WebM (`local-library.ts`) | El escáner Kotlin declara los mismos grupos de extensiones | **Implementado en código**, no garantiza que cada archivo/códec se reproduzca en todos los dispositivos. |
| Playlists y colecciones | Favoritos, recientes, más reproducidas, con letras y listas propias | Colecciones y playlists propias persistentes | **Parcial:** comparar orden, acciones por lote, navegación y casos de listas vacías; probar persistencia/reinicio. |
| Mini reproductor | Portada, título y controles compactos | Portada, título, abrir reproductor/cola y play/pausa | **Parcial visual/interactiva:** comparar gestos, acciones rápidas y estados con Stone original. |
| Reproductor completo | Controles, barra temporal, letras, cola y opciones de reproducción | Reproducción Media3, progreso, anterior/siguiente, play/pausa, favorito y herramientas | **Parcial:** aleatorio/repetición están en la hoja Cola, no como controles visibles equivalentes en la pantalla principal; comparar distribución y gestos. |
| Cola | Reordenar, saltar a pista, aleatorio, repetición y fila | Reordenar manteniendo pulsado, saltar a pista; controles de aleatorio/repetición fijos al pie de la hoja | **Implementado en código** para el flujo observado; pendiente prueba táctil, cola larga y teléfono real. |
| Audio en segundo plano | Sesión, notificación y controles del sistema | Servicio Media3, sesión, audio focus y modo “reproducir junto con otras apps” | **Implementado en código; pendiente dispositivo:** notificación, Bluetooth/auriculares, foco, pantalla apagada y fabricantes Android. |
| Persistencia de reproducción | Preferencias/cola y estado de uso | Cola/posición/modos se guardan al salir de primer plano | **Parcial:** temporizador y todas las opciones del original no se restauran; probar cierre forzado y reinicio. |
| Letras | Letras embebidas/LRC, sincronización, edición y búsqueda | Lee letras disponibles, muestra/resalta y desplaza LRC; permite pegar/editar; búsqueda abre Google | **Parcial:** confirmar formatos embebidos admitidos y comparar proveedor/flujo de búsqueda del original. |
| Ecualizador | Bandas, presets y activación | Diez bandas y presets mediante sesión de audio | **Implementado en código; pendiente dispositivo:** efecto audible, límites y compatibilidad del efecto por fabricante. |
| Velocidad | Control de velocidad de reproducción | Opciones 0.5×–2× | **Implementado en código;** verificar persistencia y audio/video en dispositivo. |
| Temporizador | Presets, tiempo personalizado y opción de terminar la pista actual | Presets 0/10/20/30/60/90 minutos; opción de terminar pista en herramientas del reproductor | **Parcial:** falta tiempo personalizado y revisar si terminar pista y estado del timer se conservan correctamente. |
| Volumen | Control propio y comportamiento de volumen de la app | No se encontró control propio ni amplificación mayor a 100% | **Pendiente.** No confundir con volumen físico del teléfono. |
| Editor de etiquetas | Edita metadatos del archivo | Editor nativo con soporte limitado a MP3 | **Parcial:** faltan formatos adicionales; validar permisos SAF, escritura y preservación de etiquetas/portada. |
| Tono de llamada y compartir | Recortar/establecer tono y compartir archivo | Flujos nativos de tono y compartir | **Implementado en código; pendiente dispositivo:** permisos especiales, proveedores SAF y URI compartida. |
| Ocultos | Audio/video, búsqueda, categorías, orden, acciones y configuración | Lista básica de canciones ocultas y restauración | **Parcial:** faltan pestañas/categorías, búsqueda, ordenar, acciones y ajustes de conversión. |
| Eliminados recientemente | Tipos de medio, selección múltiple y acciones de elemento | Lista básica de canciones eliminadas y restauración | **Parcial:** faltan videos, selección múltiple, borrar permanentemente y opciones contextuales. El movimiento es lógico; no equivale a borrar el archivo físico. |
| Video → audio / accesos a carpetas especiales | Preferencia y flujos para localizar audio | No hay conversión; no se migró configuración de carpetas especiales | **Pendiente.** Algunas filas de configuración del frontend son prototipos sin acción completa; confirmar el alcance real antes de portarlas. |
| Descargas | Descarga rápida, listas y backend | El botón conserva un aviso “no disponible” | **Fuera de alcance por ahora**, según decisión previa. No existe flujo descargador Kotlin. |
| Abrir desde otras apps | Importa URI recibida por Android (`+native-intent.tsx`) | No se halló intent-filter para archivos ni manejo de URI entrante en la actividad | **Pendiente:** filtros Android, permisos URI y prueba de audio compartido desde otras apps. |
| Ajustes | Carpeta, escaneo, tema, idioma, temporizador, ocultos/eliminados, reproducción, notificaciones, feedback y acerca de | Carpeta, escaneo, tema, idioma, reproducción, notificaciones y timer; accesos a ocultos/eliminados | **Parcial:** faltan secciones utilizables de feedback/acerca de y revisar accesibilidad/jerarquía del menú. |
| Ajustes de reproducción | Mezcla con otras apps, gapless y crossfade | Mezcla con otras apps actúa sobre el foco; Media3 maneja gapless | Crossfade no está implementado y se indica como no disponible. El ajuste gapless no tiene interruptor porque Media3 lo gestiona automáticamente. |
| Notificaciones de biblioteca/recomendaciones | Interruptores para nuevos archivos y recomendaciones | No están implementadas | **Pendiente:** notificación local de escaneo; push requiere definir/implementar backend. La UI actual lo informa en vez de fingir que funciona. |
| Ahorro energético | Interfaz mínima y apagado/encendido de pantalla por toque | Pantalla mínima con controles | **Parcial:** no se encontró el apagado automático/gesto de toque equivalente del frontend. |
| Opinión, FAQ y acerca de | Flujos de feedback, preguntas y datos de la app (`Feedback*`) | No hay pantallas nativas; el pie “Opinión / Sobre” no está conectado | **Pendiente.** |
| Copia de seguridad | Exportar/importar estado de usuario | Funciones SQLite/JSON y accesos desde Ajustes Kotlin añadidos al iniciar el lote siguiente | **Parcial:** el formato nativo no importa automáticamente el almacenamiento de la app 1.3.2; necesita conversor/puente probado. |
| Migración de datos instalada | Stone conserva favoritos, listas, historial, ajustes y otros datos en su paquete actual | El preview usa `com.zirakusa.stone.kotlin.preview` y almacenamiento propio | **Bloqueador para reemplazo:** el APK preview no lee los datos privados de Stone 1.3.2. Hace falta un mecanismo de exportación/importación compatible o una actualización firmada con el mismo paquete y migración probada. |
| Publicación / identidad | App instalada `com.zirakusa.stone` | APK preview con package distinto y firma de desarrollo | **No es actualización instalable sobre 1.3.2.** No cambiar package/firma hasta resolver respaldo, firma original, versionado y pruebas. |

## Orden sugerido para la siguiente etapa

1. **Cerrar la lista exacta de paridad** contra el commit de `legado/stone` 1.3.2, marcando aparte cualquier cambio 1.3.3 local.
2. **Importación segura de datos**: definir un backup compatible desde la app original y restaurarlo en Kotlin; probar favoritos, listas, letras, historial, permisos de carpeta y ajustes sin borrar el origen.
3. **Funciones de uso diario que faltan**: menú contextual de canción, opciones de orden/vista, temporizador personalizado y completar ocultos/eliminados.
4. **Completar equivalencia visual/gestual**: gestos de pestañas, mini/reproductor, modo vinyl y estados de biblioteca; comparar capturas en el mismo dispositivo y resolución.
5. **Completar accesos e integración Android**: abrir URI desde otras apps, feedback/acerca de, y accesos visibles a backup.
6. **Cerrar ajustes restantes**: notificaciones reales si se desean; decidir si crossfade merece implementación y con qué comportamiento. Mantener Media3 salvo una decisión explícita distinta.
7. **Traducir los cuatro idiomas restantes** y revisar cadenas hardcodeadas; no anunciar seis idiomas hasta completar todos los flujos.
8. **Prueba de aceptación en teléfono**: SAF, audio/video real, códecs, pantalla apagada, notificación, Bluetooth, permisos, rotación, datos, batería y sesiones largas.
9. Solo tras cerrar los puntos anteriores: validar el cambio al identificador/firma de producción y publicar una actualización que conserve datos.

## No asumir como terminado

- Un APK que compila no demuestra reproducción en todos los formatos ni equivalencia con el original.
- Una función presente en código puede estar desconectada de la interfaz (por ejemplo, backup) o ser una implementación reducida (ocultos/eliminados).
- Los cambios ya guardados en SQLite en el preview no se trasladan por sí solos a la app instalada.
- No migrar descargas ni cambiar el motor de Media3 como parte de este inventario; siguen las decisiones previas.

## Archivos útiles para retomar

- Biblioteca original: `frontend/src/features/library/LibraryPage.tsx` y `frontend/src/features/library/components/`.
- Mapa original: `frontend/src/features/library/MAP.md`.
- Traducciones originales: `frontend/src/i18n/translations.ts`.
- Reproductor original: `frontend/app/player.tsx`, `frontend/src/contexts/AudioContext.tsx` y `frontend/src/features/player/`.
- Biblioteca Kotlin: `android-native/app/src/main/java/com/zirakusa/stone/nativeapp/feature/library/`.
- Reproductor Kotlin: `android-native/app/src/main/java/com/zirakusa/stone/nativeapp/feature/player/` y `playback/`.
- Versiones y criterios anteriores: [`MIGRACION-KOTLIN.md`](MIGRACION-KOTLIN.md) y [`STONE-1.4.2-CAMBIOS.md`](STONE-1.4.2-CAMBIOS.md).
