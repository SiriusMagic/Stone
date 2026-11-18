// Stone (music and video player on the phone) as a hub module: every Stone tool appears as
// music_<tool>. Stone keeps checking its own token, permissions and on-screen confirmations.
import { type HubContext, type HubModule, type Tool, ToolError, type ToolResult, text } from "../types.ts";
import { type StoneLink, stoneRpc } from "./stone.ts";

export const MUSIC = "music_";
const LIST_TTL_MS = 10 * 60_000;

export async function savedLink(ctx: HubContext): Promise<StoneLink | null> {
  const raw = await ctx.store.getConfig("music.link");
  return raw ? JSON.parse(raw) as StoneLink : null;
}

/** Stone's tools, cached so tools/list stays fast and still works while the phone is offline. */
async function stoneTools(ctx: HubContext, link: StoneLink): Promise<Tool[]> {
  const cached = await ctx.store.getConfig("music.tools");
  const saved = cached ? JSON.parse(cached) as { at: number; tools: Tool[] } : null;
  if (saved && Date.now() - saved.at < LIST_TTL_MS) return saved.tools;
  try {
    const result = await stoneRpc(ctx, link, "tools/list", {}, 6_000) as { tools?: Tool[] };
    const tools = (result.tools ?? []).map((t) => ({ ...t, name: MUSIC + t.name, description: `[Stone] ${t.description}` }));
    await ctx.store.setConfig("music.tools", JSON.stringify({ at: Date.now(), tools }));
    return tools;
  } catch (error) {
    if (saved) return saved.tools;
    throw error;
  }
}

const statusTool: Tool = { name: "music_status", description: "Whether Stone is linked to the hub and reachable, and how many Stone tools there are.",
  inputSchema: { type: "object", properties: {} }, annotations: { title: "Estado de Stone", readOnlyHint: true } };

export const musicModule: HubModule = {
  prefix: MUSIC,
  title: "Música (Stone)",
  summary: "Stone on the phone: play, search the library, queue, lyrics, tags, playlists, equalizer, video.",
  validatesOwnArgs: true, // Stone validates every argument itself
  async tools(ctx) {
    const link = await savedLink(ctx);
    if (!link) return [statusTool];
    try { return [statusTool, ...await stoneTools(ctx, link)]; } catch { return [statusTool]; }
  },
  async call(name, args, ctx): Promise<ToolResult> {
    const link = await savedLink(ctx);
    if (name === "music_status") {
      if (!link) return text("Stone is not linked. The owner opens <hub>/setup and pastes the link copied from Stone.", { linked: false });
      try {
        const tools = (await stoneRpc(ctx, link, "tools/list", {}, 6_000) as { tools?: unknown[] }).tools?.length ?? 0;
        return text(`Stone is linked and online (${tools} tools).`, { linked: true, online: true, tools });
      } catch (error) {
        return text(`Stone is linked but not reachable: ${(error as Error).message}`, { linked: true, online: false });
      }
    }
    if (!link) throw new ToolError("NOT_LINKED", "Stone is not linked to the hub yet (owner: open /setup).");
    // Up to 75 s: Stone may ask the user on the phone to approve a critical change.
    const result = await stoneRpc(ctx, link, "tools/call", { name: name.slice(MUSIC.length), arguments: args }, 75_000);
    return result as ToolResult;
  },
};
