# Stone Kotlin 1.7.4

- Arreglo: la 1.7.3 se cerraba al reproducir música. Se vuelve al motor de reproducción probado
  (Media3 1.6.1) y al SDK objetivo 35; el resto de actualizaciones se mantiene.
- Si Stone vuelve a cerrarse, el error queda guardado en Ajustes › Sistema › Diagnóstico para copiarlo
  o compartirlo.
- El icono de luna del volumen sale solo en Dormilón (antes aparecía en cualquier modo con ganancia baja).
- Dormilón: volumen máximo 10 % y ganancia suave (80 %). Cine al tope (300 %, ganancia 100 %) y Ahorro de
  batería al 100 % sin cambios. Los iconos de cada modo son los mismos en toda la app.
- Ajustes: el gesto de volver (deslizar desde el borde) regresa al menú de Ajustes en vez de cerrarlo.
- Reproductor: deslizar hacia arriba desde la zona de Letras abre las letras; desde el centro, las
  recomendaciones.

# Stone Kotlin 1.7.3

- Dependencias al día: Android Gradle Plugin 8.13.2, Kotlin 2.4.21, SDK 36, Compose BOM 2026.06.01,
  Activity 1.13, Lifecycle 2.10, Media3 1.11.1, coroutines 1.11, OkHttp 5.3.2. (Lo siguiente, Compose
  1.12 y Lifecycle 2.11, exige SDK 37 y AGP 9.1: queda para otra migración.)
- MCP más seguro: tokens `stn_` de 256 bits con caducidad opcional, bloqueo temporal (429) tras
  intentos con token incorrecto, límite de cabeceras y respuestas sin caché.
- Código reorganizado en archivos pequeños (Ajustes, reproductor de vídeo, biblioteca) y mapa
  `android-native/MAP.md` para trabajar leyendo solo lo necesario.
- Vídeo en horizontal rediseñado: título con HD, subtítulos, lista y menú; accesos con texto a los
  lados (Girar, Tamaño | Repetir, Reproducción en segundo plano, Mudo); barra de tiempo y fila
  candado · anterior · −10 · pausa · +10 · siguiente · velocidad · ventana flotante. Panel lateral
  con la lista de vídeos y menú lateral con temporizador, modo nocturno, brillo, volumen,
  ecualizador, audio, propiedades y compartir. Bloqueo de pantalla y modo nocturno.
- Vídeo en vertical: carrusel de botones redondos (los elegidos en Ajustes › Vídeo, más bloqueo y
  modo nocturno), velocidad junto a la barra y fila ventana flotante · anterior · pausa ·
  siguiente · pantalla completa. Al cerrar el reproductor, la rotación vuelve a ser automática.
- Niveles de audio rediseñado: tarjetas Dormilón / Balanceado / Cine / Personalizado (tocar una
  la activa), etiqueta "Activo", volumen máximo y ganancia con su valor, y restablecer arriba.
- Ajustes ocupa toda la pantalla (antes dejaba un hueco abajo).
- Editor de etiquetas: "Completar automáticamente" busca la canción en el catálogo público de iTunes
  y rellena nombre, artista, álbum, género y portada (se guarda solo al pulsar Guardar). MCP:
  `metadata_suggest_tags`.
- Ajustes reordenados por secciones: Biblioteca, Audio, Vídeo, Notificaciones, Temas y
  personalización, Sistema y Ayuda. Tema y Personalizado son ahora una sola página, con los temas
  en miniatura arriba.
- Notificaciones en su propia sección: avisos de canciones y/o vídeos nuevos, al momento o en un
  resumen diario a la hora que elijas, y un recordatorio musical diario a su propia hora. Los
  avisos programados se restablecen tras reiniciar el teléfono.
- Letras: deslizar hacia arriba en el reproductor las abre. Se abren a pantalla completa (antes a
  media altura), con el color de la portada, la canción arriba y la línea actual marcada según el
  tiempo; editar, borrar y "seguir la canción" pasan a una cápsula flotante abajo a la derecha.
