# Stone 1.4.4 — avance de eficiencia hacia 1.5.x

La versión 1.4.4 incorpora de forma anticipada mejoras planificadas para 1.5.x,
sin convertirlas todavía en opciones visibles para el usuario.

## Alcance de 1.4.4

- Cola estable: reordenar, reproducir y eliminar elementos sin índices inválidos.
- Menos trabajo repetido del reproductor y del ecualizador.
- Letras sincronizadas con desplazamiento y resaltado eficiente.
- Volumen Stone unificado, sin depender del panel nativo cuando la reproducción está activa.
- Modo de ahorro accesible desde el miniplayer.
- Fondo dinámico calculado únicamente al cambiar de canción.
- Primera base de medición para RAM, CPU y estabilidad.

## Adelanto de 1.5.x

Las siguientes mejoras se implementarán progresivamente sin añadir complejidad a la interfaz:

1. Reducir recomposiciones y actualizaciones periódicas innecesarias.
2. Cargar carátulas al tamaño necesario y limitar cachés.
3. Escanear únicamente archivos nuevos o modificados.
4. Liberar recursos de pantallas cerradas y efectos de audio no utilizados.
5. Registrar mediciones locales de rendimiento solo durante las pruebas.
6. Comparar RAM, CPU, APK y estabilidad antes de publicar cada salto grande.

## Regla de versiones

- **1.4.4:** estabilización y primer bloque de optimización adelantada.
- **1.5.0:** consolidación del motor de eficiencia y mediciones repetibles.
- **1.5.x:** correcciones, ajustes y optimizaciones incrementales.
- **1.6.0:** Performance Lab y copia manual de respaldo del cuaderno.

No se añade sincronización automática con GitHub ni servicios permanentes.
