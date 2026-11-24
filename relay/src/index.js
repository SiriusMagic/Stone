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
// Stone itself refuses bodies above 1 MiB; refusing them here saves forwarding them.
const MAX_BODY_BYTES = 1 << 20;
// Per caller address and device: more than this in a minute gets 429 before reaching the phone.
const RATE_LIMIT_PER_MINUTE = 120;
// Requests waiting for the phone at once, per device (the phone queues at most 64 more).
const MAX_PENDING = 72;
// Answers carry tokens and library data: never cached, sniffed, framed or sent as a referrer.
const SECURITY = {
  "Strict-Transport-Security": "max-age=31536000; includeSubDomains",
  "X-Content-Type-Options": "nosniff",
  "Cache-Control": "no-store",
  "Referrer-Policy": "no-referrer",
  "X-Frame-Options": "DENY",
  "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
};

function jsonError(status, message, extra = {}) {
  return new Response(JSON.stringify({ jsonrpc: "2.0", id: null, error: { code: -32000, message } }),
    { status, headers: { "Content-Type": "application/json", ...CORS, ...SECURITY, ...extra } });
}

function isLocal(url) {
  return url.hostname === "127.0.0.1" || url.hostname === "localhost";
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    // HTTPS only (except "wrangler dev" on this computer): over plain HTTP the token in the link or
    // header would already have crossed the network in clear, so the request is refused, not redirected.
    if (url.protocol !== "https:" && !isLocal(url)) return jsonError(400, "Stone relay: use https://");
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: { ...CORS, ...SECURITY } });
    if (url.pathname === "/" ) return new Response("Stone relay OK\n", { headers: { "Content-Type": "text/plain", ...SECURITY } });
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
    this.callers = new Map(); // caller address -> { since, count }, in memory only
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
    if (saved && saved !== hash) {
      // Someone else tried to take over this phone's link: visible in "wrangler tail" / Workers logs.
      console.warn(JSON.stringify({ event: "device_takeover_refused", client: request.headers.get("CF-Connecting-IP") || "" }));
      return jsonError(403, "Stone relay: this device id belongs to another phone");
    }
    if (!saved) await this.ctx.storage.put("secret", hash);
    // Only the newest connection of the phone is kept.
    for (const old of this.ctx.getWebSockets()) { try { old.close(1000, "replaced"); } catch (_) {} }
    const [client, server] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(server);
    return new Response(null, { status: 101, webSocket: client });
  }

  /** True when [caller] already sent RATE_LIMIT_PER_MINUTE requests to this device in the current minute. */
  limited(caller, now = Date.now()) {
    if (this.callers.size > 5_000) this.callers.clear();
    let entry = this.callers.get(caller);
    if (!entry || now - entry.since > 60_000) { entry = { since: now, count: 0 }; this.callers.set(caller, entry); }
    return ++entry.count > RATE_LIMIT_PER_MINUTE;
  }

  async forward(request, url) {
    const client = request.headers.get("CF-Connecting-IP") || "";
    if (this.limited(client || "unknown")) {
      console.warn(JSON.stringify({ event: "rate_limited", client }));
      return jsonError(429, "Stone relay: too many requests. Wait a minute.", { "Retry-After": "60" });
    }
    if (this.pending.size >= MAX_PENDING) return jsonError(503, "Stone relay: the phone is busy. Try again soon.", { "Retry-After": "10" });
    const declared = Number(request.headers.get("content-length") || 0);
    if (declared > MAX_BODY_BYTES) return jsonError(413, "Stone relay: request body larger than 1 MiB");
    const socket = this.ctx.getWebSockets()[0];
    if (!socket) return jsonError(503, "Stone is not open on the phone. Open Stone and turn on MCP mode.");
    const id = String(this.nextId++);
    const headers = {};
    for (const name of FORWARDED_HEADERS) {
      const value = request.headers.get(name);
      if (value) headers[name] = value;
    }
    const path = url.pathname.replace(/^\/d\/[^/]+/, "") || "/";
    // "client" is the caller's address as Cloudflare saw it (CF-Connecting-IP is set by Cloudflare, not by
    // the caller). Stone uses it only to count wrong tokens per caller. Older Stone versions ignore it.
    const body = ["GET", "HEAD"].includes(request.method) ? "" : await request.text();
    // Content-Length can be missing (chunked uploads): the real size is checked too.
    if (new TextEncoder().encode(body).length > MAX_BODY_BYTES) return jsonError(413, "Stone relay: request body larger than 1 MiB");
    const message = { t: "req", id, method: request.method, path, query: url.search, headers, client, body };
    const answer = new Promise((resolve) => {
      this.pending.set(id, resolve);
      setTimeout(() => {
        if (!this.pending.delete(id)) return;
        // Tell the phone this request was answered 504, so it does not run it later behind the agent's back.
        // Outgoing WebSocket messages are not billed as requests. Older Stone versions ignore "timeout".
        try { socket.send(JSON.stringify({ t: "timeout", id })); } catch (_) {}
        resolve(null);
      }, REQUEST_TIMEOUT_MS);
    });
    try { socket.send(JSON.stringify(message)); } catch (_) { this.pending.delete(id); return jsonError(503, "Stone relay: phone disconnected"); }
    const reply = await answer;
    if (!reply) return jsonError(504, "Stone did not answer in time.");
    return new Response(reply.body || null, { status: reply.status || 502, headers: { ...(reply.headers || {}), ...CORS, ...SECURITY } });
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
