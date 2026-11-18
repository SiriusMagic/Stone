# Stone — guía de conexión para agentes (MCP)

Stone ofrece sus herramientas **Model Context Protocol** a través de Gateway Hub (Ajustes › Gateway Hub). Este
documento es para pegárselo a un agente de IA que no logre usarlas.

## Cómo se conecta un agente

Stone **ya no tiene enlace propio, tokens ni servidor HTTP**: es un proveedor de herramientas de **Gateway Hub**.
El agente usa la única dirección MCP del usuario, `https://<hub>/mcp`, con el enlace que Gateway Hub crea para
él (Agentes › Nuevo enlace). Las herramientas de Stone aparecen con el prefijo `music_` (`music_player_state`,
`music_library_search`…), o `music2_`… si es el Stone de otro teléfono.

- Gateway Hub decide qué agente puede usar qué (enlaces, apps permitidas, solo lectura) y Stone ejecuta solo lo
  que cubre un **paquete de ejecución** aprobado en Stone (ver más abajo).
- `tools/list` muestra solo lo que los paquetes del agente permiten. Un error `NO_PACKAGE`, `ACT_NOT_IN_PACKAGE` o
  `PACKAGE_PENDING` significa: pedir al usuario un paquete en Gateway Hub y que lo apruebe en Stone.
- `SAFE_MODE_STONE_CLOSED`: el usuario eligió el modo seguro en Gateway Hub y Stone no está en pantalla.

## Flujo recomendado

1. `stone_help` (opcional: `query` = "ecualizador", "cola", "brillo"…) para encontrar la herramienta.
2. `library_search` para obtener los `id` de canciones.
3. `player_play_song`, `player_queue_next`, `playlist_add_songs`… con esos `id`.
4. `player_state` para confirmar.
5. Si una herramienta falla: `stone_troubleshoot code=CODIGO` explica la causa y los pasos;
   `stone_activity onlyErrors=true` muestra los últimos fallos (se guardan aunque Stone se cierre).

### Letras y géneros de muchas canciones

limpiar título → buscar → validar artista/duración/versión → guardar → anotar `batchId`.

1. `metadata_prepare_write`: Android pide permiso una sola vez para todos los archivos.
2. `metadata_rename stripExtras=true dryRun=true`: deja solo el nombre real (quita `lyrics`, `official video`,
   `slowed + reverb`, `| canal`…; conserva remix, live, acoustic, sped up). Luego sin `dryRun`.
3. `metadata_fetch_lyrics` de 5-10 canciones. Stone lee las etiquetas actuales del archivo: no hace falta
   `library_rescan` entre editar y buscar.
4. `LOW_CONFIDENCE`: comparar artista, álbum, duración y versión; guardar con `choices`.
5. Géneros: `metadata_suggest_tags_many onlyMissing=genre`; solo escribe con confianza ≥ 0.8, el resto vuelve
   con `needsReview=true`.

`stone_troubleshoot code=WORKFLOW_LYRICS` (o `WORKFLOW_GENRES`, `WORKFLOW_BULK`) devuelve estos pasos al agente.

## Herramientas por categoría

- **player**: `state`, `play`, `pause`, `next`, `previous`, `seek`, `set_speed`, `set_volume`,
  `play_song`, `play_songs`, `queue_next`, `queue`, `queue_play`, `queue_move`, `queue_remove`,
  `queue_clear`, `set_shuffle`, `set_repeat`, `set_favorite`, `sleep_timer`, `sleep_timer_extend`.
- **equalizer**: `get`, `set_enabled`, `set_band` (una barra: banda 0-9, valor -10..10),
  `set_bands` (las diez), `set_preset`.
- **video**: `get`, `set_scale`, `set_brightness`, `set_orientation`.
- **library**: `search`, `stats`, `rescan`, `get_sort`, `sort`, `browse`, `favorites`, `lyrics`.
- **playlist**: `list`, `create`, `add_songs`, `songs`, `remove_songs`, `play`.
- **metadata**: `get` (canciones y vídeos, con letra), `update` (título, artista, álbum, género, portada desde
  `coverUrl`; en vídeos también descripción, fecha y palabras clave), `update_many` (muchos elementos con una sola
  aprobación; `changes` es un array JSON) y `set_lyrics` (texto o LRC; vacío borra). Todas piden aprobación en el teléfono.
