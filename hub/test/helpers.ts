import { type HubStoreApi, type Note } from "../src/modules/types.ts";
import { Limiter } from "../src/auth.ts";
import { serve } from "../src/serve.ts";

export const TOKEN = "hub_test_token_0123456789abcdefghijklmnop";
export const RELAY = "stone-relay.example.workers.dev";
export const STONE_LINK = `https://${RELAY}/d/abcdefghijkl12/mcp?token=stn_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345`;

export class MemoryStore implements HubStoreApi {
  requests = new Limiter(120);
  failures = new Limiter(20);
  config = new Map<string, string>();
  notes = new Map<string, Note>();
  private seq = 0;
  async getConfig(k: string) { return this.config.get(k) ?? null; }
  async setConfig(k: string, v: string | null) { if (v === null) this.config.delete(k); else this.config.set(k, v); }
  async notesCreate(title: string, body: string) {
    const now = Date.now() + this.seq, id = `note${String(++this.seq).padStart(8, "0")}`;
    const n = { id, title, body, created: now, updated: now }; this.notes.set(id, n); return n;
  }
  async notesList(q: string, limit: number) {
    return [...this.notes.values()].filter((n) => !q || n.title.includes(q) || n.body.includes(q))
      .sort((a, b) => b.updated - a.updated).slice(0, limit);
  }
  async notesGet(id: string) { return this.notes.get(id) ?? null; }
  async notesUpdate(id: string, title: string | null, body: string | null) {
    const n = this.notes.get(id); if (!n) return null;
    const next = { ...n, title: title ?? n.title, body: body ?? n.body, updated: Date.now() + ++this.seq };
    this.notes.set(id, next); return next;
  }
  async notesDelete(id: string) { return this.notes.delete(id); }
  async notesCount() { return this.notes.size; }
}

/** A fake Stone behind the relay: records what it receives. */
export function fakeStone(options: { status?: number } = {}) {
  const calls: { url: string; auth: string | null; body: any }[] = [];
  const fetcher = (async (input: string | URL | Request, init?: RequestInit) => {
    const body = JSON.parse(String(init?.body));
    calls.push({ url: String(input), auth: new Headers(init?.headers).get("authorization"), body });
    if (options.status) return new Response("{}", { status: options.status });
    const result = body.method === "tools/list"
      ? { tools: [{ name: "player_state", description: "What is playing", inputSchema: { type: "object" } },
                  { name: "playlist_delete", description: "Delete a playlist", inputSchema: { type: "object" } }] }
      : { content: [{ type: "text", text: `ran ${body.params.name}` }], isError: false };
    return new Response(JSON.stringify({ jsonrpc: "2.0", id: 1, result }), { status: 200 });
  }) as typeof fetch;
  return { calls, fetcher };
}

export function hub(store = new MemoryStore(), fetcher: typeof fetch = fakeStone().fetcher) {
  const env = { HUB_TOKEN: TOKEN, STONE_RELAY_HOST: RELAY };
  return {
    store,
    async rpc(method: string, params: unknown = {}, token: string | null = TOKEN, ip = "1.1.1.1") {
      const headers: Record<string, string> = { "Content-Type": "application/json", "CF-Connecting-IP": ip };
      if (token) headers.Authorization = `Bearer ${token}`;
      const response = await serve(new Request("https://hub.example/mcp", { method: "POST", headers,
        body: JSON.stringify({ jsonrpc: "2.0", id: 7, method, params }) }), env, store, fetcher);
      return { status: response.status, body: response.status === 202 ? null : await response.json() as any, response };
    },
    async setup(fields: Record<string, string>, ip = "1.1.1.1") {
      const response = await serve(new Request("https://hub.example/setup", { method: "POST",
        headers: { "CF-Connecting-IP": ip }, body: new URLSearchParams(fields) }), env, store, fetcher);
      return { status: response.status, html: await response.text() };
    },
  };
}
