import { type HubModule, type Note, ToolError, text } from "../types.ts";

export const MAX_NOTES = 1000;
const ID = { type: "string", minLength: 8, maxLength: 40 };
const TITLE = { type: "string", minLength: 1, maxLength: 200 };
const BODY = { type: "string", maxLength: 20000 };
const brief = (n: Note) => ({ id: n.id, title: n.title, updated: new Date(n.updated).toISOString(),
  preview: n.body.length > 140 ? n.body.slice(0, 140) + "…" : n.body });

export const notesModule: HubModule = {
  prefix: "notes_",
  title: "Notas",
  summary: "A private notepad: create, search, read, edit and delete text notes.",
  async tools() {
    const object = (required: string[], properties: Record<string, unknown>) =>
      ({ type: "object", additionalProperties: false, required, properties });
    return [
      { name: "notes_create", description: "Save a new note.", inputSchema: object(["title"], { title: TITLE, body: BODY }),
        annotations: { title: "Crear nota" } },
      { name: "notes_list", description: "Notes newest first; `query` searches title and text.",
        inputSchema: object([], { query: { type: "string", maxLength: 100 }, limit: { type: "integer", minimum: 1, maximum: 100 } }),
        annotations: { title: "Ver notas", readOnlyHint: true } },
      { name: "notes_get", description: "Full text of one note.", inputSchema: object(["id"], { id: ID }),
        annotations: { title: "Leer nota", readOnlyHint: true } },
      { name: "notes_update", description: "Change the title and/or text of a note.",
        inputSchema: object(["id"], { id: ID, title: TITLE, body: BODY }), annotations: { title: "Editar nota" } },
      { name: "notes_delete", description: "Delete a note for good.", inputSchema: object(["id"], { id: ID }),
        annotations: { title: "Borrar nota", destructiveHint: true } },
    ];
  },
  async call(name, args, { store }) {
    const id = args.id as string;
    const missing = () => new ToolError("NOT_FOUND", `There is no note with id '${id}'. Use notes_list to find it.`);
    switch (name) {
      case "notes_create": {
        if (await store.notesCount() >= MAX_NOTES) throw new ToolError("LIMIT", `The notepad is full (${MAX_NOTES} notes).`);
        const note = await store.notesCreate(String(args.title).trim(), String(args.body ?? ""));
        return text(`Saved note "${note.title}" (id ${note.id}).`, note);
      }
      case "notes_list": {
        const notes = await store.notesList(String(args.query ?? "").trim(), (args.limit as number) ?? 20);
        return text(notes.length ? notes.map((n) => `${n.id} · ${n.title}`).join("\n") : "No notes.", { notes: notes.map(brief) });
      }
      case "notes_get": {
        const note = await store.notesGet(id);
        if (!note) throw missing();
        return text(`${note.title}\n\n${note.body}`, note);
      }
      case "notes_update": {
        if (args.title === undefined && args.body === undefined) throw new ToolError("INVALID_ARGUMENT", "Send 'title' and/or 'body'.");
        const note = await store.notesUpdate(id, args.title === undefined ? null : String(args.title).trim(),
          args.body === undefined ? null : String(args.body));
        if (!note) throw missing();
        return text(`Updated note "${note.title}".`, note);
      }
      case "notes_delete":
        if (!(await store.notesDelete(id))) throw missing();
        return text(`Deleted note ${id}.`, { deleted: id });
    }
    throw new ToolError("UNKNOWN_TOOL", `Unknown notes tool '${name}'.`);
  },
};
