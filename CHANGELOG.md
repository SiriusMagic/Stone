# Stone Kotlin 1.7.20

- **Vuelve el Modo API de antes:** Stone se conecta otra vez directamente a la IA a través del Worker
  `stone-relay` de Cloudflare, con su enlace fijo, sin pasar por Gateway Hub.
- **Vuelven General y Analítica:** Activar MCP, **Crear token**, pausar y configurar cada token, y las gráficas de
  solicitudes por minuto.
- Se quita todo lo de Gateway Hub dentro de Stone (vinculación, paquetes y su tarjeta).
- El reproductor no cambia: se mantiene que las copias de seguridad de otras apps no salgan en la biblioteca.

# Stone Kotlin 1.7.19

- **Ya no hay nada que aprobar:** los permisos que manda Gateway Hub (solo puede mandarlos la app Gateway firmada por
  el mismo desarrollador) se activan solos. Antes había que pulsar **Aprobar** en Stone.
- **Nada pide confirmación en la pantalla**, ni siquiera borrar: si quieres que te pregunten, lo pregunta tu IA
  (Claude y ChatGPT preguntan antes de usar una herramienta). Para cortarlo todo: **Desconectar de Gateway Hub**.
- Necesario para el hub compartido de Gateway Hub 0.0.9 (copiar un enlace, pegarlo en la IA y escribir un código).

## Fase 7

- **Adiós al relay:** se borra la carpeta `relay/` (Worker `stone-relay`). Stone llega a las IA solo a través de
  Gateway Hub. El Worker `stone-relay` que quede en tu Cloudflare ya no se usa y se puede borrar.

# Stone Kotlin 1.7.18

- **Las copias de seguridad automáticas de otras apps ya no aparecen como canciones** («AutoBackup_…» con «All data
  backup», «AutoPlaylist_…» con «Playlist backup»). Stone no las crea: las deja otra app y Android las indexa como audio.
  Stone las oculta siempre; los archivos no se tocan.

## Gateway Hub: fases 2 a 6 (Stone como proveedor)

- **Paquetes de ejecución** en Ajustes › Modo API › Gateway Hub · paquetes: Gateway Hub ofrece permisos firmados
  (leer, revisar, escribir, modificar, revertir) para un agente y un tiempo. Los de solo lectura se activan solos; los
  demás esperan a que pulses **Aprobar**. Puedes **Rechazar** o **Revocar** cualquiera.
- Borrar sigue pidiendo confirmación en la pantalla de Stone, aunque el paquete lo permita.
- El puente con Gateway Hub ya no necesita «Activar MCP» ni «Redirección hub». Para cortarlo: **Desconectar de Gateway
  Hub** (se puede volver a permitir).
- Gateway puede cancelar una petición en curso, por ejemplo una confirmación que nadie ha respondido.
- Fase 3: Stone acepta varios paquetes candidatos por llamada (el hub elige cuáles podrían cubrirla).
- Fase 4: se quita la tarjeta **Cloudflare** de Modo API. Stone ya no guarda ningún token de Cloudflare (el que guardó la
  1.7.15 se borra al abrirla) ni lo comparte con Gateway Hub: el hub se publica y se actualiza desde Gateway, con un token
  que se usa una vez y se olvida.
- **Fase 6: Stone es solo proveedor de Gateway Hub.** Se retiran el servidor MCP HTTP, el enlace público (relay), los
  tokens propios, la Analítica, «Activar MCP», «Redirección hub», «Solo con Stone abierta» y el canal HubLink.
  - Ajustes › **Gateway Hub** (antes Modo API): estado que comunica Gateway, paquetes (aprobar, rechazar, revocar),
    «Desconectar de Gateway Hub» y la actividad de los agentes.
  - El modo seguro lo elige Gateway Hub y Stone lo aplica: en modo seguro solo responde con Stone en pantalla.
  - La herramienta `stone_metrics` desaparece (las métricas están en Gateway Hub). Las demás herramientas no cambian.
  - Los agentes que usaban un enlace de Stone (`stone-relay…/d/…/mcp?token=stn_…`) deben pasar a un enlace de Gateway
    Hub. El Worker `stone-relay` puede borrarse cuando no quede ninguna Stone anterior instalada.
- Sin APK todavía.

# Stone Kotlin 1.7.17

