# Arquitectura de carpetas de Stone (Android)

Paquete base: `com.zirakusa.stone.nativeapp` (módulo `app`) + módulo `core` (Kotlin puro, sin Android).
La carpeta de cada archivo coincide siempre con su `package`.

```text
core/                    Lógica pura y probada sin Android (letras, diseño de ventana, Song…)
app/…/nativeapp/
├── MainActivity.kt      Única Activity
├── ui/                  Armazón de la app
│   ├── StoneApp.kt · AutomationBridge.kt   Navegación y puente con el Modo API
│   ├── theme/           Colores, tema y look personalizado
│   ├── components/      Piezas visuales compartidas (portada, avatar, iconos)
│   └── system/          Barras del sistema, sacudida, superposición de volumen
├── feature/             Pantallas, una carpeta por función visible
│   ├── library/         Biblioteca: pantalla + ViewModel
│   │   ├── components/  Filas, tarjetas, pestañas, búsqueda, selección
│   │   └── video/       Cuadrículas y selección de videos
│   ├── player/          Reproductor (pantalla, mini reproductor, controles)
│   │   ├── lyrics/ · sheets/ · tags/ · video/
│   ├── settings/        Ajustes
│   │   ├── api/ · look/ · feedback/
│   └── onboarding/
├── playback/            Reproducción (sin interfaz)
│   ├── StonePlaybackService.kt · StoneWidgetProvider.kt   ⚠ componentes de Android
│   ├── control/         ViewModel, transiciones, motor Eco, portada del sistema
│   ├── audio/           Volumen, ecualizador, género
│   └── diagnostics/
├── data/
│   ├── persistence/     Base de datos, ajustes, cifrado de secretos
│   └── library/
│       ├── NotificationAlarmReceiver.kt                    ⚠ componente de Android
│       ├── scan/ · user/ · tags/ · lyrics/ · artwork/ · web/ · notifications/
└── automation/          Modo API (MCP)
    ├── ApiMode.kt       Fachada que usa la interfaz
    ├── auth/            Tokens, permisos, límite de intentos
    ├── server/          Servidor HTTP/MCP en 127.0.0.1
    ├── relay/           Enlace público cifrado (ver ../relay)
    ├── actions/         Herramientas que ven los agentes
    └── activity/        Registro y estadísticas
```

## Reglas

1. **Dependencias en una sola dirección:** `feature` → `playback`/`data`/`automation` → `core`.
   `data` y `playback` nunca importan `feature` ni `ui`.
2. **Una carpeta por función visible.** Una pantalla nueva va en `feature/<función>/`; si crece,
   se divide en subcarpetas por tema (como `player/lyrics`).
3. **⚠ No mover ni renombrar** `MainActivity`, `StonePlaybackService`, `StoneWidgetProvider`,
   `NotificationAlarmReceiver` ni los alias `.Home*`: Android los recuerda por su nombre completo
   (al moverlos se pierden widgets y alarmas, como pasó con los iconos en 1.6.1).
4. **Sin código oculto:** lo que no se puede usar desde la app o desde una herramienta del Modo
   API se borra. Nada de interruptores sin pantalla.
5. Después de mover o crear archivos: `python3 tools/update_map.py`.
