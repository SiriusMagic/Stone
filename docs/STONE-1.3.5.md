# Stone Kotlin 1.3.5

Segundo lote de diez funciones del reproductor:

1. Editor de título, artista, álbum, género, año y número de pista para MP3.
2. Ecualizador real enlazado a la sesión de audio de Media3.
3. Cinco presets y diez bandas configurables entre -10 y +10.
4. Velocidad de reproducción de 0.5X a 2X, conservada al reiniciar.
5. Compartir el archivo mediante el selector seguro de Android.
6. Crear y establecer un tono desde un recorte de hasta 30 segundos.
7. Temporizador de 10, 20, 30, 60 o 90 minutos, con opción de terminar la canción.
8. Pantalla de ahorro energético sin carátula, progreso ni animaciones continuas.
9. Mover canciones a Archivos ocultos sin borrar el archivo físico.
10. Mover canciones a Eliminado recientemente sin borrado físico inmediato.

Las etiquetas conservan los frames ID3 desconocidos y solo reemplazan los seis campos editables.
Ocultar y borrar siguen el comportamiento seguro de Stone 1.3.2: son operaciones lógicas y
reversibles. Las pantallas para restaurar esos elementos se conectarán con la biblioteca en el
lote correspondiente. Agregar a playlists permanece visible, pero se implementará en 1.3.6.

El tono solicita el permiso especial de Android para modificar ajustes. La escritura de etiquetas
requiere que el proveedor de la carpeta SAF permita escritura; si solo permite lectura, Stone
muestra el error y no altera el archivo.
