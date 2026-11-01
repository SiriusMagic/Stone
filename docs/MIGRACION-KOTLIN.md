# Stone 1.3.2 → Stone Kotlin

## Fuente y conservación

Única referencia de producto: Stone 1.3.2, obtenida de `legado/stone` del repositorio de origen.
[Código de referencia en GitHub](https://github.com/SiriusMagic/lica-player/tree/002c1f01f7b8ef636aa24ad07dc40bb2417bb6e0/legado/stone).
`frontend/` conserva el código React Native y los cambios locales previos de 1.3.3;
`backend/` conserva el servidor de descargas. Esta migración agrega `android-native/` sin
reemplazar esas carpetas. El icono y la paleta provienen de Stone.

La prueba se instala con un paquete diferente. No importa todavía favoritos, listas,
historial, permisos ni ajustes de la app instalada. La versión original sigue siendo la app
de uso diario mientras se comprueba la equivalencia. La selección SAF se mantiene porque
es el acceso de Stone a los archivos; cambiarlo por un escaneo global de MediaStore alteraría el producto.

## Registro de equivalencia

La matriz detallada y actualizada para la app de vista previa 1.4.2 está en
[`COMPARACION-STONE-1.3.2-KOTLIN-1.4.2.md`](COMPARACION-STONE-1.3.2-KOTLIN-1.4.2.md).
Ese documento separa lo implementado en código, lo parcial, lo pendiente y lo que se dejó
intencionalmente fuera de alcance. La tabla anterior se retiró porque mezclaba revisiones
históricas y podía sugerir equivalencia completa sin validación en dispositivo.

Detalle de la corrección del inicio: [INICIO-PREVIEW02.md](INICIO-PREVIEW02.md).
Índice archivo por archivo y versiones: [INDICE-MIGRACION.md](INDICE-MIGRACION.md).

## Orden de trabajo

1. Validar el recorrido carpeta → biblioteca → reproducción en Android real.
2. Completar identidad visual, portadas, gestos, bienvenida, idiomas y ajustes.
3. Trasladar favoritos, listas, historial, cola persistente y su exportación/importación.
4. Trasladar letras, audio avanzado, temporizador, vídeos y acciones de archivos.
5. Completar pantallas auxiliares y URI entrantes. Las descargas siguen fuera de alcance según decisión previa.
6. Comparar pantalla por pantalla con Stone 1.3.2 antes de una versión de sustitución; usa la matriz actualizada enlazada arriba.

## Pruebas de aceptación del primer recorrido

- Elegir carpeta y subcarpetas; cancelar selector; carpeta vacía; archivo incompatible.
- Reabrir proceso y conservar acceso e índice. Denegar/revocar permisos sin borrar el índice anterior.
- Agregar, borrar y modificar canciones; pulsar Actualizar. Reutilizar solo metadatos sin cambios.
- Pulsar repetidamente Actualizar y alternar primer/segundo plano: un escaneo a la vez,
  sin tareas periódicas. Tras fallos/cancelación permitir un nuevo intento.
- Buscar y cambiar agrupación sin duplicar canciones; reproducir la selección correcta.
- Pausar, avanzar, retroceder, buscar posición, cambiar aleatorio/repetición y elegir cola.
- Apagar pantalla, cambiar app, retirar auriculares y probar controles de notificación/Bluetooth.
- Rotar la pantalla y volver desde la notificación sin crear reproductores duplicados.
- Medir batería, memoria y tiempo de escaneo en el mismo teléfono y biblioteca; no inferir
  ahorro real únicamente por estar escrito en Kotlin.

No publicar como reemplazo de Stone 1.3.2 hasta cerrar los pendientes funcionales y verificar
el traspaso de datos. Mantener `com.zirakusa.stone` en una futura versión exige la firma original
y una estrategia probada para sus datos; la prueba usa otro identificador deliberadamente.
