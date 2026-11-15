# Stone Kotlin 1.6.0

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
