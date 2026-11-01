# Stone Android

## Versión 1.3.3

La biblioteca se escanea al abrir o reanudar Stone, con una separación mínima de 15 minutos.
El botón **Escanear biblioteca** fuerza una actualización inmediata. No se ejecuta ningún
temporizador ni servicio de escaneo mientras la aplicación está en segundo plano.

## Release estable
- No instales builds debug en el telefono.
- Usa solo el flujo release para evitar el error rojo de Metro.

## Generar APK release
1. Abre la carpeta:
   - `C:\Users\ZIRAKUSA\BotCaja\musica\frontend`
2. Ejecuta:
   - `npm run android:release`

## Generar e instalar por ADB
1. Con el telefono conectado por USB y depuracion activa:
   - `npm run android:install`

## APK generado
- Ruta final:
  - `C:\Users\ZIRAKUSA\BotCaja\musica\frontend\android\app\build\outputs\apk\release\stone-1.2.6-arm64-v8a-release.apk`

## Tamano esperado
- La release real para telefono queda cerca de `25 MB`.
- Si ves algo tipo `100 MB` o mas, casi seguro estas usando debug y no release.

## Regla fija
- Si aparece `Unable to load script`, se instalo una build incorrecta.
- La entrega real debe salir siempre del flujo release.
- `npm run android` queda bloqueado a proposito.
- Si de verdad necesitas debug local, el comando separado es:
  - `npm run android:debug`