- **appearance**: `get`, `set_theme`, `set_accent`, `set_look`, `reset_look`.
- **audio**: `get_levels`, `set_level` (techo de volumen y ganancia de cada modo), `reset_levels`.
- **settings**: `get`, `set_language`, `set_audio_mode`, `set_transitions`, `set_hardware_acceleration`,
  `set_play_with_others`, `set_option`.
- **library / playlist (extra)**: `library_set_favorite`, `playlist_rename`, `playlist_delete`.
- **metadata (lotes)**: `search_lyrics`, `fetch_lyrics`, `suggest_tags`, `suggest_tags_many`, `rename`,
  `prepare_write`, `history`, `undo`.
- **stone**: `help`, `troubleshoot` (diccionario de soluciones), `activity` (tus últimas llamadas y errores;
  cada token ve solo las suyas), `metrics` (contadores del servidor y del enlace, sin secretos),
  `job_status`, `job_resume`, `job_cancel` (trabajos en segundo plano; cada trabajo pertenece al token que lo
  inició, los demás no lo ven, y se detiene si ese token se revoca, pausa o caduca).

El nombre MCP es `categoria_accion`, por ejemplo `equalizer_set_band`.

## Errores

| Respuesta | Significado | Qué hacer |
| --- | --- | --- |
| HTTP 401 "missing token" | No llegó el token | Añadir `?token=` o `Authorization: Bearer`. |
| HTTP 401 "wrong or revoked token" | El enlace cambió | Copiar el enlace de nuevo en Stone. |
| HTTP 503 del relevo | Stone no está abierto o el Modo API está apagado | Abrir Stone. |
| HTTP 504 del relevo | El teléfono no respondió a tiempo | Reintentar; revisar la conexión del teléfono. |
| JSON-RPC `-32602` | Herramienta desconocida | `tools/list` o `stone_help`. |
| Resultado con `isError: true` | Parámetro inválido o acción rechazada | Leer el texto y corregir. |
| Fallo de DNS o de red en el agente | El entorno del agente no puede salir a internet | No es un problema de Stone: usar un cliente con acceso a red. |

## Permisos

- Las acciones declaran su riesgo: lectura, reversible, persistente (con copia y `metadata_undo`) o crítica.
- Lo crítico (borrar una lista) **siempre** pide confirmación en la pantalla de Stone (60 s).
- Qué acciones puede usar cada agente lo deciden sus paquetes (leer, revisar, escribir, modificar, revertir).

## Edición masiva

- `dryRun: true` en cualquier herramienta de edición devuelve qué cambiaría sin escribir nada.
- `metadata_update_many`, `metadata_set_lyrics_many`: array JSON en `changes`.
- `metadata_search_lyrics`: candidatos de lrclib.net para una canción, con `confidence` 0-1.
- `metadata_fetch_lyrics`: busca, empareja cada resultado con su canción y guarda por lote solo los
  que superan `minConfidence` (0.75). El resto vuelve como `LOW_CONFIDENCE` con candidatos; se
  guardan con `choices: [{"songId":"…","lrclibId":123}]`.
- `metadata_rename`: plantillas `{title} {artist} {album} {file} {date} {n} {n:2}`, `clean`
  (quita `_`, extensiones, `[1080p]`, `x264`, IDs de YouTube…) y `find`/`replace` con regex.
- Los lotes devuelven `total`, `updated`, `failed`, `durationMs`, `batchId` y un resultado por elemento.
- Las letras guardadas por API también se escriben dentro del archivo (MP3, FLAC, M4A) cuando Android
  ya lo permite: `embeddedInFile: true`. `metadata_undo` restaura también el archivo.

### Lotes grandes, trabajos y límites de lrclib.net / iTunes

- Hasta 10 elementos responden directamente. Con más (o `background: true`) la herramienta devuelve
  al instante un `jobId`; el trabajo sigue en el teléfono, canción por canción.
- `stone_job_status jobId=…` (cada 10-20 s): `status` (`running`, `waiting`, `paused`, `done`,
  `cancelled`), `done/total`, `percent`, un resultado por canción (`since` para leer solo lo nuevo) y
  `remainingSongIds`. `stone_job_resume` continúa; `stone_job_cancel` detiene.
