// Token checks and per-caller limits. The hub token is a Cloudflare secret (wrangler secret put
// HUB_TOKEN); it is compared through SHA-256 in constant time and never logged.

async function digest(value: string): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
}

export async function sameSecret(given: string, expected: string): Promise<boolean> {
  const [a, b] = await Promise.all([digest(given), digest(expected)]);
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

/** Bearer header, X-Api-Key, or ?token= for apps that only accept a link (ChatGPT). */
export function givenToken(request: Request, url: URL): string | null {
  const auth = request.headers.get("authorization") ?? "";
  const bearer = /^bearer\s+(.+)$/i.exec(auth)?.[1]?.trim();
  return bearer || request.headers.get("x-api-key")?.trim() || url.searchParams.get("token")?.trim() || null;
}

/** Fixed-window counter per key; the Durable Object is a single instance, so counts are global. */
export class Limiter {
  private hits = new Map<string, { since: number; count: number }>();
  private max: number;
  private windowMs: number;
  constructor(max: number, windowMs = 60_000) { this.max = max; this.windowMs = windowMs; }
  /** Counts one hit; true when [key] is over the limit. */
  hit(key: string, now = Date.now()): boolean {
    if (this.hits.size > 5_000) this.hits.clear();
    let entry = this.hits.get(key);
    if (!entry || now - entry.since > this.windowMs) { entry = { since: now, count: 0 }; this.hits.set(key, entry); }
    return ++entry.count > this.max;
  }
  blocked(key: string, now = Date.now()): boolean {
    const entry = this.hits.get(key);
    return !!entry && now - entry.since <= this.windowMs && entry.count >= this.max;
  }
}
