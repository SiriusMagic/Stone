# Arquitectura de Stone

Stone es una app de música y video para Android (Kotlin) que ofrece sus herramientas MCP a
**Gateway Hub** (otra app del mismo desarrollador) por un puente local protegido. Gateway es el único
dueño del enlace público, Cloudflare, tokens y agentes. Está organizada como un **monorepo modular**: cada parte vive en
su carpeta, no se mezclan entre sí y comparten la lógica común.

Detalle de cada carpeta de la app: [`android-native/ARQUITECTURA.md`](android-native/ARQUITECTURA.md).
Mapa archivo por archivo: [`android-native/MAP.md`](android-native/MAP.md).

```text
Stone/
├── ARQUITECTURA.md                 # Este árbol
├── README.md · CHANGELOG.md
├── docs/                           # Documentación, planes y migración a Kotlin
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
        │   ├── automation/         # 🤖 Proveedor MCP, aislado del resto
        │   │   ├── provider/       # Puente con Gateway (Bound Service + Messenger, paquetes firmados)
        │   │   ├── actions/        # Tools que ve la IA (library_search, playlist_create…)
        │   │   ├── auth/           # Modos de acceso (lectura / escritura / total)
        │   │   └── activity/       # Registro de llamadas
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
Claude / ChatGPT ──https──▶ Gateway Hub (Worker stone-hub) ──wss──▶ Gateway (teléfono) ──IPC──▶ automation/provider
                                                                                                 │ paquete firmado
Interfaz (feature/, ui/) ──────────────────────────────────────────────────────────▶ automation/actions
            │                                                                                    │
            └──────────────▶ playback/  ·  data/  ·  core/ ◀─────────────────────────────────────┘
```

- **`playback/`, `data/` y `core/` alimentan a los dos mundos.** La interfaz y las tools del MCP
  usan el mismo código para reproducir, buscar o editar, así que nada se escribe dos veces.
- **`automation/` está aislado.** Sin Gateway, Stone no tiene MCP: no abre puertos ni enlaces propios.
- **Lo nuevo va en `feature/<nombre>/`.** Si su lógica la necesita también el MCP, esa lógica va en `data/` o `playback/`.
- **Las dependencias van en una sola dirección:** `feature` → `playback` / `data` / `automation` → `core`.

## El proveedor MCP (`automation/provider/`)

- **Bound Service + Messenger**, protegido con el permiso `signature`
  `com.zirakusa.gatewayhub.permission.LINK`. Stone vuelve a comprobar el UID, el paquete y la firma de quien llama.
- **Paquetes de ejecución** firmados por Gateway con la clave del entorno (Android Keystore, ECDSA P-256): leer,
  revisar, escribir, modificar, revertir. Los de «leer» se aprueban solos; el resto, con un toque en Stone. Lo
  crítico pide confirmación en pantalla.
- **Modo seguro / segundo plano:** lo envía Gateway (`gatewayhub/state`); en Modo seguro Stone solo atiende con su
  pantalla abierta.
- **Límites:** 200 000 caracteres por mensaje; cancelación con `notifications/cancelled`.
- **Herramientas:** las de `actions/` (biblioteca, reproductor, letras, etiquetas, playlists, ecualizador, vídeo y
  ajustes), con el mismo código que los botones.

Gateway Hub está en [SiriusMagic/Gateway-Hub-MCP](https://github.com/SiriusMagic/Gateway-Hub-MCP). Ver también
[`docs/MCP.md`](docs/MCP.md).

## Secretos

- **Stone no guarda tokens, claves de Cloudflare ni enlaces públicos.** Todo eso vive en Gateway Hub.
- Stone solo guarda la clave pública del entorno que le da Gateway al vincular y los paquetes aprobados.
- **Archivos ignorados por Git:** `.env`, `local.properties`, `*.keystore` y `*.jks`.
- **Si algo se filtra:** en Gateway se revocan los agentes o los paquetes, o se desconecta el entorno; en Stone,
  Ajustes › Gateway Hub › Desconectar corta el puente al instante.
