# AI RULES

Objetivo: cambios rápidos con mínima lectura.

## Reglas obligatorias

- Leer `MAP.md` antes de editar.
- No leer archivos completos si no es necesario.
- No abrir más de 2 archivos por tarea.
- No ejecutar `npm run dev`.
- Validar solo con `npm run check` o `npm run build`.
- Para pruebas Android sin PC ni Metro, instalar solo APK `release`; `debug` requiere Metro y puede mostrar "Unable to load script".
- En Stone para Android, biblioteca y descargas deben usar la carpeta fija elegida por el usuario mediante SAF. No volver al modelo de almacenamiento solo interno salvo que el flujo lo pida de forma explicita.
- Error ya detectado: si queda `expo-media-library` activo o permisos heredados de almacenamiento/microfono en Android sin uso real, el flujo de aceptacion de permisos puede cerrar la app. No reintroducir `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `RECORD_AUDIO` ni el plugin `expo-media-library` salvo que exista una pantalla funcional que realmente los use.
- Error ya detectado: en Android nativo no asumir APIs web del navegador. Antes de usar `window.addEventListener`, `window.removeEventListener`, `window.dispatchEvent` o `CustomEvent`, validar que existan; si no, la app puede cerrarse al montar la pantalla.
- Si localhost ya responde, no reiniciar nada.
- Si un comando no termina, detenerlo.
- No preguntar "¿ya terminó?".
- No crear archivos grandes.
- Si un botón o bloque se repite, moverlo a `shared` o a un subcomponente cercano.
- Si se divide un archivo, mantener compatibilidad pasando `props` y handlers desde el archivo dueño.

## Límites

- Archivo visual recomendado: 100 líneas.
- Intervalo aceptable: hasta 150 líneas.
- Componente ideal: 60 líneas.
- Hook ideal: 35 líneas.
- Función ideal: 20 líneas.
- Archivo de estilos ideal: 60 líneas.

## Regla dura

- Si llega a 100 líneas, dividir.
- Si llega a 120 líneas, refactor obligatorio.
- Si llega a 200 líneas, archivo prohibido.

## Si se supera el límite

Dividir antes de agregar funciones.

## Orden de lectura

1. Leer `MAP.md`.
2. Abrir solo el archivo indicado.
3. Abrir máximo una dependencia.
4. Editar.
5. Ejecutar `npm run check`.
