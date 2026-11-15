// Stone relay: gives every phone a fixed public HTTPS link for its MCP server.
//
// The phone opens an outgoing WebSocket to /connect (works on any Wi-Fi or mobile data, no
// router setup). AI apps call https://<relay>/d/<device>/mcp?token=…; the relay forwards each
// request over that WebSocket and returns the phone's answer. The relay never sees inside the
// requests beyond forwarding them, and Stone itself still checks the token.
//
// One Durable Object per device keeps the socket. The first connection of a device stores a hash
// of its secret, so nobody else can take over that device's link.

const DEVICE = /^[a-z0-9]{12,40}$/;
const FORWARDED_HEADERS = ["authorization", "x-api-key", "content-type", "accept", "mcp-session-id",
  "mcp-protocol-version", "last-event-id", "origin"];
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, Accept, Mcp-Session-Id, Mcp-Protocol-Version, X-Api-Key",
  "Access-Control-Expose-Headers": "Mcp-Session-Id, X-Stone-Server",
};
const REQUEST_TIMEOUT_MS = 75_000; // Stone may wait up to 60 s for the user to approve a change.

function jsonError(status, message) {
  return new Response(JSON.stringify({ jsonrpc: "2.0", id: null, error: { code: -32000, message } }),
    { status, headers: { "Content-Type": "application/json", ...CORS } });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (url.pathname === "/" ) return new Response("Stone relay OK\n", { headers: { "Content-Type": "text/plain" } });
    let device;
    if (url.pathname === "/connect") device = url.searchParams.get("device");
    else {
      const match = url.pathname.match(/^\/d\/([^/]+)(\/.*)?$/);
      if (!match) return jsonError(404, "Stone relay: unknown path");
      device = match[1];
    }
    if (!device || !DEVICE.test(device)) return jsonError(400, "Stone relay: invalid device id");
    const stub = env.DEVICES.get(env.DEVICES.idFromName(device));
    return stub.fetch(request);
  },
};

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export class DeviceRelay {
  constructor(ctx) {
    this.ctx = ctx;
    this.pending = new Map(); // request id -> resolve
    this.nextId = 1;
    // Keep-alive pings are answered without waking the object.
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(request) {
    const url = new URL(request.url);
    return url.pathname === "/connect" ? this.connect(request) : this.forward(request, url);
  }

  async connect(request) {
    if (request.headers.get("Upgrade") !== "websocket") return jsonError(426, "Stone relay: WebSocket required");
    const secret = request.headers.get("X-Stone-Secret") || "";
    if (secret.length < 32) return jsonError(401, "Stone relay: missing device secret");
    const hash = await sha256(secret);
    const saved = await this.ctx.storage.get("secret");
    if (saved && saved !== hash) return jsonError(403, "Stone relay: this device id belongs to another phone");
    if (!saved) await this.ctx.storage.put("secret", hash);
    // Only the newest connection of the phone is kept.
    for (const old of this.ctx.getWebSockets()) { try { old.close(1000, "replaced"); } catch (_) {} }
    const [client, server] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(server);
    return new Response(null, { status: 101, webSocket: client });
  }

  async forward(request, url) {
    const socket = this.ctx.getWebSockets()[0];
    if (!socket) return jsonError(503, "Stone is not open on the phone. Open Stone and turn on API mode.");
    const id = String(this.nextId++);
    const headers = {};
    for (const name of FORWARDED_HEADERS) {
      const value = request.headers.get(name);
      if (value) headers[name] = value;
    }
    const path = url.pathname.replace(/^\/d\/[^/]+/, "") || "/";
    const message = { t: "req", id, method: request.method, path, query: url.search, headers,
      body: ["GET", "HEAD"].includes(request.method) ? "" : await request.text() };
    const answer = new Promise((resolve) => {
      this.pending.set(id, resolve);
      setTimeout(() => { if (this.pending.delete(id)) resolve(null); }, REQUEST_TIMEOUT_MS);
    });
    try { socket.send(JSON.stringify(message)); } catch (_) { this.pending.delete(id); return jsonError(503, "Stone relay: phone disconnected"); }
    const reply = await answer;
    if (!reply) return jsonError(504, "Stone did not answer in time.");
    return new Response(reply.body || null, { status: reply.status || 502, headers: { ...(reply.headers || {}), ...CORS } });
  }

  async webSocketMessage(socket, data) {
    let message;
    try { message = JSON.parse(typeof data === "string" ? data : new TextDecoder().decode(data)); } catch (_) { return; }
    if (message.t !== "res") return;
    const resolve = this.pending.get(message.id);
    if (resolve) { this.pending.delete(message.id); resolve(message); }
  }

  async webSocketClose(socket) {
    try { socket.close(1000, "closed"); } catch (_) {}
  }
}
