// The hub's single Durable Object: storage (SQLite, parameterized queries only), global limits,
// and the request handling for /mcp and /setup.
import { DurableObject } from "cloudflare:workers";
import { type HubEnv, type HubStoreApi, type Note } from "./modules/types.ts";
import { serve } from "./serve.ts";
import { Limiter } from "./auth.ts";

type Row = Record<string, string | number>;
const toNote = (r: Row): Note => ({ id: String(r.id), title: String(r.title), body: String(r.body),
  created: Number(r.created), updated: Number(r.updated) });
const like = (q: string) => "%" + q.replace(/[\\%_]/g, (c) => "\\" + c) + "%";

export class HubStore extends DurableObject<HubEnv> implements HubStoreApi {
  requests = new Limiter(120);   // per caller and minute
  failures = new Limiter(20);    // wrong tokens per caller and minute
  private sql: SqlStorage;

  constructor(ctx: DurableObjectState, env: HubEnv) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`CREATE TABLE IF NOT EXISTS notes(id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
      created INTEGER NOT NULL, updated INTEGER NOT NULL)`);
    this.sql.exec("CREATE TABLE IF NOT EXISTS config(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  }

  fetch(request: Request): Promise<Response> { return serve(request, this.env, this); }

  async getConfig(key: string) {
    const rows = this.sql.exec("SELECT value FROM config WHERE key = ?", key).toArray() as Row[];
    return rows.length ? String(rows[0].value) : null;
  }
  async setConfig(key: string, value: string | null) {
    if (value === null) this.sql.exec("DELETE FROM config WHERE key = ?", key);
    else this.sql.exec("INSERT INTO config(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value);
  }
  async notesCreate(title: string, body: string) {
    const now = Date.now(), id = crypto.randomUUID().replace(/-/g, "").slice(0, 16);
    this.sql.exec("INSERT INTO notes(id, title, body, created, updated) VALUES(?, ?, ?, ?, ?)", id, title, body, now, now);
    return { id, title, body, created: now, updated: now };
  }
  async notesList(query: string, limit: number) {
    const rows = query
      ? this.sql.exec("SELECT * FROM notes WHERE title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' ORDER BY updated DESC LIMIT ?",
          like(query), like(query), limit)
      : this.sql.exec("SELECT * FROM notes ORDER BY updated DESC LIMIT ?", limit);
    return (rows.toArray() as Row[]).map(toNote);
  }
  async notesGet(id: string) {
    const rows = this.sql.exec("SELECT * FROM notes WHERE id = ?", id).toArray() as Row[];
    return rows.length ? toNote(rows[0]) : null;
  }
  async notesUpdate(id: string, title: string | null, body: string | null) {
    const current = await this.notesGet(id);
    if (!current) return null;
    const next = { ...current, title: title ?? current.title, body: body ?? current.body, updated: Date.now() };
    this.sql.exec("UPDATE notes SET title = ?, body = ?, updated = ? WHERE id = ?", next.title, next.body, next.updated, id);
    return next;
  }
  async notesDelete(id: string) {
    return this.sql.exec("DELETE FROM notes WHERE id = ?", id).rowsWritten > 0;
  }
  async notesCount() {
    return Number((this.sql.exec("SELECT COUNT(*) AS n FROM notes").one() as Row).n);
  }
}
