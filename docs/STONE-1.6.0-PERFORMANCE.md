# Stone 1.6.0 — rendimiento y consumo

La meta de 1.6.0 es reducir el consumo total sin eliminar funciones: memoria, CPU, batería,
tamaño y actividad en segundo plano se medirán antes de publicar.

## Principio de la etapa

Las mejoras de eficiencia serán invisibles para el usuario: no añadirán opciones innecesarias ni
complicarán la interfaz. Cada versión deberá conservar la reproducción, la biblioteca, la cola,
las notificaciones y el funcionamiento sin conexión.

## Prioridades hasta 1.6.0

1. Performance Lab automático: APK, RAM, CPU, estabilidad y regresiones.
2. Escaneo incremental de canciones.
3. Carga inteligente de carátulas y fotogramas.
4. Detección de fugas de memoria.
5. Audio offload con fallback automático.
6. Arranque rápido.
7. Suspensión de tareas innecesarias.
8. Recuperación de reproducción tras fallos.
9. Caché adaptativa según memoria disponible.
10. Diagnóstico local únicamente bajo demanda.

Orden de implementación: medir primero; después optimizar carátulas, escaneo y fugas; luego
offload, arranque, suspensión y recuperación. Ninguna mejora se aceptará si rompe la reproducción
o aumenta de forma sostenida el consumo energético.

## Stone Efficiency Score

Cada versión podrá recibir una puntuación técnica de 0 a 100, normalizada contra los mismos
dispositivos y objetivos de referencia:

| Área | Peso |
| --- | ---: |
| Batería | 35 |
| RAM | 25 |
| CPU | 20 |
| Estabilidad | 15 |
| Tamaño del APK | 5 |

No será una cifra publicitaria ni una medición universal: el informe incluirá dispositivo, Android,
salida de audio, códec, volumen, temperatura y condiciones de prueba.

## Matriz de compatibilidad

Las pruebas deben cubrir Android antiguo, gama baja, gama media y un dispositivo con DAC externo.
Offload, ahorro y alta fidelidad se marcarán como compatibles, no disponibles o no comprobados;
nunca se asumirá que una capacidad detectada equivale a una mejora activa.

## Presupuesto inicial

| Escenario | Objetivo PSS | Límite de revisión |
| --- | ---: | ---: |
| Pantalla apagada, audio reproduciendo | 40 MB aspiracional | 80 MB |
| Stone en segundo plano | 60 MB aspiracional | 125 MB |
| Navegación por biblioteca | 80 MB aspiracional | 130 MB |

También se registrarán APK, CPU, temperatura, interrupciones, servicios activos y conexiones de
red. Estos valores no se consideran mediciones hasta probarlos en un dispositivo real.

## Primera optimización aplicada

- Las carátulas y fotogramas de video usan cachés limitadas según `ActivityManager.memoryClass`.
- Android puede vaciarlas cuando informa presión de memoria.
- Los fotogramas de video se reducen a un máximo de 640 px para evitar conservar imágenes grandes.
- No se añadió un proceso, servicio, hilo periódico ni conexión de red.

## Stone Performance Lab

Antes del APK de 1.6.0 se compararán tres escenarios con la versión anterior: biblioteca de 100
canciones, biblioteca de 5.000 canciones y una hora de reproducción local con la pantalla apagada.
La mediana de cinco sesiones será la referencia; un aumento sostenido superior al 5% requiere
revisión y superar repetidamente el límite bloquea la publicación.

La RAM se medirá como PSS con `dumpsys meminfo`, CPU con `top`/Perfetto y batería con
`batterystats` o medición externa. El ahorro energético solo se afirmará si es repetible.
