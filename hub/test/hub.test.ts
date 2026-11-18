// End to end through serve(): auth, limits, MCP protocol, every module, Stone link and /setup.
import { test } from "node:test";
import assert from "node:assert/strict";
import { RELAY, STONE_LINK, TOKEN, MemoryStore, fakeStone, hub } from "./helpers.ts";

test("token required, compared exactly, and wrong tokens throttled", async () => {
  const h = hub();
  assert.equal((await h.rpc("ping", {}, null)).status, 401);
  assert.equal((await h.rpc("ping", {}, TOKEN + "x")).status, 401);
  assert.equal((await h.rpc("ping")).status, 200);
  for (let i = 0; i < 20; i++) await h.rpc("ping", {}, "nope", "6.6.6.6");
  assert.equal((await h.rpc("ping", {}, TOKEN, "6.6.6.6")).status, 429); // that caller is blocked a minute
  assert.equal((await h.rpc("ping", {}, TOKEN, "7.7.7.7")).status, 200); // others are not
});

test("rate limit per caller", async () => {
  const h = hub();
  let last = 0;
  for (let i = 0; i < 121; i++) last = (await h.rpc("ping", {}, TOKEN, "9.9.9.9")).status;
  assert.equal(last, 429);
});

test("initialize names every module; notifications get 202", async () => {
  const h = hub();
  const init = await h.rpc("initialize", { protocolVersion: "2025-06-18" });
  assert.equal(init.body.result.protocolVersion, "2025-06-18");
  for (const p of ["music_", "notes_", "calc_", "utils_"]) assert.match(init.body.result.instructions, new RegExp(p));
  assert.ok(init.response.headers.get("mcp-session-id"));
  assert.equal(init.response.headers.get("x-content-type-options"), "nosniff");
});

test("calculator and utils through MCP, with server-side validation", async () => {
  const h = hub();
  const ok = await h.rpc("tools/call", { name: "calc_evaluate", arguments: { expression: "(150 * 0.15) + 20" } });
  assert.equal(ok.body.result.structuredContent.result, 42.5);
  const bad = await h.rpc("tools/call", { name: "calc_evaluate", arguments: { expression: 5 } });
  assert.equal(bad.body.result.isError, true);
  assert.equal(bad.body.result.structuredContent.error.code, "INVALID_ARGUMENT");
  const extra = await h.rpc("tools/call", { name: "calc_percentage", arguments: { percent: 15, value: 80, evil: 1 } });
  assert.equal(extra.body.result.structuredContent.error.code, "INVALID_ARGUMENT");
  const pct = await h.rpc("tools/call", { name: "calc_percentage", arguments: { percent: 15, value: 80 } });
  assert.equal(pct.body.result.structuredContent.result, 12);
  const tz = await h.rpc("tools/call", { name: "utils_time", arguments: { timezone: "Not/AZone" } });
  assert.equal(tz.body.result.isError, true);
  const r = await h.rpc("tools/call", { name: "utils_random", arguments: { min: 1, max: 6 } });
  assert.ok(r.body.result.structuredContent.value >= 1 && r.body.result.structuredContent.value <= 6);
});

test("notes: create, search, update, delete", async () => {
  const h = hub();
  const made = await h.rpc("tools/call", { name: "notes_create", arguments: { title: "Compras", body: "leche y pan" } });
  const id = made.body.result.structuredContent.id;
  const found = await h.rpc("tools/call", { name: "notes_list", arguments: { query: "pan" } });
  assert.equal(found.body.result.structuredContent.notes[0].id, id);
  await h.rpc("tools/call", { name: "notes_update", arguments: { id, body: "leche" } });
  assert.equal((await h.rpc("tools/call", { name: "notes_get", arguments: { id } })).body.result.structuredContent.body, "leche");
  assert.equal((await h.rpc("tools/call", { name: "notes_delete", arguments: { id } })).body.result.isError, undefined);
  const gone = await h.rpc("tools/call", { name: "notes_get", arguments: { id } });
  assert.equal(gone.body.result.structuredContent.error.code, "NOT_FOUND");
  const long = await h.rpc("tools/call", { name: "notes_create", arguments: { title: "x".repeat(201) } });
  assert.equal(long.body.result.structuredContent.error.code, "INVALID_ARGUMENT");
});

test("music: Stone tools appear as music_* and calls go to Stone with the token in the header", async () => {
  const stone = fakeStone();
  const h = hub(new MemoryStore(), stone.fetcher);
  const before = await h.rpc("tools/list");
  assert.deepEqual(before.body.result.tools.filter((t: any) => t.name.startsWith("music_")).map((t: any) => t.name), ["music_status"]);
  assert.match((await h.setup({ token: TOKEN, action: "link", link: STONE_LINK })).html, /vinculado y en línea/);
  const names = (await h.rpc("tools/list")).body.result.tools.map((t: any) => t.name);
  assert.ok(names.includes("music_player_state") && names.includes("music_playlist_delete"));
  const played = await h.rpc("tools/call", { name: "music_player_state", arguments: {} });
  assert.equal(played.body.result.content[0].text, "ran player_state");
  const last = stone.calls.at(-1)!;
  assert.equal(last.url, `https://${RELAY}/d/abcdefghijkl12/mcp`);            // no token in the URL
  assert.equal(last.auth, "Bearer stn_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345");
});

test("setup: needs the hub token, accepts only links of the relay, refuses tokens Stone rejects", async () => {
  const h = hub();
  assert.equal((await h.setup({ token: "wrong", action: "link", link: STONE_LINK })).status, 401);
  for (const link of ["https://evil.example/d/abcdefghijkl12/mcp?token=stn_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345",
    `http://${RELAY}/d/abcdefghijkl12/mcp?token=stn_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345`,
    `https://${RELAY}/d/abcdefghijkl12/mcp`, `https://${RELAY}/admin?token=stn_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345`]) {
    assert.equal((await h.setup({ token: TOKEN, action: "link", link })).status, 400, link);
  }
  const rejected = hub(new MemoryStore(), fakeStone({ status: 401 }).fetcher);
  assert.equal((await rejected.setup({ token: TOKEN, action: "link", link: STONE_LINK })).status, 400);
  assert.equal(await rejected.store.getConfig("music.link"), null);
});

test("setup page escapes what it shows", async () => {
  const h = hub();
  const page = await h.setup({ token: TOKEN, action: "link", link: "<script>alert(1)</script>" });
  assert.doesNotMatch(page.html, /<script>alert/);
});
