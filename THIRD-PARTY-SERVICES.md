# Servicios de terceros que usa Stone

Stone funciona **sin cuenta y sin servidor propio**: tu música está en el teléfono. Solo sale a
internet cuando tú (o un agente con tu token) pides letras, etiquetas o una portada, o cuando
activas el Modo API con enlace público. Todo va por HTTPS. Ninguno de estos servicios recibe tus
tokens ni tu biblioteca: solo lo que se busca (título y artista).

| Servicio | Para qué | Qué se envía | Cuándo | Clave | Dónde en el código |
|---|---|---|---|---|---|
| **LRCLIB** (`lrclib.net`) | Letras sincronizadas | Título, artista, álbum, duración | Al buscar letras (botón o agente) | No | `data/library/lyrics/LrcLibClient.kt` |
| **iTunes Search API** (`itunes.apple.com`) | Sugerir etiquetas y portadas | Título y artista | "Completar etiquetas" | No | `data/library/tags/TagSuggestions.kt` |
| Servidor de la portada elegida | Descargar la imagen | La dirección de la imagen | Al aplicar una portada | No | `data/library/artwork/CoverDownloader.kt` |
| **Google, Letras.com, Genius, Musixmatch** | Navegador de letras dentro de la app | La búsqueda que tú escribes; cada web pone sus cookies | Solo si abres "Buscar en la web" | No | `feature/player/lyrics/LyricsSearchBrowser.kt` |
| **Cloudflare Workers** (`stone-relay.<cuenta>.workers.dev`) | Enlace público del Modo API | Las peticiones del agente, cifradas por HTTPS | Solo con el Modo API activado | Secreto propio del teléfono (no tu token) | `relay/`, `automation/relay/` |
| **Stone Hub** (`stone-hub.<cuenta>.workers.dev`, Cloudflare) | Un solo enlace MCP para varias apps | Lo que pida la IA con el token del hub; las notas y el enlace de Stone se guardan en su Durable Object | Solo si lo conectas a una IA | Token del hub (secreto de Cloudflare) | `hub/` |
| **GitHub** (`github.com/SiriusMagic/Stone`) | Enlace al código y a las descargas | Nada (solo se abre el enlace) | Si lo tocas en Ajustes | No | `feature/settings/LegalScreens.kt` |

Stone respeta los límites de cada servicio: espera entre peticiones y, si alguno responde
"demasiadas peticiones" (HTTP 429), se detiene y espera (`data/library/web/WebPacer.kt`).

## Letras: de quién son y qué se puede hacer

> Esto no es asesoramiento legal; es la regla general. Si publicas Stone en Google Play o con
> muchos usuarios, consulta a un abogado de propiedad intelectual de tu país.

- **Las letras tienen derechos de autor**, igual que la música. Pertenecen al autor y a su
  editorial, no a quien las sube a una web ni a Stone. Una canción "que no es mía" sigue siendo
  de sus autores aunque tengas el disco.
- **LRCLIB** es una base de datos comunitaria y gratuita. Su API no pide clave, pero **no da
  licencia sobre las letras**: solo las aloja, y los derechos siguen siendo de sus titulares.
- **Musixmatch, Genius y Letras.com** sí tienen acuerdos con editoriales para **mostrar** letras
  en su propia web. Stone solo abre esas páginas en un navegador: ese acuerdo no pasa a Stone ni
  a ti para copiarlas o repartirlas.
- **Uso razonable en Stone:** buscar una letra para leerla mientras escuchas tu música, en tu
  propio teléfono.
- **Lo que tiene riesgo:**
  - Compartir archivos con la letra incrustada. Stone puede copiar la letra dentro del MP3/FLAC/M4A
    (`LyricsFileSync`), y al compartirlo envías también la letra.
  - Publicar o vender letras.
  - Que Stone incluyera letras dentro del APK (no lo hace).
- **Recomendación si Stone se distribuye públicamente:** muestra la fuente de cada letra (Stone
  ya sabe de qué servicio viene cada letra encontrada), añade un aviso en Legal ("las letras pertenecen a sus
  autores") y no incrustes letras en archivos que se vayan a compartir. Para tener licencia de
  verdad hace falta un proveedor con acuerdos (por ejemplo, la API comercial de Musixmatch), que
  es de pago.

## Portadas y etiquetas (iTunes)

- Las portadas también tienen derechos de autor (de la discográfica o del artista).
- La iTunes Search API es gratuita y sin clave. Apple la ofrece para enlazar a su catálogo, y sus
  condiciones de uso pueden cambiar: revísalas antes de una publicación comercial.
- Uso razonable: poner la portada correcta en **tu** copia de la canción.

## Cloudflare (relay)

- Stone usa el plan gratuito de Workers y Durable Objects.
- El relay **no guarda** peticiones, letras ni tokens: solo el hash del secreto de cada teléfono.
- Con el límite de 120 peticiones por minuto, un desconocido no puede agotar el plan gratuito.

## Licencias del código que usa Stone

Ver [`DEPENDENCIAS.md`](DEPENDENCIAS.md). Todo lo que va dentro del APK es **Apache-2.0**, que
permite su uso comercial y exige conservar los avisos de copyright. El código propio de Stone se
publica bajo la licencia de [`LICENSE`](LICENSE).
