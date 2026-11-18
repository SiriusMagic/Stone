# Arquitectura de Stone

Stone es una app de música y video para Android (Kotlin) con un **servidor MCP** dentro del
teléfono, para que Claude, ChatGPT y otros agentes la controlen, y un **relay** en Cloudflare que
le da un enlace público cifrado. Está organizada como un **monorepo modular**: cada parte vive en
su carpeta, no se mezclan entre sí y comparten la lógica común.

Detalle de cada carpeta de la app: [`android-native/ARQUITECTURA.md`](android-native/ARQUITECTURA.md).
Mapa archivo por archivo: [`android-native/MAP.md`](android-native/MAP.md).

```text
Stone/
├── ARQUITECTURA.md                 # Este árbol
├── README.md · CHANGELOG.md
├── docs/                           # Documentación, planes y migración a Kotlin
│
├── relay/                          # Enlace público https://…/d/<teléfono>/mcp (Cloudflare Worker)
│   ├── src/index.js                # Reenvía cada petición al teléfono por un WebSocket saliente
│   └── wrangler.toml               # Sin secretos: el relay no guarda tokens, solo el hash del teléfono
│
│                                   # (El hub MCP vive aparte: github.com/SiriusMagic/Gateway-Hub-MCP)
└── android-native/                 # App Android
    ├── core/                       # 🧠 Lógica pura compartida, sin Android (letras, Song, diseño)
    │
    └── app/src/
        ├── main/java/…/nativeapp/
        │   ├── MainActivity.kt
        │   │
        │   ├── playback/           # 🎵 Motor de audio (lo usan la interfaz y el MCP)
        │   │   ├── StonePlaybackService.kt · StoneWidgetProvider.kt
        │   │   ├── control/        # Cola, transiciones, modo Eco
        │   │   ├── audio/          # Volumen, ecualizador, género
        │   │   └── diagnostics/
        │   │
        │   ├── data/               # 💾 Datos (lo usan la interfaz y el MCP)
        │   │   ├── persistence/    # Base de datos, ajustes, SecretBox (cifrado con Keystore)
        │   │   └── library/
        │   │       ├── scan/       # Encontrar canciones y videos
        │   │       ├── user/       # Playlists, favoritos, papelera
        │   │       ├── tags/       # Etiquetas MP3/FLAC/MP4
        │   │       ├── lyrics/     # Letras (archivo, LRCLIB, índice)
        │   │       ├── artwork/    # Portadas
        │   │       ├── web/        # Ritmo de peticiones a internet
        │   │       └── notifications/
        │   │
        │   ├── automation/         # 🤖 Servidor MCP, aislado del resto
        │   │   ├── ApiMode.kt      # Lo único que la interfaz necesita conocer
        │   │   ├── server/         # HTTP + MCP en 127.0.0.1 (punto de entrada)
        │   │   ├── actions/        # Tools que ve la IA (library_search, playlist_create…)
        │   │   ├── auth/           # Tokens, permisos (lectura / escritura / total), límite de intentos
        │   │   ├── relay/          # Cliente del relay
        │   │   └── activity/       # Registro y estadísticas de llamadas
        │   │
        │   ├── feature/            # 🧩 Funciones visibles, una carpeta cada una
        │   │   ├── library/        # Biblioteca (components/, video/)
        │   │   ├── player/         # Reproductor (lyrics/, sheets/, tags/, video/)
        │   │   ├── settings/       # Ajustes (api/, look/, feedback/)
        │   │   └── onboarding/
        │   │
        │   └── ui/                 # 🎨 Armazón visual compartido (theme/, components/, system/)
        │
        ├── main/res/               # Textos, iconos, imágenes, network_security_config (solo HTTPS)
        └── test/                   # Pruebas de la app y del MCP (seguridad incluida)
```

## Cómo encajan las piezas

```text
Claude / ChatGPT ──https──▶ relay/ ◀──wss── automation/relay ──▶ automation/server
                                                                       │ token + permisos
Interfaz (feature/, ui/) ──────────────────────────────▶ automation/actions
            │                                                          │
            └──────────────▶ playback/  ·  data/  ·  core/ ◀───────────┘
```

- **`playback/`, `data/` y `core/` alimentan a los dos mundos.** La interfaz y las tools del MCP
  usan el mismo código para reproducir, buscar o editar, así que nada se escribe dos veces.
- **`automation/` está aislado.** La interfaz solo habla con `ApiMode`. Se puede cambiar el
  servidor MCP sin tocar las pantallas.
- **Lo nuevo va en `feature/<nombre>/`.** Por ejemplo, un identificador de canciones tendría su
  carpeta propia; si su lógica la necesita también el MCP, esa lógica va en `data/` o `playback/`.
- **Las dependencias van en una sola dirección:** `feature` → `playback` / `data` / `automation` → `core`.

## El servidor: cómo está construido

Son **dos piezas**, cada una en un lenguaje:

```text
Claude / ChatGPT
      │ https://stone-relay…workers.dev/d/<teléfono>/mcp
      ▼
┌───────────────────────────────┐
│ 1. RELAY  · JavaScript        │  Cloudflare Workers + Durable Objects
│    relay/src/index.js         │  (en internet, plan gratuito)
└──────────────┬────────────────┘
               │ WebSocket cifrado (wss://) que abre el TELÉFONO hacia fuera
               ▼
┌───────────────────────────────┐
│ 2. SERVIDOR MCP · Kotlin      │  Dentro de Stone, en el teléfono
│    127.0.0.1:8765             │  (android-native/…/automation/)
└──────────────┬────────────────┘
               ▼
   El mismo código que usan los botones de la app
   (reproductor, biblioteca, letras, etiquetas…)
```

