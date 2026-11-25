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
        │   ├── automation/         # 🤖 Servidor MCP, aislado del resto; lo controla Arka
        │   │   ├── ApiMode.kt      # Enlace público encendido o no (lo único que la interfaz lee)
        │   │   ├── HubLink.kt      # Canal de Arka con firma: link, connect, disconnect, status, tool
        │   │   ├── server/         # HTTP + MCP en 127.0.0.1 (punto de entrada)
        │   │   ├── actions/        # Tools que ve la IA (library_search, playlist_create…)
        │   │   ├── auth/           # El token de Arka, permisos (lectura / escritura / total), límite de intentos
        │   │   ├── relay/          # Cliente del relay
        │   │   └── activity/       # Registro de llamadas (lo leen los agentes con stone_activity)
        │   │
        │   ├── feature/            # 🧩 Funciones visibles, una carpeta cada una
        │   │   ├── library/        # Biblioteca (components/, video/)
        │   │   ├── player/         # Reproductor (lyrics/, sheets/, tags/, video/)
        │   │   ├── settings/       # Ajustes (look/, feedback/; Modo MCP solo enlaza a Arka)
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

## Secretos

- **Ningún token ni clave está en el código ni en Git.** Stone guarda un solo token, el de Arka, cifrado con el
  Android Keystore y comparado en tiempo constante. Activar el Modo MCP, permitirlo en local y crear tokens para cada
  IA se hace en Arka (el hub); Stone ya no tiene pantalla propia.
- **El relay no guarda tokens**, así que no necesita `wrangler secret`: quien comprueba el token es
  el teléfono.
- **Archivos ignorados por Git:** `.env`, `local.properties`, `*.keystore` y `*.jks`.
- **Menor privilegio:** cada agente recibe su propio token en el hub (Arka), con sus apps, permisos y caducidad.
- **Mejor en la cabecera:** `Authorization: Bearer …`. El token dentro del enlace (`?token=`) solo
  existe porque algunos conectores aceptan nada más que una URL.
- **Si un token se filtra:** revócalo en Arka › Agentes (deja de funcionar al instante).
