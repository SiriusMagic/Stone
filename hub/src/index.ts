// Stone Hub (Cloudflare Worker): one MCP link that gives an AI several apps. HTTPS only; every
// request then goes to the single HubStore Durable Object (storage + global limits).
import { type HubEnv } from "./modules/types.ts";
import { CORS, SECURITY, json } from "./serve.ts";

export { HubStore } from "./store.ts";

interface Env extends HubEnv { HUB: DurableObjectNamespace }

const local = (url: URL) => url.hostname === "127.0.0.1" || url.hostname === "localhost";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    // Over plain HTTP the token would already have crossed the network in clear: refuse, don't redirect.
    if (url.protocol !== "https:" && !local(url)) return json(400, { error: "Stone Hub: use https://" });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: { ...CORS, ...SECURITY } });
    if (url.pathname === "/") return new Response("Stone Hub OK\n", { headers: { "Content-Type": "text/plain", ...SECURITY } });
    return env.HUB.get(env.HUB.idFromName("main")).fetch(request);
  },
};
