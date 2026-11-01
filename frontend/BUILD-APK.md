# Build APK

Desde `C:\Users\ZIRAKUSA\BotCaja\musica\frontend` ejecuta:

```powershell
.\build-apk.cmd
```

Para limpiar antes de compilar:

```powershell
.\build-apk.cmd -Clean
```

Para compilar e instalar por ADB sin borrar los datos de Stone:

```powershell
.\build-apk.cmd -Install
```

El APK queda en `dist\Stone-1.3.0-arm64-v8a.apk`.