- Sin cambios visibles. Preparación de la arquitectura con Gateway Hub centralizado (fase 1b):
  - El manejo de MCP sale del servidor HTTP a un despachador propio, sin red (`automation/provider/McpDispatcher.kt`).
    El servidor HTTP lo usa igual que antes, y el puente con Gateway ya no depende de él.
  - El puente con Gateway Hub (`automation/provider/`) usa los permisos que Gateway delega en cada petición, recortados a
    lo que Stone permite: por ahora, solo lectura. Ya no depende del token «Gateway Hub» de Stone; ese token solo cuenta
    para el enlace público mientras exista.

# Stone Kotlin 1.7.16

- **Puente local con Gateway Hub** (fase 1 de la migración a un solo endpoint MCP): la app Gateway Hub del mismo
  teléfono puede listar las herramientas de Stone y ejecutar su herramienta de prueba (`stone_help`) por IPC de Android,
  sin pasar por internet. Detalles en `docs/MCP.md` › Puente local.
  - Solo Gateway Hub firmado por el mismo desarrollador, comprobado por Android en cada mensaje.
  - **Solo lectura**: las herramientas que cambian algo no aparecen y se rechazan.
  - Necesita «Redirección hub» y Modo API activados. Apagar «Redirección hub» lo cierra.
- Nada cambia en el enlace público, el relay ni Cloudflare.

# Stone Kotlin 1.7.15

- **Cloudflare (túnel y hub)** en Ajustes › Modo API: pega o **actualiza tu token de la API de Cloudflare** (por ejemplo,
  cuando caduque a los 7 días). Muestra cuántos días le quedan y avisa en amarillo a partir de 2 días.
  - Se guarda cifrado con el Keystore y se comparte con **Gateway Hub 0.0.6**: si lo actualizas en una app, vale para
    las dos. Solo pasa entre apps del mismo desarrollador y con «Redirección hub» activada.
  - **Comprobar túnel:** busca el relay (`stone-relay`) en tu cuenta y te dice si es el que usa Stone.
  - **Crear token en Cloudflare** abre la página de Cloudflare con el permiso necesario ya elegido (Workers Scripts › Editar).
- **Crear token** pregunta primero **para qué es**: *para un hub* (se llama «Gateway Hub» y su enlace va en Gateway Hub,
  nunca en un chat) o *para una IA* (Claude, ChatGPT…).

# Stone Kotlin 1.7.14

- **Modo seguro de Gateway Hub** (Ajustes › Modo API › Gateway Hub › «Solo con Stone abierta»): con el
  interruptor activado, Stone atiende MCP solo mientras está en pantalla. Al salir de Stone se cortan el
  servidor y el enlace público (las peticiones pendientes se cancelan) y vuelven solos al abrirla de nuevo.
  Desactivado, todo funciona como antes, también en segundo plano.
- La app **Gateway Hub 0.0.5** cambia este interruptor desde su propio ajuste *Modo de funcionamiento*
  (Modo seguro o MCP en segundo plano). Solo lo acepta de apps firmadas por el mismo desarrollador, y nunca da
  enlaces ni tokens por ese canal.

# Stone Kotlin 1.7.13

- **Redirección hub** (Ajustes › Modo API › Gateway Hub): con el interruptor activado, la app Gateway Hub de este
  teléfono vincula Stone sola al escanear, sin copiar ni pegar. Hay tres cerrojos y hacen falta los tres:
  - un permiso de Android que solo tienen las apps firmadas con la misma clave;
  - Stone vuelve a comprobar la firma de quien llama;
  - tu interruptor.
- Viene apagada. Si la desactivas, Stone deja de responder al hub. Revocar el token «Gateway Hub» corta el acceso.

# Stone Kotlin 1.7.12

- La app **Gateway Hub** encuentra Stone con su escáner (Stone se declara como la app `music_`) y su botón
  «Abrir y vincular» abre directamente Ajustes › Modo API. Ser encontrada no activa nada: decides tú en Stone.
- Seguridad: cualquier app que abra Stone con esa acción solo consigue mostrar Modo API (Stone no lee nada
  de la petición). La app del hub comprueba la firma de Stone antes de marcarla como «Mismo desarrollador».

# Stone Kotlin 1.7.11

