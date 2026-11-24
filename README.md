# Stone

Reproductor de música y video para Android (Kotlin + Jetpack Compose + Media3) con servidor MCP dentro del
teléfono y un relay en Cloudflare para tener un enlace público.

- **`android-native/`**: la app. [Guía de compilación](android-native/README.md).
- **`relay/`**: enlace público `https://…/d/<teléfono>/mcp` (Cloudflare Worker).
- **`docs/`**: guía del MCP y planes vigentes.
- APK: `android-native/Stone-<versión>-optimized.apk`.

La versión web antigua (Expo/React Native y servidor de descargas) ya no está aquí. Si hace falta verla:
`git checkout a115471` (respaldo completo) o el commit `4f880a7` (último con sus notas).