- Letras vacías: se quita la lupa (navegador); "Buscar automáticamente" pasa a ser el botón
  principal, en amarillo. Su diálogo sigue ofreciendo "Buscar en la web" si no encuentra la canción.

# Stone Kotlin 1.7.2

- Letrero "LETRAS" y lista "Con letras": ahora cuentan las letras guardadas en Stone (a mano, de
  lrclib.net o del navegador) y las incrustadas en los archivos del escaneo del dispositivo, que se
  leen en segundo plano una sola vez. El letrero también aparece en la Fila.

- Forma de portada (Ajustes › Personalizado y MCP `appearance_set_artwork_shape`): redonda que gira
  mientras suena, cuadrada, redonda sin girar o pantalla completa. El mini reproductor también la
  usa y su botón de pausa tiene un anillo que se llena con el avance de la canción.
- Tipo de letra: Romana (por defecto) o Cursiva para toda la app (MCP `appearance_set_font_style`).
- Pestaña Géneros con tarjetas de color; el género se lee de MediaStore (Android 11+) y de las
  etiquetas de las carpetas, entra en la búsqueda y en `library_browse` (`kind: genres`).
- Widget de pantalla de inicio (4×2) con portada, título, anterior / reproducir / siguiente / me
  gusta; Ajustes › Widgets lo añade con un toque.
- Tema: cargar imagen de fondo y elegir el color de acento desde la propia página.
- Deslizar hacia arriba el mini reproductor abre "Recomendaciones para ti" (mismo artista, género
  o álbum, favoritas y más escuchadas).

# Stone Kotlin 1.7.1

- Navegador de letras en su propia ventana a pantalla completa (dentro de la hoja se veía en
  blanco), con barra de carga y aviso si la página no carga.
- Notificación de archivos nuevos: ahora funciona (antes el interruptor no hacía nada); pide el
  permiso de notificaciones en Android 13+.
- Biblioteca: se quitan las pestañas Películas y Shorts (todos los vídeos van a Vídeos) y hay un
  separador entre Vídeos y Canciones.
- Iconos de inicio: se quitan Mezclar todo, Favoritos y Ecualizador.
- Personalizado: paleta de colores táctil; la foto de fondo tiene opacidad, modo Fondo de pantalla
  o Foco y vista previa.
- Playlists en tarjetas (Nueva playlist, Canciones favoritas, Recientemente reproducido, Más
  reproducido, Con letras) con portada, número y las dos primeras canciones; mantener pulsado
  cambia la portada.
- Mini reproductor: los tres puntos pasan a ser el modo de reproducción (Repetir todo → Repetir una
  canción → Mezclar todo).
- Fila: modos excluyentes, título "Repetir una canción" con la canción fijada arriba y el resto
  atenuado, texto oscuro sobre portadas claras y álbum junto al artista.
- Política de privacidad, términos y licencias actualizados (tokens, copias, lrclib.net,
  navegador de letras, acceso a todos los archivos, notificaciones).

# Stone Kotlin 1.7.0

- Letras: "Buscar automáticamente" (lrclib.net) muestra resultados con su porcentaje de
  coincidencia (título, artista y duración) y abre el elegido en el editor, sincronizado si existe.
- Navegador de letras: chips Google, Letras.com, Genius y Musixmatch. "Copiar" detecta la letra
  (también la parte oculta tras "Más" en Google), quita créditos como "Fuente: Musixmatch" o
  "Compositores" y vuelve al editor con la letra puesta.
- Modo API: `metadata_search_lyrics` (candidatos con confianza) y `metadata_fetch_lyrics` empareja
  cada resultado con su canción, guarda solo los seguros (`minConfidence`, 0.75 por defecto),
  devuelve `LOW_CONFIDENCE` con candidatos para el resto y acepta `choices` para guardar los elegidos.

# Stone Kotlin 1.6.9

- Modo API rediseñado en pestañas: General, Herramientas, Seguridad, Enlace y Actividad.
- Modos de acceso: Solo lectura, Lectura y escritura (solo lo crítico, como borrar una lista, pide
  aprobación) y Acceso completo (nada pregunta). Varios tokens, cada uno con nombre y modo propio;
  se pueden rotar o revocar por separado.