- Stone espacia las peticiones (lrclib.net ~0,7 s, iTunes 3 s) y las comparte entre agentes. Ante un
  HTTP 429 respeta `Retry-After`; si sigue, devuelve `RATE_LIMITED` con `retryAfterSeconds`. Un lote
  directo se detiene ahí y devuelve `remainingSongIds`; un trabajo espera (cada vez más) y sigue solo.

## Códigos de error

`structuredContent.error.code` (y `[CODE]` al inicio del texto): `UNKNOWN_TOOL`, `INVALID_PARAM`,
`NOT_FOUND`, `READ_ONLY_TOKEN`, `TOOL_DISABLED`, `USER_DENIED`, `ANDROID_CONSENT`,
`UNSUPPORTED_FORMAT`, `STONE_CLOSED`, `LYRICS_NOT_FOUND`, `LOW_CONFIDENCE`, `RATE_LIMITED`, `NETWORK`,
`SKIPPED`, `ACTION_FAILED`. Avisos: `TITLE_HAS_EXTRAS` (con `suggestedTitle`). Muchos incluyen `hint` y
`solution: "stone_troubleshoot code=…"` con cómo resolverlo.

## Seguridad

- Stone no escucha en ningún puerto ni abre conexiones para MCP: solo responde a Gateway Hub del mismo teléfono por
  IPC, verificado por Android (permiso `signature`, uid, paquete y firma en cada mensaje).
- Los paquetes van firmados con la clave del teléfono de Gateway y Stone los verifica al recibirlos y al cargarlos.

## Varios agentes a la vez

Cada petición lleva su propia autorización (agente, modo y herramientas bloqueadas) en su corrutina: dos agentes a
la vez nunca ven ni usan los permisos del otro. Los trabajos en segundo plano y la actividad son de quien los creó.

## Detalles del puente con Gateway Hub

Además del enlace público, Stone ofrece su servidor MCP a la app **Gateway Hub del mismo teléfono** por IPC de
Android, sin HTTP ni relay. Es la primera fase de la migración a un solo endpoint MCP por usuario (Gateway Hub).

- **Código (1.7.17):** `automation/provider/`: `ProviderService.kt` (servicio), `ProviderBridge.kt` (identidad,
  límites, filtros), `GatewayGrant.kt` (permisos delegados) y `McpDispatcher.kt` (MCP JSON-RPC sin sockets, que también
  usa el servidor HTTP mientras exista). El puente ya no depende de `McpServer` ni del token «Gateway Hub» de Stone.
- **Transporte:** Bound Service `automation/provider/ProviderService.kt` con `Messenger`, acción
  `com.zirakusa.gatewayhub.action.MCP`. Cada mensaje es un objeto JSON-RPC (sin lotes) en el `Bundle`, clave
  `message`; `arg1` empareja la respuesta.
- **Identidad (solo de Android, nunca del mensaje):**
  1. para enlazarse hace falta el permiso `com.zirakusa.gatewayhub.permission.LINK` (`signature`);
  2. en cada mensaje, `Message.sendingUid` → paquetes de ese uid → debe estar `com.zirakusa.gatewayhub`;
  3. y firmado con la misma clave que Stone (`checkSignatures`).
- **Nada que activar en Stone** (desde la fase 2): solo «Desconectar de Gateway Hub» lo corta.
- **Permisos delegados por Gateway (`GatewayGrant`):** cada petición puede llevar
  `params._meta["com.zirakusa.gatewayhub/grant"] = {"mode": "read"|"write"|"full", "blockedTools": [...], "agent": "id"}`.
  Stone lo acepta solo porque el remitente ya se verificó por uid y firma, y lo **recorta a su tope local**: hoy solo
  lectura. Si falta o está mal formado, se aplica el tope sin nada extra.
- **Solo lectura en `tools/list` y `tools/call`:** según el `ActionRisk` declarado de cada acción. Una herramienta que
  no es `READ` se rechaza con `READ_ONLY_BRIDGE` antes de ejecutarse. Las herramientas que Gateway bloquea no se listan
  y su llamada da `TOOL_DISABLED`. El token «Gateway Hub» de Stone (pausa, caducidad, herramientas desactivadas) ya solo
  afecta al camino HTTP/relay.
