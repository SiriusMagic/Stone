# Stone para Android — migración a Kotlin

Kotlin + Jetpack Compose + Media3. No necesita Node, Expo ni un servidor.

## Abrir y compilar

Abre **esta carpeta `android-native`** en Android Studio. Instala Android SDK 35,
Build Tools 35.0.0 y usa JDK 21 para Gradle. No necesita Node, Expo ni un servidor.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

En Windows usa `gradlew.bat`. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
Para el APK descargable optimizado: `./gradlew :app:assemblePreview`.
Sale en `app/build/outputs/apk/preview/app-preview.apk`, con firma de desarrollo,
sin depuración y con reducción de código y recursos. No es una publicación estable.
La prueba usa `com.zirakusa.stone.kotlin.preview` y el nombre **Stone Kotlin · Prueba**.
La última versión generada es `1.7.29` (versionCode 63). El APK final de este cambio incluye
la distribución horizontal configurable y fuerza la actualización al girar la ventana; consulta
[`DISENO-HORIZONTAL.md`](DISENO-HORIZONTAL.md). `lintDebug` debe terminar con 0 avisos.
El contexto de continuidad y las decisiones de la entrega están en
[`../docs/STONE-CONTEXTO-ACTUAL.md`](../docs/STONE-CONTEXTO-ACTUAL.md).
En Git también se conserva `Stone-1.7.29-optimized.apk`: es la misma app con R8 y recursos
reducidos (aprox. 4,6 MB). `app-debug.apk` es el paquete de diagnóstico sin reducción
(aprox. 21,6 MB), preferible para comprobar una instalación cuando se sospecha de un APK
demasiado pequeño.
Pruebas de interfaz y capturas: `./gradlew :app:testDebugUnitTest`.
Las imágenes se escriben en `app/build/screenshots/`.
Se instala junto a Stone. El APK debug incluye el código Kotlin compilado y no necesita Metro.
No cambiar al identificador original antes de completar equivalencia y migración de datos.

## Stone Prueba para Lucía

[Stone Prueba 1.7.27](Stone-1.7.27-lucia-preview.apk) se instala junto a Stone y guarda
su propia biblioteca y ajustes. Abre **Stone Prueba › Ajustes › Modo MCP › Permitir MCP local**
para usarlo con Lucía Prueba 0.0.27. Esta variante usa un permiso de firma propio y funciona con
el relay apagado.

El APK se compila con `./gradlew :app:assembleLuciaPreview` y se firma con la misma clave
de desarrollo que Lucía Prueba.

## Organización

```text
android-native/
├── core/                           Modelos y reglas Kotlin sin Android; pruebas JVM
└── app/src/main/
    ├── java/com/zirakusa/stone/nativeapp/
    │   ├── MainActivity.kt         Entrada de Android
    │   ├── data/library/           Carpeta SAF, metadatos e índice local
    │   ├── feature/library/        Estado y pantalla de biblioteca
    │   ├── feature/player/         Pantalla, controles, cola y mini reproductor
    │   ├── playback/               Servicio Media3 y conexión de la interfaz
    │   └── ui/                    Composición de pantallas y tema de Stone
    └── res/                       Textos, estilos e icono original de Stone
```

Cada archivo tiene una responsabilidad. Las pantallas usan ViewModels; solo el servicio posee
el reproductor. Nuevas funciones se añadirán bajo `feature/<función>`; las reglas reutilizables
van en `core` y el acceso a Android en `data` o `playback`. No crear carpetas vacías para simular funciones.

## Primer recorrido implementado

Elegir carpeta → conservar permiso → cargar índice → buscar/agrupar canciones → reproducir →
mini reproductor → pantalla completa → cola, anterior/siguiente, avance, aleatorio y repetición.
Media3 controla la sesión y notificación; su funcionamiento en un teléfono requiere pruebas.

La biblioteca se revisa al iniciar y al volver a primer plano (mínimo 15 minutos entre
escaneos automáticos dentro del mismo proceso), o manualmente. No hay sondeo periódico.
Se reutilizan metadatos si tamaño y fecha siguen iguales. Ante errores de lectura no se
reemplaza el índice anterior. Al pasar a segundo plano se solicita cancelar el escaneo;
una lectura del proveedor ya iniciada puede terminar antes de observar la cancelación.
Solo la barra de progreso del reproductor visible se actualiza cada segundo durante reproducción.

**Esta es una primera versión de prueba, no la migración completa.** Consulta el contexto de
continuidad y [`DISENO-HORIZONTAL.md`](DISENO-HORIZONTAL.md) para el estado, equivalencia,
pruebas y pendientes.
