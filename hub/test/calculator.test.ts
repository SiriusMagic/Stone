import { test } from "node:test";
import assert from "node:assert/strict";
import { evaluate } from "../src/modules/calculator/parser.ts";

test("precedence, power and functions", () => {
  assert.equal(evaluate("(150 * 0.15) + 20"), 42.5);
  assert.equal(evaluate("2 + 3 * 4"), 14);
  assert.equal(evaluate("2^3^2"), 512);          // right associative
  assert.equal(evaluate("-2^2"), -4);
  assert.equal(evaluate("2^-1"), 0.5);
  assert.equal(evaluate("sqrt(16) + abs(-3)"), 7);
  assert.equal(evaluate("10 % 4"), 2);
  assert.equal(evaluate("1,5 * 2"), 3);           // decimal comma
  assert.ok(Math.abs(evaluate("sin(pi/2)") - 1) < 1e-12);
});

test("never runs code and rejects bad input", () => {
  for (const bad of ["constructor.constructor('return process')()", "alert(1)", "this", "2 +", "(1", "1 / 0",
    "1".repeat(301), "(".repeat(60) + "1" + ")".repeat(60), "__proto__", "toString(1)"]) {
    assert.throws(() => evaluate(bad), bad);
  }
});
