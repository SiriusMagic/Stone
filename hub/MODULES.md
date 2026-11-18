# Cómo añadir una app al hub (guía para Codex, Claude o una persona)

Cada app es un **módulo**: una carpeta en `src/modules/<app>/` con un `index.ts` que exporta un
objeto `HubModule` (contrato en `src/modules/types.ts`). El enrutador (`src/router.ts`) lee el
**prefijo** de la herramienta que pide la IA y la envía al módulo dueño de ese prefijo. No hay
`if/else` gigantes.

## Pasos

1. Elige un prefijo único: letras minúsculas o cifras terminadas en `_` (`gallery_`, `weather_`).
   No puede empezar por otro prefijo ya usado (`music_` y `musicx_` chocan, y el hub no arranca).
2. Copia `src/modules/calculator/index.ts` como plantilla en `src/modules/<app>/index.ts`.
3. Añade el módulo a la lista de `src/modules/index.ts`. Nada más cambia.
4. Escribe una prueba en `test/` y ejecuta `npm test`.

## Reglas (el router y las pruebas las hacen cumplir)

- **Nombres:** todas las herramientas empiezan por el prefijo del módulo: `^[a-z][a-z0-9_]{1,63}$`.
  Una herramienta mal nombrada no se muestra a la IA.
- **Esquema estricto:** cada `inputSchema` declara `type`, `required`, `maxLength`, `minimum` y
  `maximum`, y lleva `additionalProperties: false`. El hub valida los argumentos en el servidor
  antes de llamar al módulo.
- **Errores:** los errores esperables se lanzan como `new ToolError("CODIGO", "mensaje")`, que la
  IA lee y puede corregir. Cualquier otro error se registra y se responde sin detalles internos.
- **Nunca** uses `eval`, `Function()`, `new Function` ni plantillas con código.
- **Datos:** se guardan solo con `ctx.store`. Las consultas a SQL son siempre parametrizadas
  (`sql.exec("… WHERE id = ?", id)`) y nunca se concatena texto en ellas.
- **Red:** `ctx.fetch` se usa solo hacia hosts fijos y conocidos, nunca hacia una URL que mande la
  IA. Si una app necesita una dirección configurable, valídala como hace `music/stone.ts`.
- **Secretos:** nunca en el código. Van con `npx wrangler secret put NOMBRE` o en `/setup`.
- **Anotaciones:** pon `readOnlyHint: true` en lo que solo lee y `destructiveHint: true` en lo que
  borra.
- **Tamaño:** un archivo cerca de 100 líneas; divídelo antes de pasar de 150.

## Plantilla mínima

```ts
import { type HubModule, ToolError, text } from "../types.ts";

export const weatherModule: HubModule = {
  prefix: "weather_",
  title: "Tiempo",
  summary: "Forecast for a city.",
  async tools() {
    return [{ name: "weather_today", description: "Today's forecast for a city.",
      inputSchema: { type: "object", additionalProperties: false, required: ["city"],
        properties: { city: { type: "string", minLength: 1, maxLength: 80 } } },
      annotations: { title: "Tiempo de hoy", readOnlyHint: true } }];
  },
  async call(name, args, ctx) {
    if (name === "weather_today") return text(`Sunny in ${args.city}`);
    throw new ToolError("UNKNOWN_TOOL", `Unknown weather tool '${name}'.`);
  },
};
```
