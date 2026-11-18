// The discriminator: lists the tools of every module and sends each call to the module that owns
// its prefix. One module failing never hides or breaks the others.
import { type HubContext, type HubModule, type Tool, ToolError, type ToolResult } from "./modules/types.ts";
import { validateArgs } from "./validate.ts";

const PREFIX = /^[a-z][a-z0-9]{0,15}_$/;
const TOOL_NAME = /^[a-z][a-z0-9_]{1,63}$/;

export interface Router {
  modules: HubModule[];
  listTools(ctx: HubContext): Promise<Tool[]>;
  call(name: string, args: Record<string, unknown>, ctx: HubContext): Promise<ToolResult>;
}

export function createRouter(modules: HubModule[]): Router {
  for (const m of modules) {
    if (!PREFIX.test(m.prefix)) throw new Error(`Invalid module prefix: ${m.prefix}`);
    const clash = modules.find((o) => o !== m && (o.prefix.startsWith(m.prefix) || m.prefix.startsWith(o.prefix)));
    if (clash) throw new Error(`Prefixes ${m.prefix} and ${clash.prefix} overlap`);
  }
  const owner = (name: string) => modules.find((m) => name.startsWith(m.prefix));

  async function toolsOf(m: HubModule, ctx: HubContext): Promise<Tool[]> {
    try {
      // A tool outside its module's prefix or with an unsafe name is never shown.
      return (await m.tools(ctx)).filter((t) => t.name.startsWith(m.prefix) && TOOL_NAME.test(t.name));
    } catch (error) {
      console.warn(JSON.stringify({ event: "module_tools_failed", module: m.prefix, error: String(error) }));
      return [];
    }
  }

  return {
    modules,
    async listTools(ctx) {
      const lists = await Promise.all(modules.map((m) => toolsOf(m, ctx)));
      return lists.flat();
    },
    async call(name, args, ctx) {
      const module = owner(name);
      if (!module) throw new ToolError("UNKNOWN_TOOL",
        `No module handles '${name}'. Modules: ${modules.map((m) => m.prefix + "*").join(", ")}.`);
      if (!module.validatesOwnArgs) {
        const tool = (await toolsOf(module, ctx)).find((t) => t.name === name);
        if (!tool) throw new ToolError("UNKNOWN_TOOL", `Unknown tool '${name}'. Call tools/list.`);
        validateArgs(tool.inputSchema, args);
      }
      return module.call(name, args, ctx);
    },
  };
}
