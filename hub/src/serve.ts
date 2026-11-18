// Every request to the hub, after the Worker checked HTTPS: limits, token, size, then /mcp or /setup.
import { type HubContext, type HubEnv, type HubStoreApi, ToolError } from "./modules/types.ts";
import { Limiter, givenToken, sameSecret } from "./auth.ts";
import { handleRpc } from "./mcp.ts";
import { createRouter } from "./router.ts";
import { modules } from "./modules/index.ts";
import { parseStoneLink, stoneRpc } from "./modules/music/stone.ts";
import { savedLink } from "./modules/music/index.ts";
import { SETUP_HEADERS, setupPage } from "./setup.ts";

export const VERSION = "1.0.0";
const MAX_BODY = 1 << 20;
export const SECURITY = {
  "Strict-Transport-Security": "max-age=31536000; includeSubDomains", "X-Content-Type-Options": "nosniff",
  "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Frame-Options": "DENY",
};
export const CORS = {
  "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, Accept, Mcp-Session-Id, Mcp-Protocol-Version, X-Api-Key",
  "Access-Control-Expose-Headers": "Mcp-Session-Id",
};
const router = createRouter(modules);

export function json(status: number, body: unknown, extra: Record<string, string> = {}): Response {
  return new Response(body === null ? null : JSON.stringify(body),
    { status, headers: { "Content-Type": "application/json", ...SECURITY, ...CORS, ...extra } });
}
const rpcError = (status: number, message: string, extra: Record<string, string> = {}) =>
  json(status, { jsonrpc: "2.0", id: null, error: { code: -32001, message } }, extra);
const html = (status: number, body: string) => new Response(body, { status, headers: { ...SECURITY, ...SETUP_HEADERS } });

export interface Limits { requests: Limiter; failures: Limiter }

/** true when [token] is the hub token; wrong tokens are counted per caller and logged without the value. */
async function authorized(token: string | null, env: HubEnv, limits: Limits, caller: string): Promise<boolean> {
  if (token && env.HUB_TOKEN && await sameSecret(token, env.HUB_TOKEN)) return true;
  limits.failures.hit(caller);
  console.warn(JSON.stringify({ event: "auth_rejected", caller, missing: !token }));
  return false;
}

export async function serve(request: Request, env: HubEnv, store: HubStoreApi & Limits, fetcher: typeof fetch = fetch): Promise<Response> {
  const url = new URL(request.url);
  const caller = request.headers.get("CF-Connecting-IP") || "local";
  if (store.requests.hit(caller)) {
    console.warn(JSON.stringify({ event: "rate_limited", caller }));
    return rpcError(429, "Stone Hub: too many requests. Wait a minute.", { "Retry-After": "60" });
  }
  if (!env.HUB_TOKEN) return rpcError(503, "Stone Hub has no token yet (owner: npx wrangler secret put HUB_TOKEN).");
  if (store.failures.blocked(caller)) return rpcError(429, "Stone Hub: too many wrong tokens. Wait a minute.", { "Retry-After": "60" });
  if (Number(request.headers.get("content-length") ?? 0) > MAX_BODY) return rpcError(413, "Stone Hub: body larger than 1 MiB.");
  const ctx: HubContext = { store, env, fetch: (input, init) => fetcher(input, init) };

  if (url.pathname === "/mcp") {
    if (request.method === "DELETE") return json(200, null);
    if (request.method !== "POST") return rpcError(405, "Stone Hub: send MCP requests with POST.", { Allow: "POST, DELETE" });
    if (!await authorized(givenToken(request, url), env, store, caller))
      return rpcError(401, "Stone Hub: missing or wrong token.", { "WWW-Authenticate": "Bearer" });
    const body = await request.text();
    if (body.length > MAX_BODY) return rpcError(413, "Stone Hub: body larger than 1 MiB.");
    const reply = await handleRpc(body, router, ctx, VERSION);
    const session = body.includes('"initialize"') ? { "Mcp-Session-Id": crypto.randomUUID() } : {};
    return reply === null ? json(202, null, session) : json(200, reply, session);
  }

  if (url.pathname === "/setup") {
    const names = modules.map((m) => m.title);
    if (request.method === "GET") return html(200, setupPage({ linked: !!await savedLink(ctx), modules: names }));
    if (request.method !== "POST") return html(405, setupPage({ linked: false, modules: names, error: true, message: "Método no permitido." }));
    const form = await request.formData();
    if (!await authorized(String(form.get("token") ?? ""), env, store, caller))
      return html(401, setupPage({ linked: !!await savedLink(ctx), modules: names, error: true, message: "Token del hub incorrecto." }));
    const show = async (message: string, error = false) =>
      html(error ? 400 : 200, setupPage({ linked: !!await savedLink(ctx), modules: names, message, error }));
    if (form.get("action") === "unlink") {
      await store.setConfig("music.link", null); await store.setConfig("music.tools", null);
      return show("Stone desvinculado.");
    }
    try {
      const link = parseStoneLink(String(form.get("link") ?? ""), env.STONE_RELAY_HOST);
      let note = "Stone vinculado y en línea.";
      try { await stoneRpc(ctx, link, "tools/list", {}, 8_000); } catch (error) {
        if ((error as ToolError).code === "STONE_TOKEN") throw error; // never save a token Stone rejects
        note = "Stone vinculado. Ahora no responde: abre Stone con el Modo API activado.";
      }
      await store.setConfig("music.link", JSON.stringify(link)); await store.setConfig("music.tools", null);
      return show(note);
    } catch (error) {
      return show((error as Error).message, true);
    }
  }
  return rpcError(404, "Stone Hub: unknown path. Use POST /mcp or open /setup.");
}
