// Talking to Stone's own MCP server through the relay. The Stone link is accepted only from the
// configured relay host, so the hub can never be turned into a proxy to other sites.
import { type HubContext, ToolError } from "../types.ts";

export interface StoneLink { endpoint: string; token: string }

const TOKEN = /^(stn_[A-Za-z0-9_-]{20,100}|[0-9a-f]{32,128})$/;
const local = (host: string) => /^(127\.0\.0\.1|localhost)(:\d+)?$/.test(host);

/** Checks a link copied from Stone (…/d/<device>/mcp?token=…) and splits it into endpoint + token. */
export function parseStoneLink(link: string, relayHost: string): StoneLink {
  let url: URL;
  try { url = new URL(link.trim()); } catch { throw new ToolError("INVALID_LINK", "That is not a link."); }
  const scheme = local(relayHost) ? "http:" : "https:";
  if (url.protocol !== scheme || url.host !== relayHost)
    throw new ToolError("INVALID_LINK", `Only links of ${relayHost} (copied from Stone) are accepted.`);
  if (!/^\/d\/[a-z0-9]{12,40}\/mcp$/.test(url.pathname))
    throw new ToolError("INVALID_LINK", "The link must end in /d/<phone>/mcp?token=… (Stone › Ajustes › Modo API › Copiar).");
  const token = url.searchParams.get("token") ?? "";
  if (!TOKEN.test(token)) throw new ToolError("INVALID_LINK", "The link has no valid Stone token (?token=stn_…).");
  return { endpoint: url.origin + url.pathname, token };
}

/** One JSON-RPC call to Stone; the token travels in the header, never in the URL. */
export async function stoneRpc(ctx: HubContext, link: StoneLink, method: string, params: unknown, timeoutMs: number) {
  let response: Response;
  try {
    response = await ctx.fetch(link.endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", Authorization: `Bearer ${link.token}` },
      body: JSON.stringify({ jsonrpc: "2.0", id: 1, method, params }),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch {
    throw new ToolError("STONE_UNREACHABLE", "Stone did not answer in time. Is Stone open with API mode on?");
  }
  if (response.status === 401 || response.status === 403)
    throw new ToolError("STONE_TOKEN", "Stone rejected the token saved in the hub (revoked, paused or expired). Link Stone again in /setup.");
  if (response.status === 503) throw new ToolError("STONE_OFFLINE", "Stone is not open on the phone, or API mode is off.");
  if (response.status === 429) throw new ToolError("RATE_LIMITED", "Stone is limiting requests. Wait a minute.");
  if (!response.ok) throw new ToolError("STONE_ERROR", `Stone answered HTTP ${response.status}.`);
  const reply = await response.json() as { result?: unknown; error?: { message?: string } };
  if (reply.error) throw new ToolError("STONE_ERROR", reply.error.message ?? "Stone returned an error.");
  return reply.result;
}