### 1. Servidor MCP: Kotlin, dentro de la app (`automation/`)

- **Sin framework**: no usa Ktor ni NanoHTTPD. Es un servidor HTTP/1.1 mínimo escrito a mano sobre
  `java.net.ServerSocket` (`server/McpHttp.kt`, `server/McpServer.kt`). Con poco código propio
  hay pocas dependencias y poca superficie de ataque.
- **Solo escucha dentro del teléfono** (`127.0.0.1`). Desde fuera solo se llega a través del relay.
- **Límites**: 8 hilos, 64 cabeceras y 1 MiB de cuerpo como máximo, y 15 s de espera por petición.
- **Protocolo**: MCP sobre JSON-RPC 2.0, con tres formas de conectarse para que funcione con
  cualquier cliente:
  - *Streamable HTTP*: `POST /mcp` (el estándar actual).
  - *SSE*: `GET /sse` (clientes antiguos).
  - *GET simple*: `/tools` y `/call/<herramienta>` (agentes que no hablan MCP).
- **Herramientas**: 85 tools en `actions/`: biblioteca, reproductor, letras, etiquetas, playlists,
  ecualizador, vídeo y ajustes. Hacen lo mismo que los botones de la app, con el mismo código.
- **Seguridad** (`auth/`):
  - Tokens `stn_` de 256 bits, cifrados con el Android Keystore.
  - Tres modos de acceso: lectura, escritura o total.
  - Caducidad y herramientas desactivables por token.
  - Bloqueo de 1 minuto tras 20 tokens erróneos.
  - Las acciones críticas piden confirmación en la pantalla del teléfono.

### 2. Relay: JavaScript en Cloudflare (`relay/`)

- **Cloudflare Worker** con un **Durable Object por teléfono**, que guarda la conexión WebSocket
  de ese teléfono.
- **El teléfono llama hacia fuera**, así que funciona con cualquier Wi-Fi o con datos móviles,
  sin tocar el router.
- **No interpreta ni guarda** los datos ni los tokens: solo reenvía. Guarda únicamente el hash del
  secreto de cada teléfono, para que nadie se quede con su enlace.
- **Protecciones**:
  - Solo HTTPS.
  - Cabeceras de seguridad.
  - 120 peticiones por minuto por cliente.
  - Cuerpo de 1 MiB como máximo.
  - Registro de intentos de quedarse con el enlace de otro teléfono.
- **Sin dependencias**: usa solo las APIs de Cloudflare. `wrangler` sirve solo para publicarlo
  (`cd relay && npx wrangler deploy`).

**En resumen:** la inteligencia y la seguridad están en el **teléfono (Kotlin)**; el relay
(**JavaScript**) es solo un cartero cifrado. Aunque alguien atacara el relay, sin el token no
podría hacer nada en Stone.

## Gateway Hub MCP (repositorio aparte)

Stone y el hub son **dos aplicaciones separadas**. El hub está en
[SiriusMagic/Gateway-Hub-MCP](https://github.com/SiriusMagic/Gateway-Hub-MCP) y da a una IA **un
solo enlace** para Stone y otras apps (notas, calculadora, utilidades…).

```text
Claude / ChatGPT ──https──▶ Gateway Hub ──https──▶ relay ──wss──▶ Stone (teléfono)
                            (otro repositorio)
```

| | Stone (este repo) | Gateway Hub |
|---|---|---|
| Activar o apagar el acceso | ✅ Interruptor «Activar MCP» en el teléfono | Pausar o revocar agentes |
| Seguridad final | ✅ Token, modos y confirmación en pantalla para lo crítico | Filtro previo por app y por solo lectura |
| Enlaces para las IA | Un token reservado, «Gateway Hub» | ✅ Uno por agente (apps, solo lectura, caducidad) |
| Métricas | Las suyas, locales (Analítica) | ✅ De todas las apps, por agente y herramienta |

**Vincular:** Stone › Ajustes › Modo API › **Gateway Hub › Vincular**. Esto crea (o reutiliza) el
token «Gateway Hub», copia su enlace y abre el panel del hub para pegarlo. Para cortar el acceso
del hub, revoca ese token en Stone. La dirección del hub va en `stoneHubUrl` de
`android-native/gradle.properties`.

## Secretos

- **Ningún token ni clave está en el código ni en Git.** Los tokens se crean en el teléfono, se
  guardan cifrados con el Android Keystore y se comparan en tiempo constante.
- **El relay no guarda tokens**, así que no necesita `wrangler secret`: quien comprueba el token es
  el teléfono.
- **El token «Gateway Hub»** de Stone se pega en el panel `/admin` del hub; nunca pasa por un chat ni
  por Git.
- **Archivos ignorados por Git:** `.env`, `local.properties`, `*.keystore` y `*.jks`.
- **Menor privilegio:** cada agente recibe su propio token, en modo lectura, escritura o total,
  con fecha de caducidad y con las tools que no deba usar desactivadas.
- **Mejor en la cabecera:** `Authorization: Bearer …`. El token dentro del enlace (`?token=`) solo
  existe porque algunos conectores aceptan nada más que una URL.
- **Si un token se filtra:** Ajustes › Modo API › el token › **Rotar** (mismo agente, enlace nuevo)
  o **Revocar** (deja de funcionar al instante).
