# Stone 1.4.2 — lote de 30 cambios

Este lote reemplaza la revisión 1.4.1. No se modifica el APK 1.3.2 instalado: la aplicación Kotlin conserva el identificador de vista previa.

1. Ajustes se abre desde el icono de configuración en su propio panel.
2. Las páginas de preferencias ya no apilan hojas modales.
3. La flecha Atrás vuelve del detalle al índice de Ajustes.
4. El panel permite desplazamiento en pantallas compactas.
5. Se conservan las tres variantes visuales: negro, gris y claro.
6. La selección del tema se aplica al reconstruir la actividad.
7. Los temas se guardan con validación de valores permitidos.
8. El selector ofrece solo español e inglés, idiomas implementados.
9. Se incorpora catálogo de recursos completo en inglés para la interfaz nativa.
10. El idioma guardado se aplica al contexto de Android al abrir la actividad.
11. La preferencia de idioma persiste entre sesiones.
12. “Carpeta de Stone” abre el selector de carpeta.
13. “Escanear biblioteca” escanea la carpeta existente o la solicita si falta.
14. “Archivos ocultos” abre su lista real desde Ajustes.
15. “Eliminado recientemente” abre su lista real desde Ajustes.
16. Se eliminó el enrutamiento erróneo de esas filas al selector de carpeta.
17. “Reproduce junto con otras aplicaciones” controla el manejo del foco de audio.
18. Media3 aplica esa preferencia también al conectar el reproductor.
19. Se quitó el interruptor ficticio de reproducción sin pausas.
20. Se informa que Media3 gestiona automáticamente las transiciones sin pausas.
21. Se quitó el control de crossfade que no producía efecto de audio.
22. Crossfade se identifica explícitamente como no disponible todavía.
23. Se quitaron interruptores de notificación que solo guardaban un valor sin notificar.
24. El estado de notificaciones de archivos nuevos indica que aún no está disponible.
25. Las recomendaciones push se presentan como no disponibles sin backend.
26. Las etiquetas de notificación ya no sugieren acciones inexistentes.
27. Los presets del temporizador muestran cuál está activo.
28. Elegir un tiempo no cierra a la fuerza todo el panel de Ajustes.
29. La pantalla de descargas mantiene su aviso de no disponibilidad, sin acción falsa.
30. Gradle y la documentación avanzan consecutivamente a 1.4.2 / versionCode 7.

La compilación Kotlin se usa como comprobación técnica del lote. El APK de este lote se genera al completar estos 30 cambios.