- Stone y el hub MCP pasan a ser dos aplicaciones separadas: el hub vive en
  [SiriusMagic/Gateway-Hub-MCP](https://github.com/SiriusMagic/Gateway-Hub-MCP). Stone conserva el
  interruptor de la API, su token y las confirmaciones en el teléfono; el hub genera los enlaces de
  cada IA y lleva las métricas de todas las apps.
- Nueva tarjeta en Ajustes › Modo API › **Gateway Hub › Vincular**: crea o reutiliza el token
  «Gateway Hub», copia su enlace y abre el panel del hub para pegarlo, sin pasar por ningún chat.
  Revocar ese token en Stone corta el hub al instante.

# Stone Kotlin 1.7.10

- Seguridad: Stone ya no envía nada sin cifrar. Se quitó la última excepción (127.0.0.1) y las portadas
  solo se descargan por HTTPS.
- Se eliminó por completo el acceso por Wi-Fi (HTTP sin cifrar), que seguía en el código aunque no se
  mostraba. El servidor MCP solo escucha dentro del teléfono; desde fuera se llega solo por el enlace
  cifrado. Los ajustes antiguos sin pantalla (Wi-Fi, puerto, token solo en cabecera) se borran.
- El enlace del relay nunca se conecta sin cifrar.
- Relay (ya publicado): solo HTTPS, cabeceras de seguridad, como máximo 120 peticiones por minuto por
  cliente, cuerpo de hasta 1 MiB y registro de intentos de quedarse con el enlace de otro teléfono.
- Código sin uso eliminado y carpetas reorganizadas por función (ver `ARQUITECTURA.md`). Los widgets y
  las alarmas siguen funcionando tras actualizar.
- Dependencias revisadas contra OSV.dev: Guava pasa a 33.7.2 (CVE-2026-102554). Ver `DEPENDENCIAS.md`
  y `THIRD-PARTY-SERVICES.md`.

# Stone Kotlin 1.7.9

- Seguridad con varios agentes: cada petición usa solo los permisos de su token (antes, con varios agentes a la
  vez, `stone_help` podía mostrar a un token de solo lectura herramientas de otro). Cada agente ve solo su
  actividad y sus trabajos; un trabajo se detiene si su token se revoca, pausa o caduca.
- Un agente con un token erróneo ya no bloquea a los demás: los intentos se cuentan por origen real del cliente
  y un token válido nunca queda bloqueado.
- Sin acciones duplicadas por tiempo de espera: lo que esperó demasiado no se ejecuta (503, «no se cambió nada»)
  y un cambio repetido tras un 504 devuelve el primer resultado. Cola del enlace limitada (64).
- El enlace deja de reconectar cuando el relay responde un error que no cambiará y, ante fallos de red, espera
  hasta 5 min y vuelve a intentar al recuperar la red (antes: cada 30 s sin fin).
- Nueva herramienta `stone_metrics`: solicitudes por ruta, códigos HTTP, métodos MCP, latencia, esperas en cola,
  reconexiones y tiempos de espera del relay, sin guardar tokens, direcciones ni argumentos.
- Relay (requiere `npx wrangler deploy`): envía la dirección del cliente y avisa al teléfono de cada 504.

# Stone Kotlin 1.7.8

- Modo API más simple: solo dos pestañas. General: «Activar MCP», «Crear token» y una tarjeta por token con
  nombre, enlace (copiar) y Revocar · Pausar · Configurar. Analítica aparte, con la actividad debajo.
- Crear token: nombre, su enlace, duración, modo y herramientas (categorías plegadas) en una sola pantalla;
  al guardar se copia el enlace. Nuevo «Pausar»: el token se rechaza hasta reanudarlo, sin cambiar su enlace.
- Se quitan las pestañas Herramientas, Seguridad y Enlace: los permisos son por token, la red local (HTTP sin
  cifrar), el puerto y las configuraciones manuales desaparecen. Revocar pide confirmación y borra el token.

# Stone Kotlin 1.7.7

- APK completo (sin optimizar): `Stone-1.7.7-completo.apk`.
- Arreglo: si el Android Keystore perdía su clave, la nueva clave del relay no se guardaba y el enlace público dejaba de funcionar.
- Modo API › Actividad › Analítica: solicitudes por minuto de todos los agentes o de cada token (última hora
  o 24 h): total, media y pico por minuto, errores y denegadas, gráfico de barras (toca una barra para ver su
  hora y cifras) y llamadas con token erróneo. Se guarda en el teléfono.
- «Configurar token»: editor de cada token con nombre, modo, caducidad, herramientas permitidas por categoría
  (solo para ese token; Stone las comprueba en cada solicitud y el agente deja de verlas) y su propia analítica.
  Revocar pide confirmación.
- Ecualizador «Según el género»: con el ecualizador encendido, cada canción aplica el ajuste de su género
  (Electrónica, Rock, Latín, Heavy metal…) al empezar. Las canciones sin género usan tu propio ajuste, que nunca
  se sobrescribe. También desde la API: equalizer_set_by_genre.
- Seguridad del Modo API: los tokens y la clave del relay se guardan cifrados con una clave del Android
  Keystore (se borra la copia antigua en texto plano). Interruptor «Token dentro del enlace»: apagado, los
  enlaces no llevan el secreto y solo se acepta Authorization: Bearer. Las sesiones SSE exigen el token en cada
  mensaje, pertenecen al token que las abrió y se cierran al revocarlo o rotarlo. Aviso de que la red local
  usa HTTP sin cifrar.
- Modo API: cada token muestra su propio enlace con botón de copiar, para dar uno distinto a cada agente
  (pueden trabajar a la vez). Caducidad por token: sin fecha, 7, 30, 90 días, 1 año o personalizada, también
  al editar (renueva un token caducado).
- Agentes: lrclib.net e iTunes se consultan con pausas compartidas; ante un HTTP 429 Stone espera y, si
  sigue, responde RATE_LIMITED con los segundos a esperar y las canciones pendientes.
- Lotes de más de 10 canciones pasan a segundo plano (jobId) con progreso y resultado por canción; esperan
  solos ante límites y se pueden reanudar o cancelar. Ya no caducan por el tiempo del enlace.
- Nuevas herramientas: stone_activity (últimas llamadas y errores, guardadas aunque Stone se cierre),
  stone_troubleshoot (diccionario de soluciones por código y flujos recomendados), stone_job_status /
  resume / cancel, metadata_suggest_tags_many (géneros en lote solo con confianza ≥ 0,8) y
  metadata_prepare_write (pedir el permiso de Android una vez antes de un lote).
- Las búsquedas de letras y etiquetas usan los datos actuales del archivo: ya no hace falta reescanear tras
  cambiar un título. Avisan TITLE_HAS_EXTRAS con el título limpio sugerido; metadata_rename stripExtras=true
  quita "lyrics", "official video", "slowed + reverb", "| canal"… y conserva remix, live, acoustic, sped up.
- Las letras guardadas por agentes también se escriben dentro del archivo cuando Android lo permite.
- Al guardar o borrar letras, también se guardan o se quitan dentro del archivo de la canción (MP3,
  FLAC y M4A). Al compartir la canción o abrirla en otro reproductor, la letra va incluida, con sus
  tiempos si es sincronizada. Stone avisa si se guardó en el archivo o si el formato no lo admite.
- Stone lee letras incrustadas también en FLAC y M4A (antes solo en MP3).
- Editar título, artista, álbum o género ya no toca las letras guardadas en el archivo.

# Stone Kotlin 1.7.6

- Arreglo: al tocar una canción, la marca ya no salta a la primera de la lista y vuelve; la canción
  tocada queda marcada hasta que el reproductor la tiene cargada.
- Arreglo: Stone se cerraba al abrir un vídeo en vertical (desde el mini reproductor). La fila de
  botones redondos se desplazaba dentro de otra fila desplazable; ahora solo se desplaza una.
- Letras sincronizadas en el reproductor: el recuadro bajo los controles muestra una frase y, al
  cambiar, sube suavemente mientras la siguiente entra desde abajo (650 ms). Sigue la posición real
  del audio: al adelantar, retroceder, pausar o cambiar la velocidad se actualiza al instante.
- LRC: marcas por palabra (`<mm:ss.xx>`), líneas vacías como pausa instrumental (♪) y marcas
  repetidas sin saltos. Las letras sin tiempos ya no marcan la última línea como actual.
- En la vista completa de letras, tocar una frase salta a ese momento de la canción.
- Con "Quitar animaciones" de Android, las letras cambian sin movimiento.

# Stone Kotlin 1.7.5

- Arreglo del cierre en vertical: Kotlin 2.4 era más nuevo que el optimizador R8 del APK, que no
  entendía su código y podía generar un APK que fallaba. Se usa Kotlin 2.3.21, que R8 sí entiende.

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
