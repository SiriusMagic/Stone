// Server-side check of tool arguments against the tool's JSON schema (the subset the hub's own
// modules use): required fields, no unknown fields, types, lengths, ranges and enums.
import { ToolError } from "./modules/types.ts";

type Schema = { type?: string; properties?: Record<string, Schema>; required?: string[]; enum?: unknown[];
  maxLength?: number; minLength?: number; minimum?: number; maximum?: number; additionalProperties?: boolean };

export function validateArgs(schema: Schema, args: Record<string, unknown>): void {
  const properties = schema.properties ?? {};
  for (const key of schema.required ?? []) {
    if (args[key] === undefined || args[key] === null) throw new ToolError("INVALID_ARGUMENT", `'${key}' is required.`);
  }
  for (const [key, value] of Object.entries(args)) {
    const rule = properties[key];
    if (!rule) {
      if (schema.additionalProperties === false) throw new ToolError("INVALID_ARGUMENT", `Unknown argument '${key}'.`);
      continue;
    }
    check(key, rule, value);
  }
}

function check(key: string, rule: Schema, value: unknown): void {
  const fail = (why: string) => { throw new ToolError("INVALID_ARGUMENT", `'${key}' ${why}.`); };
  switch (rule.type) {
    case "string":
      if (typeof value !== "string") fail("must be text");
      if (rule.maxLength !== undefined && (value as string).length > rule.maxLength) fail(`is longer than ${rule.maxLength} characters`);
      if (rule.minLength !== undefined && (value as string).length < rule.minLength) fail(`needs at least ${rule.minLength} characters`);
      break;
    case "number": case "integer":
      if (typeof value !== "number" || !Number.isFinite(value)) fail("must be a number");
      if (rule.type === "integer" && !Number.isInteger(value)) fail("must be a whole number");
      if (rule.minimum !== undefined && (value as number) < rule.minimum) fail(`must be at least ${rule.minimum}`);
      if (rule.maximum !== undefined && (value as number) > rule.maximum) fail(`must be at most ${rule.maximum}`);
      break;
    case "boolean":
      if (typeof value !== "boolean") fail("must be true or false");
      break;
  }
  if (rule.enum && !rule.enum.includes(value)) fail(`must be one of ${rule.enum.join(", ")}`);
}