- Copia de seguridad antes de cada edición de etiquetas, letras o portadas: `metadata_history` y
  `metadata_undo` (también "Deshacer la última edición" en Seguridad).
- `dryRun` en todas las herramientas de edición; errores con código estable
  (`structuredContent.error.code`); resumen y barra de progreso en las operaciones por lote.
- Nuevas: `metadata_set_lyrics_many`, `metadata_fetch_lyrics` (lrclib.net) y `metadata_rename`
  (plantillas `{artist} - {title}`, `{n:2}`, limpieza de nombres técnicos de vídeo, regex).
- Android: un solo diálogo del sistema por lote y opción "Acceso a todos los archivos" para que
  no vuelva a preguntar.

# Stone Kotlin 1.6.8

- Modo API: casilla por herramienta en Opciones avanzadas (agrupadas por categoría, con
  "Marcar todas" / "Desmarcar todas"). Las desmarcadas desaparecen para el agente.
- Modo API: nuevas herramientas `library_set_favorite` (me gusta a cualquier canción o vídeo),
  `playlist_rename`, `playlist_delete`, `audio_get_levels`, `audio_set_level`,
  `audio_reset_levels`, `settings_get`, `settings_set_transitions` (sin pausas / crossfade),
  `settings_set_hardware_acceleration`, `settings_set_play_with_others` y `settings_set_option`.

- Modo API (MCP): permisos del enlace en Opciones avanzadas: "Solo lectura" (solo consultas) y
  "Permitir edición (escritura)". `tools/list` muestra solo lo permitido y las llamadas
  bloqueadas devuelven el motivo.
- Modo API: `metadata_get` y `metadata_update` ahora sirven para canciones y vídeos (título,
  artista, álbum, género; en vídeos también descripción, fecha y palabras clave) y aceptan una
  portada externa por URL (`coverUrl`). Nuevas `metadata_update_many` (muchos cambios con una
  sola aprobación) y `metadata_set_lyrics` (guardar o borrar letras, texto o LRC).

# Stone Kotlin 1.6.7

- Panel de volumen: barrita discreta (36 × 4 dp, gris claro) bajo el volumen, se cierra al
  deslizar hacia arriba y aparece/desaparece en 150 ms.
- Listas: elegir una portada desde la galería ahora sí la cambia (el diálogo olvidaba la lista
  al abrir la galería y la portada anterior quedaba en caché).
- Inicio: la barra de búsqueda queda fija bajo los iconos y se retira la lupa. Nuevos accesos
  opcionales en Ajustes › Accesos de inicio: Mezclar todo, Favoritos, Ecualizador, Temporizador
  y Modo API.
- Video: el carrusel de controles suma Retroceder 10 s, Avanzar 10 s, Repetir, Subtítulos,
  Ventana flotante y Silenciar (se ordenan y ocultan en Ajustes › Vídeo).
- Sobre Stone: "Calificarnos" abre el repositorio en GitHub, y Política de privacidad, Términos
  de servicio y Licencias de código abierto muestran su texto (español e inglés).
- Modo API (MCP): 26 herramientas nuevas (cola, aleatorio/repetir, favoritos, ecualizador por
  banda y preajustes, brillo/escala/orientación de video, ordenar y explorar la biblioteca,
  letras, canciones de una lista) y `stone_help` para buscarlas por intención.
- Modo API, conexión: un GET al enlace ya no devuelve solo "405": responde una guía con la lista
  de herramientas. Para agentes que no hablan MCP hay `GET /tools`, `GET /call/HERRAMIENTA` y
  `GET /health` (sin token). Los errores distinguen token ausente, token incorrecto y
  herramienta desconocida. Guía en `docs/MCP.md`.

# Stone Kotlin 1.6.6

- Los botones físicos de volumen vuelven a mostrar el panel de volumen de Stone dentro de la
  app; ya no aparece el panel de Android. Cada pulsación mueve un nivel de Android hasta el
  100 % y, por encima, 10 % de amplificación hasta el tope del modo.

# Stone Kotlin 1.6.5

