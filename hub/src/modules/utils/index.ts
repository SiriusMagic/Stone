import { type HubModule, ToolError, text } from "../types.ts";

export const utilsModule: HubModule = {
  prefix: "utils_",
  title: "Utilidades",
  summary: "Current date and time in any time zone, and fair random numbers.",
  async tools() {
    return [
      { name: "utils_time", description: "Current date and time. `timezone` is an IANA name like America/Bogota (default UTC).",
        inputSchema: { type: "object", additionalProperties: false,
          properties: { timezone: { type: "string", maxLength: 64 } } },
        annotations: { title: "Hora actual", readOnlyHint: true } },
      { name: "utils_random", description: "A random whole number between min and max (both included).",
        inputSchema: { type: "object", additionalProperties: false, required: ["min", "max"],
          properties: { min: { type: "integer", minimum: -1e9, maximum: 1e9 }, max: { type: "integer", minimum: -1e9, maximum: 1e9 } } },
        annotations: { title: "Número al azar", readOnlyHint: true } },
    ];
  },
  async call(name, args) {
    if (name === "utils_time") {
      const timezone = (args.timezone as string | undefined) ?? "UTC";
      let local: string;
      try {
        local = new Intl.DateTimeFormat("es", { timeZone: timezone, dateStyle: "full", timeStyle: "long" }).format(new Date());
      } catch {
        throw new ToolError("INVALID_ARGUMENT", `Unknown time zone '${timezone}'. Use an IANA name like Europe/Madrid.`);
      }
      return text(local, { iso: new Date().toISOString(), timezone, local });
    }
    if (name === "utils_random") {
      const min = args.min as number, max = args.max as number;
      if (min > max) throw new ToolError("INVALID_ARGUMENT", "'min' must not be greater than 'max'.");
      const span = max - min + 1, limit = Math.floor(2 ** 32 / span) * span; // rejection sampling: no bias
      const buffer = new Uint32Array(1);
      do crypto.getRandomValues(buffer); while (buffer[0] >= limit);
      const value = min + (buffer[0] % span);
      return text(String(value), { value });
    }
    throw new ToolError("UNKNOWN_TOOL", `Unknown utility tool '${name}'.`);
  },
};
