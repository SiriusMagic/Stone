# Stone relay

Da a cada teléfono un **enlace fijo de internet** para el Modo API (MCP) de Stone, sin
computadora y sin tocar el router. Los usuarios solo tocan **Activar MCP** y **Copiar enlace**,
y lo pegan en Claude o ChatGPT (Ajustes › Conectores › Añadir conector personalizado).

## Cómo funciona

```text
Claude / ChatGPT ──https──▶ relevo (Cloudflare) ◀──WebSocket saliente── Stone en el teléfono
                            /d/<teléfono>/mcp?token=…                   reenvía a 127.0.0.1:8765
```

- El teléfono abre la conexión hacia fuera, así que funciona en cualquier Wi-Fi o con datos.
- Cada teléfono tiene un identificador y un secreto propios: otro teléfono no puede ocupar su
  enlace (401 sin secreto, 403 con otro secreto).
- Stone sigue comprobando el token del enlace. "Cambiar enlace" en la app crea otro token y
  desconecta a quien tuviera el anterior.
- Los cambios que escriben archivos piden confirmación en la pantalla del teléfono.

## Publicarlo (una sola vez, lo hace quien publica la app)

Necesitas una cuenta gratuita de Cloudflare.

```sh
cd relay
npm install
npx wrangler login     # abre el navegador para entrar en Cloudflare
npx wrangler deploy    # muestra la dirección, por ejemplo https://stone-relay.<tu-cuenta>.workers.dev
```

Después compila Stone con esa dirección:

```sh
cd ../android-native
./gradlew :app:assemblePreview -PstoneRelayUrl=https://stone-relay.<tu-cuenta>.workers.dev
```

O escríbela una vez en `android-native/gradle.properties` como `stoneRelayUrl=…`.
Sin dirección, el Modo API funciona solo en el propio teléfono o en la misma Wi-Fi.

El plan gratuito de Workers y Durable Objects alcanza para uso normal. Revisa los límites
actuales de Cloudflare si tienes muchos usuarios.

## Probar en local

```sh
npm run dev            # relevo en http://127.0.0.1:8787
```

## Desde Stone 1.7.9

El relay añade al mensaje `client` (la dirección del cliente según Cloudflare, `CF-Connecting-IP`) y avisa al
teléfono con `{t:"timeout", id}` cuando responde 504. Ambos son compatibles: un Stone anterior los ignora, y un
Stone nuevo sigue funcionando con el relay anterior (cuenta los tokens erróneos de todo el relay juntos y
detecta las respuestas tardías por tiempo). Para activarlos: `npx wrangler deploy`.

## Protecciones (desde 1.7.9+)

- Solo HTTPS: una petición por `http://` a la dirección pública se rechaza (400), no se redirige.
- Cabeceras de seguridad en todas las respuestas (HSTS, `nosniff`, `no-store`, `no-referrer`, CSP, sin iframes).
- Cuerpo máximo de 1 MiB (413), igual que Stone.
- Límite de 120 peticiones por minuto por dirección y teléfono (429), y como máximo 72 en espera por teléfono.
- Los intentos de ocupar el enlace de otro teléfono y los bloqueos por límite se registran
  (`npx wrangler tail`).

Para activarlas hay que volver a publicar: `npx wrangler deploy`.
