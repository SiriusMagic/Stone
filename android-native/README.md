# Stone para Android — migración a Kotlin

Proyecto independiente en Kotlin + Jetpack Compose + Media3. Referencia funcional y visual:
Stone 1.3.2, conservada en `../frontend/`. El trabajo previo de 1.3.3 continúa allí.
La aplicación nativa se escribe directamente desde Stone. No reutiliza la implementación de Lica/Sren.
El ZIP nativo contiene esta carpeta y la documentación, sin dependencias ni archivos de
compilación. El código original continúa conservado por separado en `frontend/` y `backend/`
dentro del espacio de trabajo y no se modifica ni se mezcla con la app Kotlin.

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
La última versión generada es `1.5.6` (versionCode 16). El APK final de este cambio incluye
la distribución horizontal configurable; consulta [`DISENO-HORIZONTAL.md`](DISENO-HORIZONTAL.md).
Pruebas de interfaz y capturas: `./gradlew :app:testDebugUnitTest`.
Las imágenes se escriben en `app/build/screenshots/`.
Se instala junto a Stone. El APK debug incluye el código Kotlin compilado y no necesita Metro.
No cambiar al identificador original antes de completar equivalencia y migración de datos.

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

**Esta es una primera versión de prueba, no la migración completa.** Consulta
[`../docs/MIGRACION-KOTLIN.md`](../docs/MIGRACION-KOTLIN.md) para equivalencia, pruebas y pendientes.
El detalle de la recuperación visual está en [`../docs/INICIO-PREVIEW02.md`](../docs/INICIO-PREVIEW02.md).
