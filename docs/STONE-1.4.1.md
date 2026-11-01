# Stone Kotlin 1.4.1

Cambio grande de persistencia, dividido en diez garantías:

1. Base SQLite versionada `stone-user-data.db` como fuente de verdad para datos del usuario.
2. Migración automática y transaccional desde las preferencias de 1.3.3–1.3.6.
3. Playlists y sus canciones migradas sin depender del índice multimedia.
4. Favoritos migrados a la base.
5. Historial y conteos de reproducción migrados.
6. Letras agregadas manualmente migradas.
7. Estados de ocultos y eliminados migrados y reversibles.
8. Velocidad, ecualizador, bandas y preset migrados.
9. Fila, canción, posición, aleatorio y repetición restaurados al volver a abrir Stone.
10. Exportación e importación JSON versionada desde Ajustes, con importación transaccional.

Las preferencias antiguas no se eliminan en esta versión: permanecen como respaldo de seguridad.
La caché `library-index.json` sigue siendo descartable y no contiene datos creados por el usuario.

La copia no incluye los archivos musicales ni concede permisos SAF en otro teléfono. Después de
importarla en otro dispositivo hay que volver a elegir la carpeta de Stone; las coincidencias dependen
de que Android entregue las mismas URI. La importación fusiona los valores de la copia y no borra
datos ajenos que ya existan.

Android impide leer directamente los datos privados de Stone 1.3.2 porque usa otro paquete. Una
migración desde esa app necesitará una exportación realizada por la propia 1.3.2 o una futura versión
firmada con su certificado original; 1.4.1 no intenta evadir esa protección.
