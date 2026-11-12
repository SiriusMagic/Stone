# Diseño horizontal de Stone

El ajuste está en **Ajustes → Personalización → Diseño horizontal**. Se conserva
entre sesiones y no modifica los perfiles ni el orden de los iconos de inicio.

- **Siempre las tres zonas** (predeterminado): al girar desde la biblioteca o el
  reproductor de música aparecen navegación lateral, biblioteca y reproductor.
- **Solo desde la biblioteca**: la biblioteca usa las tres zonas, pero el reproductor
  abierto mantiene su pantalla independiente. La carátula lateral permite abrirla.

En vertical se conserva la interfaz anterior. Al regresar se recupera la pantalla
anterior; pulsar Inicio en la barra lateral vuelve explícitamente a Canciones.
Se conservan consulta, orden, pestaña, colección y desplazamiento de la biblioteca.

## Adaptación al espacio disponible

Las medidas usan dp y descuentan los márgenes seguros del sistema.

| Ancho horizontal | Navegación | Contenido |
| --- | --- | --- |
| Menos de 600 dp | Iconos, 64 dp | Biblioteca y minirreproductor; este abre la pantalla completa |
| 600–719 dp | Iconos, 64 dp | Biblioteca y reproductor lateral |
| 720–1199 dp | Iconos, 72 dp | Biblioteca y reproductor lateral |
| Desde 1200 dp | Iconos y nombres, 160 dp | Biblioteca y reproductor lateral |

El espacio restante se divide aproximadamente 60/40, con mínimos de 280 dp para
la biblioteca y 240 dp para el reproductor. Las acciones laterales y pestañas se
pueden desplazar. La carátula se reduce con la altura y el tamaño de letra;
los controles de reproducción permanecen accesibles.

## Reutilización y consumo

- Sigue existiendo un solo `PlaybackViewModel` y servicio multimedia. El giro y
  la preferencia de distribución no cambian cola, posición, volumen ni reproducción.
- No se ejecutan escaneos por cambiar de orientación. Las portadas reutilizan la
  caché existente; no se añaden dependencias ni imágenes de decoración.
- Solo la presentación visible consulta el progreso, mientras la actividad está
  en primer plano. La actualización periódica se detiene si está pausada y los
  eventos del reproductor actualizan los saltos de posición.
- Letras, cola, opciones y ecualizador comparten el mismo estado y sus paneles.
- Vídeo abierto y PiP conservan sus reproductores dedicados. Al navegar por la
  biblioteca, el panel lateral muestra una portada con acceso a vídeo, sin crear
  otro reproductor ni una segunda superficie de vídeo.

## Verificación

Las pruebas cubren política de distribución, persistencia de la preferencia,
restauración de navegación y paneles, accesos personalizados, callbacks de
reproducción, biblioteca vacía, vídeo y capturas de 640×360, 891×411, 1280×800 y
560×320 dp. Se comprueba también fuente al 150 % con márgenes laterales.
Las pruebas existentes conservan las capturas verticales y el caso de 360×780 dp.

Comando de validación:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assemblePreview :app:lintDebug --continue
```

Las capturas se generan en `app/build/screenshots/`. `assemblePreview` genera el
APK optimizado correctamente y `lintDebug` termina con 0 errores y 0 avisos.
La Activity conserva el servicio de reproducción al girar, pero ahora publica el
nuevo `orientation` y el composable también usa las dimensiones reales de la ventana;
por eso el cambio a las tres zonas no depende de una recreación tardía de Android.
En la validación de este cambio pasaron 14 pruebas de core y 30 de la app. Las
tres capturas verticales (`stone-home-empty`, `stone-home-songs` y
`stone-home-miniplayer`) conservaron exactamente el mismo SHA-256 que antes
del cambio de distribución.
No se dispone de un teléfono conectado para medir RAM/batería, continuidad audible,
Bluetooth o PiP real. Estas verificaciones siguen pendientes; no se afirma una
reducción numérica de consumo a partir de pruebas de interfaz.