- **Herramienta de prueba:** solo `stone_help` (lee, sin efectos, funciona sin la pantalla de Stone), marcada en
  `tools/list` con `_meta["com.zirakusa.gatewayhub/probe"] = true`. Gateway no ejecuta ninguna otra.
- **Tamaño:** máximo 200 000 caracteres por mensaje (~400 KB en UTF-16, por debajo del búfer de 1 MB de Binder).
  Una respuesta mayor se devuelve como `RESPONSE_TOO_LARGE`. Hoy `tools/list` de solo lectura ocupa ~9 700 caracteres.
  Si en el futuro una respuesta lo supera (p. ej. listas grandes de la biblioteca), el transporte alternativo previsto
  es un `ParcelFileDescriptor` (tubería) o respuestas paginadas; no hace falta en esta fase.
- **Errores del canal:** cola de 16 peticiones (`BUSY` si se llena), errores JSON-RPC con código en el mensaje
  (`NOT_GATEWAY`, `NOT_SAME_DEVELOPER`, `REDIRECT_OFF`, `API_OFF`), y si Gateway desaparece la respuesta se descarta.
- **Reversión:** apagar «Redirección hub» cierra el puente sin reinstalar nada. El enlace público, el relay y Cloudflare
  no cambian.

### Fase 2 (sin publicar): paquetes de ejecución

- **Sin nada que activar en Stone:** el puente ya no depende del Modo API ni de «Redirección hub». Solo lo corta
  «Desconectar de Gateway Hub» (Ajustes › Modo API › Gateway Hub · paquetes), que borra el entorno y todos los paquetes.
- **Vinculación** (`gatewayhub/link`): Gateway envía su id de entorno y la clave pública ECDSA P-256 (la privada nunca
  sale del Keystore de Gateway). Stone responde con su id de instalación (`inst_…`). Un entorno nuevo sustituye al
  anterior y a sus paquetes.
- **Paquete:** `{"pkg": base64url(bytes JSON), "sig": base64url(ECDSA P-256/SHA-256)}`; el JSON lleva `v`, `id`
  (`pkg_…`), `env`, `aud` (`<paquete>#<instancia>`), `agent` (`*` o id), `label`, `acts`, `tools` (nombres o prefijos
  con `*`), `nbf` y `exp` (máximo 366 días). Stone lo verifica una vez al recibirlo y otra al cargar (un archivo
  modificado no da permisos). Máximo 64 paquetes de 8 KB.
- **Acciones sobre el riesgo declarado:** leer = `READ`; revisar = herramientas con `dryRun` (se fuerza `dryRun=true`);
  escribir = `REVERSIBLE`; modificar = `PERSISTENT` y `CRITICAL` (lo crítico sigue pidiendo confirmación en pantalla:
  el modo usado es `WRITE`, nunca `FULL`); revertir = `metadata_undo`.
- **Aprobación:** un paquete que solo lee queda activo al momento; cualquier otro queda pendiente hasta «Aprobar» en Stone.
- **Llamadas:** `_meta["com.zirakusa.gatewayhub/grant"] = {"agent": "...", "package": "pkg_..."}`. `tools/list` devuelve
  solo lo que cubren los paquetes activos del agente; `tools/call` se rechaza con `PACKAGE_PENDING`, `OTHER_AGENT`,
  `TOOL_NOT_IN_PACKAGE`, `ACT_NOT_IN_PACKAGE`, `EXPIRED` o `NO_PACKAGE`. Sin paquete, el puente sigue en solo lectura
  (compatible con Gateway 0.0.7).
- **Varios candidatos (fase 3):** el hub puede enviar `packages: [...]` (hasta 8, primero los del agente). Stone prueba cada
  uno y usa el primero que cubre la llamada; si ninguno, responde con el motivo del primero.
- **Otros métodos:** `gatewayhub/packages/offer|revoke|list` y `gatewayhub/state` (estado del entorno y modo que Stone muestra).
- **Cancelación:** `notifications/cancelled {requestId}` interrumpe la llamada (por ejemplo, una confirmación aún en
  pantalla) y no se envía respuesta. Lo ya hecho no se deshace.
- **Pendiente:** `notifications/tools/list_changed` (las herramientas de Stone no cambian en ejecución) y herramientas sin
  la interfaz abierta (fase 2b).
