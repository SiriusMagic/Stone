# Stone Hub: un solo enlace MCP para varias apps

En lugar de conectar cada app a la IA una por una, conectas **un enlace** y la IA ve todas las
herramientas juntas, cada una con el prefijo de su app:

| Prefijo | App | Qué hace |
|---|---|---|
| `music_` | Stone (en tu teléfono) | Todas las herramientas de Stone, a través del relay |
| `notes_` | Notas | Crear, buscar, leer, editar y borrar notas |
| `calc_` | Calculadora | Expresiones y porcentajes, sin ejecutar código |
| `utils_` | Utilidades | Hora en cualquier zona horaria y números al azar |

```text
Claude / ChatGPT ──https──▶ Stone Hub (Cloudflare Worker, TypeScript)
                              │ token del hub + límites
                              ▼
                          router.ts ── prefijo ──▶ notes_ · calc_ · utils_  (en el hub)
                              │
                              └── music_ ──https──▶ relay ──wss──▶ Stone en el teléfono
                                                                    (comprueba su propio token)
```

## Conectar la IA

- Enlace: `https://stone-hub.<cuenta>.workers.dev/mcp?token=<TOKEN_DEL_HUB>`
- O mejor, si la app lo permite: `https://…/mcp` con la cabecera `Authorization: Bearer <TOKEN_DEL_HUB>`.

## Vincular Stone (desde el teléfono, sin pegar tokens en ningún chat)

1. En Stone: Ajustes › Modo API › **Crear token** solo para el hub, en el modo que quieras (lectura
   o escritura). Luego toca **Copiar**.
2. Abre `https://stone-hub.<cuenta>.workers.dev/setup`, escribe el token del hub, pega el enlace
   de Stone y toca **Vincular Stone**.
3. Para cortar el acceso: **Desvincular** en `/setup`, o revoca ese token en Stone.

## Seguridad

- Solo HTTPS (una petición por HTTP se rechaza, no se redirige). Cabeceras HSTS, nosniff,
  no-store, DENY y CSP en `/setup`.
- Token del hub como secreto de Cloudflare (`wrangler secret put HUB_TOKEN`), nunca en el código.
  Se compara por SHA-256 en tiempo constante. Tras 20 tokens erróneos por minuto, ese cliente
  queda bloqueado un minuto.
- 120 peticiones por minuto por cliente, cuerpo de 1 MiB como máximo y lotes de 20 mensajes.
- Argumentos validados en el servidor con el esquema de cada herramienta. SQL siempre
  parametrizado. HTML de `/setup` siempre escapado, sin cookies (por tanto, sin riesgo de CSRF).
- El módulo de música solo acepta enlaces del relay configurado (`STONE_RELAY_HOST`), así que el hub
  no puede usarse para llamar a otras webs. El token de Stone viaja en la cabecera, no en la URL.
- Stone sigue aplicando sus permisos por token y pide confirmación en el teléfono para lo crítico.
- Un módulo que falla no tumba a los demás: sus herramientas desaparecen de la lista y el resto
  sigue funcionando.

## Desarrollo

```sh
npm install
npm test                 # pruebas en Node (enrutador, módulos, autenticación, /setup)
npm run dev              # hub en http://127.0.0.1:8790 (secretos locales en .dev.vars, ignorado por Git)
npx wrangler secret put HUB_TOKEN
npx wrangler deploy
```

Añadir una app nueva: [`MODULES.md`](MODULES.md).
