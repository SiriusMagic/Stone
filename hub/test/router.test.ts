import { test } from "node:test";
import assert from "node:assert/strict";
import { createRouter } from "../src/router.ts";
import { type HubModule, ToolError, text } from "../src/modules/types.ts";
import { MemoryStore } from "./helpers.ts";

const mod = (prefix: string, names: string[], fail = false): HubModule => ({
  prefix, title: prefix, summary: "",
  async tools() { if (fail) throw new Error("down"); return names.map((name) => ({ name, description: "", inputSchema: { type: "object", properties: {} } })); },
  async call(name) { return text(`${prefix}:${name}`); },
});
const ctx = { store: new MemoryStore(), env: { STONE_RELAY_HOST: "x" }, fetch };

test("routes by prefix and lists every module", async () => {
  const router = createRouter([mod("a_", ["a_one"]), mod("bb_", ["bb_two"])]);
  assert.deepEqual((await router.listTools(ctx)).map((t) => t.name), ["a_one", "bb_two"]);
  assert.equal((await router.call("bb_two", {}, ctx)).content[0].text, "bb_:bb_two");
});

test("rejects overlapping or invalid prefixes", () => {
  assert.throws(() => createRouter([mod("ab_", []), mod("ab_x_", [])]));
  assert.throws(() => createRouter([mod("Music_", [])]));
});

test("a failing module does not hide the others; foreign names are dropped", async () => {
  const router = createRouter([mod("a_", ["a_ok", "b_sneaky", "a_bad name"]), mod("c_", [], true)]);
  assert.deepEqual((await router.listTools(ctx)).map((t) => t.name), ["a_ok"]);
});

test("unknown tools give a coded error", async () => {
  const router = createRouter([mod("a_", ["a_ok"])]);
  await assert.rejects(router.call("zz_x", {}, ctx), (e: ToolError) => e.code === "UNKNOWN_TOOL");
  await assert.rejects(router.call("a_missing", {}, ctx), (e: ToolError) => e.code === "UNKNOWN_TOOL");
});
