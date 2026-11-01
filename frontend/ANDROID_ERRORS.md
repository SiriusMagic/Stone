# Android Errors

## Error rojo: "Unable to load script"
- Causa: instalar una build debug o dev client que intenta leer JavaScript desde Metro.
- Sintoma: la app pide `localhost:8081`, PC encendida o Metro activo.
- Regla fija: no instalar debug en el telefono para pruebas reales.
- Solucion aplicada:
  - release con bundle embebido
  - bloqueo del comando `npm run android`
  - instalacion por ADB desde el APK release `arm64-v8a`
- Comando permitido:
  - `npm run android:release`
  - `npm run android:install`
- Comando no permitido para entrega:
  - `npm run android`
  - instalar APK debug

## Abrir con Android
- Causa tipica de fallo: filtros demasiado orientados a navegador en vez de archivos locales.
- Solucion aplicada:
  - filtros `VIEW` con `DEFAULT`
  - soporte para `audio/*` y `video/*`
  - soporte para `content://` y `file://`

## Escaneo de biblioteca
- Regla fija:
  - no hacer escaneo periodico
  - actualizar solo al elegir carpeta, escanear manualmente, importar o terminar descarga

## Regla de entrega Android
- La APK valida para telefono es la release `arm64-v8a`.
- La build debug no se entrega ni se instala para pruebas reales.
- Version estable actual:
  - `Stone 1.2.6`
  - `versionCode 13`
  - `release arm64-v8a ~25 MB`
