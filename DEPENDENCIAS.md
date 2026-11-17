# Dependencias de Stone

Revisado el 17-11-2026 contra la base de datos pública de vulnerabilidades **OSV.dev**
(la de Google, que reúne GitHub Advisories, NVD y otras), dependencia por dependencia,
incluidas las que llegan arrastradas por otras.

| Parte | Paquetes | Vulnerables | Licencias |
|---|---|---|---|
| App (va dentro del APK) | 168 | **0** | Todas Apache-2.0 |
| Compilación (Gradle, Kotlin) | 2 plugins | **0** | Apache-2.0 |
| Solo pruebas (no van en el APK) | 63 | **0** | Apache-2.0, MIT, BSD, EPL-1.0, CDDL+GPLv2-CE, Bouncy Castle, Unicode |
| Relay (`npm`, solo herramienta de publicación) | 38 | **0** (`npm audit`) | MIT, Apache-2.0, ISC, 0BSD, CC0, LGPL-3.0 |

Política: **la versión segura más antigua que ya lleve tiempo publicada**, no la última.
Lo que ya es seguro se deja para la migración siguiente (compileSdk 37 + AGP 9, ver `android-native/app/lint.xml`).

## Árbol (lo que va dentro del APK)

```text
Stone (app)
├── Kotlin 2.4.21 ............................... stdlib (JetBrains, Apache-2.0)
├── kotlinx-coroutines-android 1.11.0 ........... tareas en segundo plano
├── :core ...................................... código propio, sin dependencias
├── Jetpack Compose (BOM 2026.06.01) ............ interfaz (Google, Apache-2.0)
│   ├── compose-ui · foundation · animation · runtime 1.11.4
│   ├── material3 1.4.0
│   └── material-icons-extended 1.7.8
├── activity-compose 1.13.0
├── lifecycle-runtime-compose / viewmodel-compose 2.10.0
├── Media3 1.6.1 ................................ reproductor (Google, Apache-2.0)
│   ├── media3-exoplayer
│   │   ├── media3-common · datasource · decoder · extractor · container
│   │   └── Guava 33.7.2-android ⚠ forzada (ver abajo)
│   │       └── failureaccess · jspecify · error_prone_annotations · j2objc
│   ├── media3-session → androidx.media 1.7.0
│   └── media3-ui → recyclerview 1.3.0
└── OkHttp 5.3.2 ................................ WebSocket del relay (Square, Apache-2.0)
    └── Okio 3.16.4
```

## Compilación

```text
Gradle 8.14.3
├── Android Gradle Plugin 8.13.2
└── Kotlin Gradle Plugin 2.4.21 (+ plugin de Compose 2.4.21)
```

## Relay (`relay/`)

```text
stone-relay
└── wrangler 4.162.0 (solo para publicar y probar en local; NO se sube a Cloudflare)
    └── miniflare, esbuild, workerd, sharp/libvips (LGPL, solo en el ordenador) …
```

El Worker publicado (`relay/src/index.js`) **no tiene ninguna dependencia**: solo usa las APIs de Cloudflare.

## Cambios de esta revisión

| Paquete | Antes | Ahora | Motivo |
|---|---|---|---|
| Guava (llega con Media3) | 33.3.1-android | **33.7.2-android** | GHSA-xxph-c9ww-hj94 / CVE-2026-102554 (alta): al deserializar ciertas colecciones se podía reservar memoria sin límite. Stone no deserializa objetos de Java, pero se corrige igualmente. Se eligió la versión que lo arregla y no la 33.8.0, publicada hace solo dos semanas. |
| Kotlin (compilador y stdlib) | 2.3.21 | **2.4.21** | GHSA-r937-wjx7-w2jp / CVE-2026-53914 (moderada): ejecución de código a través de la caché de compilación. Solo afecta al ordenador que compila, no al APK. 2.4.21 es la versión de corrección de la rama 2.4, publicada hace más de un mes. |
| `ui-tooling-preview` | 1.x | **eliminada** | No se usaba. |

## Cómo repetir la revisión

```sh
cd android-native && ./gradlew :app:dependencies --configuration releaseRuntimeClasspath
# cada grupo:artefacto:versión → POST https://api.osv.dev/v1/querybatch
cd ../relay && npm audit
```
