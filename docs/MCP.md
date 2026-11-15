# Stone — guía de conexión para agentes (MCP)

Stone expone un servidor **Model Context Protocol** en el teléfono (Ajustes › Modo API). Este
documento es para pegárselo a un agente de IA que no logre conectarse.

## Enlace

`https://<relevo>/d/<dispositivo>/mcp?token=<TOKEN>` (botón "Copiar enlace" en Stone).
El token también se acepta como `Authorization: Bearer <TOKEN>` o `X-Api-Key: <TOKEN>`.
Stone debe estar abierto en el teléfono con el Modo API activado.

## Tres formas de usarlo

| Cliente | Cómo |
| --- | --- |
| Cliente MCP (Claude, ChatGPT, Claude Code…) | `POST` JSON-RPC 2.0 al enlace. Métodos: `initialize`, `tools/list`, `tools/call`. Cabeceras `Content-Type: application/json` y `Accept: application/json, text/event-stream`. |
| Cliente MCP antiguo (SSE) | `GET …/sse?token=…` y `POST` al endpoint que anuncia. |
| Agente que solo puede hacer GET | `GET …/tools?token=…` lista las herramientas; `GET …/call/NOMBRE?token=…&param=valor` ejecuta una. |

Un `GET` normal al enlace devuelve una guía en JSON con la lista de herramientas. Solo un `GET`
con `Accept: text/event-stream` recibe `405`, que es lo que el protocolo indica cuando el
servidor no ofrece flujo propio: **no es un fallo**, hay que usar `POST`.

`GET …/health` no necesita token y solo dice si Stone está en marcha y su versión.

## Ejemplos

```bash
LINK="https://<relevo>/d/<dispositivo>"; TOKEN="<TOKEN>"

# ¿Está vivo?
curl "$LINK/health"

# MCP: listar herramientas
curl -X POST "$LINK/mcp" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# MCP: mover una barra del ecualizador
curl -X POST "$LINK/mcp" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"equalizer_set_band","arguments":{"band":0,"value":6}}}'

# Sin MCP: solo GET
curl "$LINK/call/player_state?token=$TOKEN"
curl "$LINK/call/library_search?token=$TOKEN&query=alan%20walker&limit=5"
curl "$LINK/call/video_set_brightness?token=$TOKEN&percent=60"
```

## Flujo recomendado

1. `stone_help` (opcional: `query` = "ecualizador", "cola", "brillo"…) para encontrar la herramienta.
2. `library_search` para obtener los `id` de canciones.
3. `player_play_song`, `player_queue_next`, `playlist_add_songs`… con esos `id`.
4. `player_state` para confirmar.

## Herramientas por categoría

- **player**: `state`, `play`, `pause`, `next`, `previous`, `seek`, `set_speed`, `set_volume`,
  `play_song`, `play_songs`, `queue_next`, `queue`, `queue_play`, `queue_move`, `queue_remove`,
  `queue_clear`, `set_shuffle`, `set_repeat`, `set_favorite`, `sleep_timer`, `sleep_timer_extend`.
- **equalizer**: `get`, `set_enabled`, `set_band` (una barra: banda 0-9, valor -10..10),
  `set_bands` (las diez), `set_preset`.
- **video**: `get`, `set_scale`, `set_brightness`, `set_orientation`.
- **library**: `search`, `stats`, `rescan`, `get_sort`, `sort`, `browse`, `favorites`, `lyrics`.
- **playlist**: `list`, `create`, `add_songs`, `songs`, `remove_songs`, `play`.
- **metadata**: `get`, `update` (escribe etiquetas en el archivo; pide aprobación en el teléfono).
- **appearance / settings**: `get`, `set_theme`, `set_accent`, `set_look`, `reset_look`,
  `set_language`, `set_audio_mode`.

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

## Seguridad

Quien tenga el enlace controla la reproducción. No lo pegues en sitios públicos; si se filtra,
pulsa "Cambiar enlace" en Stone para revocarlo. Las acciones que escriben archivos siempre piden
confirmación en el teléfono.