- Corrige el enlace público del Modo API en el teléfono: Android bloqueaba la llamada interna de
  Stone a su propio servidor ("Cleartext HTTP traffic to 127.0.0.1 not permitted") y el agente
  recibía un error 502. El relevo ahora habla con el servidor local por un socket directo, y
  "Probar conexión" tiene permitido HTTP solo hacia el propio teléfono.

# Stone Kotlin 1.6.4

- El enlace del Modo API ya es público: el relevo está publicado y su dirección viene dentro de
  la app (`stoneRelayUrl` en `android-native/gradle.properties`). "Copiar enlace" entrega un
  enlace `https://…` que Claude y ChatGPT aceptan; ya no sale `127.0.0.1`.
- Comprobado contra el relevo publicado: inicio de sesión MCP, lista de 27 herramientas, una
  llamada real y rechazo de un token incorrecto.

# Stone Kotlin 1.6.3

- Modo API en dos toques: "Activar MCP" y "Copiar enlace". El enlace es fijo, lleva el token
  dentro y se pega en Claude o ChatGPT (Conectores › Añadir conector personalizado). "Cambiar
  enlace" lo revoca. Lo técnico (red local, puerto, prueba, configuraciones) queda en "Opciones
  avanzadas".
- Relevo propio (`relay/`, Cloudflare Workers): el teléfono se conecta hacia fuera, así que
  funciona en cualquier Wi-Fi o con datos, sin computadora. Se publica una vez y su dirección se
  pone al compilar (`-PstoneRelayUrl=…`); sin ella, el modo API funciona solo en el teléfono o la Wi-Fi.
- Comprobado de punta a punta con el SDK oficial de MCP a través del relevo.

# Stone Kotlin 1.6.2

- Corrige que Stone no se abriera tras actualizar (el lanzador llevaba a los ajustes de Android):
  si en la 1.6.0 se eligió un icono de color, la 1.6.1 quedaba sin ninguna entrada de inicio
  activa. Las entradas de inicio tienen nombres nuevos y el pingüino vuelve a estar activo.

# Stone Kotlin 1.6.1

- Modo API más compatible: token por cabecera, `X-Api-Key` o `?token=` en el enlace; CORS para
  clientes en navegador; transporte SSE antiguo (`/sse` + `/messages`) además de `POST /mcp`;
  puerto configurable; cada respuesta se identifica con `X-Stone-Server`. Botón "Probar conexión"
  que distingue si responde Stone u otro programa en el puerto, y botones para copiar el enlace con
  token, el enlace SSE, la configuración JSON y el comando de Claude Code. Comprobado con el SDK
  oficial de MCP por los tres caminos.
- Bienvenida en el primer inicio: "Elige tu idioma" y Empezar; después los permisos de música y
  vídeos (el escaneo arranca en cuanto se conceden, con la biblioteca cargándose debajo) y de
  notificaciones. Quien ya usaba Stone no la ve.
- Idiomas: portugués, francés, italiano y alemán completos, además de español e inglés. El primer
  inicio usa el idioma del teléfono si Stone lo tiene.
- Mascotas: pingüino, panda, koala y zorro. La elegida en Personalizado se ve dentro de Stone y
  como icono de la pantalla de inicio (icono adaptativo, con versión monocroma para temas).
- Reproducción sin pausas y Crossfade funcionan: sin pausas salta el silencio al principio y al
  final de cada canción; crossfade baja y sube el volumen entre canciones (1 a 12 s). Ambos en
  Ajustes de reproducción.
- Ajustes: Vídeo y Controles de vídeo están en una sola sección.
- Selector de vista (compacta, lista, grande): solo iconos.
- Opiniones y sugerencias rediseñado: tarjetas de tipo, contador de caracteres, botón punteado
  para capturas y envío según el tipo.
- Personalizado: acento libre ("Mío") con deslizadores de tono, saturación y brillo o código hex;
  colores naranja y turquesa nuevos; icono de inicio en 11 colores (el pingüino sobre fondo de
  color, cambia en la pantalla de inicio y en el cajón de apps).
