# Rendimiento de Stone

## Referencias y objetivos

- La versión 1.5.3 publicada contiene un APK optimizado de 3.306.377 bytes. El tamaño
  descargado, el espacio instalado y la memoria PSS son medidas diferentes.
- La compilación optimizada 1.5.4 ocupa 3.305.017 bytes: 1.360 bytes menos que el APK
  anterior. El espacio instalado y la memoria de esta versión siguen pendientes de medir.
- La captura facilitada por el usuario muestra 11,91 MB de espacio instalado y 137 MB
  de PSS; son referencias aproximadas, no una medición controlada de esta versión.
- Se busca reducir ambos valores y el consumo de CPU sin quitar funciones. La cifra de
  63,9 % de CPU es una muestra aislada y no demuestra consumo sostenido.

## Medición comparable en el teléfono

1. Antes de actualizar, anotar versión, dispositivo, Android, almacenamiento de la app,
   biblioteca, canción, salida de audio y modo. Conservar los datos de la aplicación.
2. Con la misma canción y pantalla, dejar estabilizar la reproducción y ejecutar el
   diagnóstico durante un minuto. Repetir tres veces y guardar las muestras exportadas.
3. Actualizar sin desinstalar, repetir exactamente el mismo procedimiento y comparar
   espacio instalado, mediana y picos de PSS y CPU. No comparar la RAM total del teléfono
   con la del proceso. Probar también con la pantalla de diagnóstico cerrada.
4. Registrar si hubo escaneo, efectos activos, pantalla apagada u otras diferencias;
   son factores que pueden modificar los resultados. El porcentaje de CPU usa un núcleo
   como 100 %, por lo que un proceso multinúcleo puede superar el 100 %.

## Cambios de esta versión

- El reproductor y la ventana de ecualizador comparten una lectura de progreso. La
  cadencia de 250 ms se mantiene para no degradar letras sincronizadas ni vídeo.
- El diagnóstico mantiene una sola lista de muestras y entrega los cambios de la interfaz
  en el contexto de quien inició la sesión; la lectura pesada sigue en segundo plano.
- Las consultas agrupadas de biblioteca se hacen fuera del hilo de interfaz. Se evita
  crear un lector multimedia al cargar una portada que ya es un archivo de imagen.
- Se elimina el código sin referencias de la pantalla de información técnica retirada.
  Los perfiles de ecualizador antiguos se conservan en la base de datos, pero ya no se
  muestran; no se borran canciones, listas ni ajustes.

Las mejoras de memoria y CPU deben verificarse en el dispositivo; el tamaño reducido del
APK por sí solo no demuestra menor consumo de RAM o batería.
