# Stone Kotlin 1.6.0

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
