# Stone — nota sobre el origen de esta copia

Copia del código de **Stone** (`com.zirakusa.stone`, versión 1.3.2), hecha por el dueño con Codex.
Origen: rama `release/stone-1.3.2` y etiquetas `stone-1.3.2` / `v1.3.2` de este repositorio. El APK está
en `apk/legado/stone-1.3.2.apk` del Codespace (no se guarda en git).

- Tecnología: Expo 54 / React Native 0.81 (`frontend/`) + módulos nativos en Kotlin
  (`frontend/android/app/src/main/java/com/zirakusa/stone/`) + un servidor Python (`backend/`).
- Mapa del código: `frontend/MAP.md`.
- Compilar: `frontend/STONE_ANDROID.md` (`npm run android:release`).

Este archivo conserva su nombre histórico. En este proyecto independiente, Stone es la
referencia del producto. La migración actual está en `android-native/` y se sigue en
`docs/MIGRACION-KOTLIN.md`. El servidor de descargas se conserva en `backend/`.
