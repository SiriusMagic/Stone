# Stone Kotlin 1.6.0

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