- Temporizador para dormir: cuenta regresiva en vivo, botones +5 y +15 min, presets 15/45/90,
  "Al terminar esta canción", bajada gradual de volumen en el último medio minuto y memoria del
  último tiempo personalizado y de los interruptores.
- Modo API: herramientas `appearance_set_look`, `appearance_reset_look`, `player_sleep_timer` y
  `player_sleep_timer_extend`; `appearance_get` devuelve toda la personalización.
- Plan de nuevas funciones de controles de vídeo en `docs/STONE-VIDEO-CONTROLES-PLAN.md`.
- Ajustes > Sistema > Modo API (agentes de IA): servidor MCP opcional, apagado por defecto, con
  token Bearer, solo loopback salvo que se permita la red local; herramientas de reproducción,
  biblioteca, listas, etiquetas (con confirmación en pantalla), tema, acento, idioma y modo de audio.
- Biblioteca: mantener pulsado selecciona (canciones, vídeos y shorts) con "Seleccionar todos",
  lápiz para editar etiquetas y acciones Agregar a, Reproducir a continuación, Compartir y Borrar.
- Vídeo: mantener pulsado reproduce a 2.0X mientras se mantiene el dedo.
- Editores de etiquetas a pantalla completa: sugerencia desde el nombre del archivo, portada al tocar,
  ruta del archivo; en vídeo además duración, tamaño y "Más detalles".
- Ajustes: "Salida de audio" y "Notificaciones" quedan juntos en Reproducción.
- Modo Dormilón: volumen máximo al 10 % (-20 dB) para escuchar en la cama sin molestar.
- Editores de etiquetas: escriben en el archivo original (MP3, FLAC, M4A, MP4) con portada,
  y Android vuelve a indexarlo para que otros reproductores vean el cambio.
- Vídeos: pestañas Shorts (< 1:30) y Películas (> 1 h); controles nuevos (barra superior con
  velocidad y ⋮, acciones, progreso y transporte; botón de rotar) y menú ⋮ de vídeo con
  escala, modo de reproducción, subtítulos y pista de audio (panel lateral en horizontal).
- Audio horizontal: disco de vinilo que gira; girarlo con el dedo avanza o retrocede la canción.
- La Fila y el menú ⋮ toman el color de la portada de la canción.
- Carpetas: encabezado con número de carpetas y botón para añadir carpeta.
- Sonido: offload real al DSP en modo Ahorro, salida en coma flotante para PCM de alta resolución,
  la salida sigue siempre la ruta activa (auriculares/Bluetooth/USB) y los diagnósticos de audio ya
  no se ejecutan en las versiones optimizadas.
- Biblioteca: escaneo de todo el dispositivo con el índice multimedia de Android (permiso pedido en
  el primer inicio y en Ajustes > Escanear todo el dispositivo). Las carpetas elegidas siguen igual.
- Papelera: eliminar para siempre una canción o vaciar la papelera (Android confirma el borrado de
  archivos del índice multimedia).
- Editor de vídeo: escritura MP4 en streaming en carpetas SAF y archivos locales, sin límite de
  64 MB; corrige los offsets `stco/co64` cuando `moov` va antes de `mdat`; añade álbum y etiquetas.
- Reportar un error: al agitar el teléfono se adjunta automáticamente una captura de la pantalla
  actual, con miniatura y botón para quitarla; descripción de hasta 2000 caracteres.
- Diseño: temas Medianoche y AMOLED (negro puro), textura sutil de fondo.
- Correcciones: gesto de volumen en vídeo, modo inmersivo que quedaba con barras visibles, menú de
  opciones a media pantalla en horizontal, ⋮ junto a los auriculares en vídeo a pantalla completa,
  sin "Ventana flotante" al reproducir solo audio. Se retira "Diseño horizontal" de Ajustes.

# Stone 1.3.3

- La biblioteca ya no se actualiza mediante comprobaciones recurrentes.
- El escaneo automático ocurre al abrir o volver a la aplicación, como máximo una vez cada 15 minutos.
- El botón de escaneo manual fuerza la actualización.
- Se añade una política Kotlin reutilizable para coordinar actualizaciones nativas sin tareas en segundo plano.
