# Library Structure

Objetivo: hacer cambios rápidos sin releer archivos enormes.

## Regla nueva

- Máximo recomendado por archivo visual: `220` líneas.
- Si una pantalla supera eso, se divide antes de seguir agregando funciones.
- Las pestañas visibles viven fuera de `app/library.tsx`.
- Los datos cortos y textos repetidos deben ir a `constants.ts` o al archivo del componente dueño.

## Mapa rápido

- `app/library.tsx`
  - Solo estado, navegación local, modales y conexión entre piezas.
- `components/LibraryTabContent.tsx`
  - Contenido de `Videos`, `Canciones`, `Playlists`, `Carpetas`, `Artistas`, `Álbumes`.
- `components/LibraryTabs.tsx`
  - Barra horizontal de pestañas.
- `components/HiddenFilesModal.tsx`
  - Archivos ocultos.
- `components/RecentDeletedModal.tsx`
  - Eliminado recientemente.
- `components/SettingsSheet.tsx`
  - Ajustes.
- `components/SleepTimerSheet.tsx`
  - Temporizador.
- `components/LanguageSheet.tsx`
  - Idioma.
- `styles.ts`
  - Estilos compartidos actuales.

## Siguiente recorte recomendado

- Dividir `styles.ts` por zonas:
  - `styles/core`
  - `styles/library-tabs`
  - `styles/modals`
  - `styles/hidden-files`
- Hacer lo mismo después con `app/player.tsx`, que sigue siendo el archivo más grande.
