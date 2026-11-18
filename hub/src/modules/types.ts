// Contract every hub module follows. A module owns one prefix ("calc_") and every tool it lists
// starts with it; the router (../router.ts) uses that prefix to send each call to its module.

export interface Tool {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  annotations?: { title?: string; readOnlyHint?: boolean; destructiveHint?: boolean };
}

export interface ToolResult {
  content: { type: "text"; text: string }[];
  structuredContent?: unknown;
  isError?: boolean;
}

export interface Note { id: string; title: string; body: string; created: number; updated: number }

/** Persistent storage the modules may use (a Durable Object in production, memory in tests). */
export interface HubStoreApi {
  getConfig(key: string): Promise<string | null>;
  setConfig(key: string, value: string | null): Promise<void>;
  notesCreate(title: string, body: string): Promise<Note>;
  notesList(query: string, limit: number): Promise<Note[]>;
  notesGet(id: string): Promise<Note | null>;
  notesUpdate(id: string, title: string | null, body: string | null): Promise<Note | null>;
  notesDelete(id: string): Promise<boolean>;
  notesCount(): Promise<number>;
}

export interface HubEnv { STONE_RELAY_HOST: string; HUB_TOKEN?: string }

export interface HubContext {
  store: HubStoreApi;
  env: HubEnv;
  /** Injected so tests can fake the network. */
  fetch: typeof fetch;
}

export interface HubModule {
  /** Lowercase letters/digits ending in "_": "music_", "notes_". */
  prefix: string;
  title: string;
  /** One line for the LLM: what this module is for. */
  summary: string;
  /** true when the module checks its own arguments (e.g. a remote app); otherwise the router does. */
  validatesOwnArgs?: boolean;
  tools(ctx: HubContext): Promise<Tool[]>;
  call(name: string, args: Record<string, unknown>, ctx: HubContext): Promise<ToolResult>;
}

/** An expected failure with a stable code the LLM can read and act on. */
export class ToolError extends Error {
  code: string;
  constructor(code: string, message: string) { super(message); this.code = code; }
}

export function text(message: string, structured?: unknown): ToolResult {
  return structured === undefined ? { content: [{ type: "text", text: message }] }
    : { content: [{ type: "text", text: message }], structuredContent: structured };
}
