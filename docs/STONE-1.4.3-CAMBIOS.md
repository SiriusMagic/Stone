# Stone 1.4.3 — contador del lote de migración

La última vista previa es 1.4.2. Esta etapa avanza solo la app nativa y mantiene intacta la app instalada. Se generará el siguiente APK al completar **30 cambios funcionales verificables**; no se crea un APK intermedio.

## Progreso

- **30 de 30 cambios completados.**
- **Cambio 1:** se añadieron a Ajustes los accesos para exportar e importar una copia Stone Kotlin, conectados a los selectores de archivo existentes.
- El formato está rotulado como Stone Kotlin porque todavía **no es compatible con los datos privados de Stone 1.3.2**.
- **Cambio 2:** el reproductor ahora responde al giro natural del teléfono; en horizontal presenta el disco/portada girando durante la reproducción, la letra actual y los controles sin bloquear la orientación del dispositivo.
- **Cambio 3:** la cápsula de letras del reproductor vertical avanza con las marcas de tiempo, anima el paso de línea y abre la hoja de letras al tocar; toma en cuenta las letras personalizadas guardadas.
- **Cambio 4:** elegir una carpeta multimedia nueva ahora la agrega a la biblioteca en vez de sustituir la carpeta de música anterior.
- **Cambio 5:** la caché se separa por carpeta y el escaneo combina los resultados sin duplicar las mismas URI; el índice anterior de una sola carpeta se sigue leyendo para conservar bibliotecas existentes.
- **Cambio 6:** la biblioteca permanece visible durante el escaneo de una carpeta adicional y conserva los resultados previos si una carpeta falla.
- **Cambio 7:** los videos tienen icono y subtítulo de tipo de medio propios; la pestaña vacía de videos ya no muestra mensajes que dicen “canciones”.
- **Cambio 8:** el reproductor de video tiene un diseño horizontal con superficie de video, ajuste de aspecto y controles, en vez de reutilizar el diseño estrecho de audio.
- **Cambio 9:** se añadió a Ajustes la configuración de iconos de inicio; permite mostrar/ocultar búsqueda, carpeta, escaneo, descargas, ordenar, ecualizador y temporizador. Los dos accesos nuevos abren las herramientas existentes y el icono de descargas conserva su aviso.
- **Cambio 10:** al seleccionar una carpeta, se ocultan las llamadas redundantes a elegir/escanear dentro de la lista; esas acciones siguen disponibles en la barra de inicio y en Ajustes, incluso en la pestaña Videos.
- **Cambio 11:** el ecualizador muestra la canción activa solo como contexto, conserva bandas/presets globales (no por pista), y adopta el panel de la referencia: diez faders verticales, interruptor, ocho tarjetas de presets y tema verde azulado.
- **Cambio 12:** el temporizador ofrece apagar, 10/20/30/60 minutos, selector personalizado de horas/minutos y opción para terminar la pista actual; la misma interfaz está disponible desde el reproductor y Ajustes, en español e inglés.
- **Cambio 13:** la fila recupera el icono de edición de la versión original; al activarlo aparecen los controles para reordenar, y “Repetir una” vuelve a distinguirse con el indicador 1. Ir a la pista actual, cerrar, repetir todo y aleatorio permanecen accesibles.
- **Cambio 14:** el reordenamiento de la fila ahora inicia directamente al arrastrar el asa, desplaza la pista con el dedo y anima suavemente los movimientos entre posiciones; se conserva la acción de tocar una canción para reproducirla.
- **Cambio 15:** los controles de repetición y aleatorio ahora también actualizan el orden visible de la fila. Repetir una/todo pone primero la pista activa; aleatorio deja esa pista al inicio y mezcla el resto; al apagar aleatorio vuelve al orden base de la sesión. La cola reordenada y el modo se conservan en la restauración.
- **Cambio 16:** Videos ahora tiene una cuadrícula de dos columnas con fotogramas de vista previa, título, resolución y duración; el icono de carpeta abre la navegación por carpetas de video y cada tarjeta abre la reproducción del video.
- **Cambio 17:** Álbumes ahora usa tarjetas visuales con portada, color de marco, nombre, cantidad de pistas y hasta dos canciones de muestra; al abrir una tarjeta se conserva el listado de pistas del álbum.
- **Cambio 18:** Artistas adopta tarjetas con portada representativa circular, nombre, cantidad de canciones y dos pistas de muestra; tocar una tarjeta abre la lista filtrada del artista.
- **Cambio 19:** el servicio Media3 añade volumen de app de 0–300% con `LoudnessEnhancer`, guarda el nivel y presenta un control propio. Las teclas físicas se capturan mientras hay una cola activa y ajustan Stone sin cambiar el volumen multimedia de Android; fuera de Stone, Android conserva su panel normal.
- **Cambio 20:** se retiró el tema claro, que mostraba texto blanco con fondo claro. Preferencias antiguas `light` se normalizan a `dark`; Negro y Gris mantienen esquemas oscuros legibles y se conserva sin cambios el JPG original del pingüino.
- **Cambio 21:** Ajustes → Ajustes de reproducción contiene una vista inferior de **Modo de reproducción** con explicación, tres opciones tipo radio, descripciones e iconos amarillos. La selección se guarda automáticamente y actualiza el subtítulo; se explicitan las limitaciones del starter: offload no configurado y salida bit-perfect no garantizada.
- **Cambio 22:** se integró `StoneEcoEngine` dentro del servicio Media3 existente: un solo ExoPlayer/sesión, selección de política por modo, detección de salida bajo demanda y fallback seguro. No crea procesos, hilos periódicos ni un segundo decodificador. La capacidad real de offload sigue dependiendo de Android, el formato y el dispositivo, y queda pendiente de diagnóstico físico.
- **Cambio 23:** Ajustes incorpora **Modo de escaneo** con Auto reescanear activado por defecto. El escaneo automático ocurre al iniciar Stone, una sola vez por entrada a primer plano; el escaneo manual continúa disponible desde el icono de escanear. No existe vigilancia continua ni temporizador de escaneo.
- **Cambio 24:** Ajustes de reproducción incorpora **Aceleración de hardware** con Automático, Deshabilitado, Decodificación, Completa y Adaptativo (Eco Engine). La preferencia se guarda y Eco Engine aplica una política diferenciada con fallback; la activación real de offload/decodificación sigue dependiendo de Android, formato y dispositivo.
- **Cambio 25:** Ajustes incorpora accesos visuales con iconos para Opinión y Sobre Stone; la pantalla Sobre muestra el pingüino original, versión, copyright y bloques de calificación, compartir, privacidad, términos y licencias.
- **Cambio 26:** Opinión ahora tiene categorías seleccionables, detalles limitados a 500 caracteres, zona de adjuntos opcionales, consentimiento y botón de entrega preparado. El envío queda desactivado hasta integrar un servicio de soporte, por lo que Stone no comparte datos actualmente.
- **Cambio 27:** Configuración de notificaciones incorpora Recordatorios musicales desactivados por defecto. Al activarlos muestra ventanas de mañana/tarde y reglas de tranquilidad; no programa tareas ni envía datos todavía, y las notificaciones de reproducción permanecen separadas.
- **Cambio 28:** Opinión permite seleccionar hasta cuatro imágenes adjuntas mediante el selector de archivos de Android y muestra el número seleccionado; no las sube ni las comparte.
- **Cambio 29:** Sobre Stone activa Compartir Stone mediante el selector seguro del sistema, sin conexión propia ni servicio permanente.
- **Cambio 30:** Configuración de notificaciones persiste los estados de archivos nuevos y notificaciones push, ambos desactivados por defecto; las funciones no generan actividad hasta disponer de una implementación de notificaciones aprobada.
- **Decisión de alcance:** los modos pertenecen a 1.4.3, no a 1.5.0. El único elemento anotado para 1.5.0 por ahora es la animación del pingüino.

## Alcance restante por elegir del inventario

Los siguientes cambios se tomarán del inventario en [`COMPARACION-STONE-1.3.2-KOTLIN-1.4.2.md`](COMPARACION-STONE-1.3.2-KOTLIN-1.4.2.md), priorizando portabilidad de datos, funciones faltantes y traducción. Se anotará cada cambio aquí con su comportamiento y evidencia; el contador no considera una fila de documentación como cambio funcional.

No cambiar `applicationId`, firma, Media3 ni el alcance de descargas dentro de este lote sin una decisión explícita. La app Stone 1.3.2 sigue siendo la versión de uso diario hasta cerrar equivalencia y migración de datos.
