// Safe arithmetic: a tiny recursive-descent parser. It never runs code (no eval / Function),
// so an expression like "constructor.constructor('…')()" is just a syntax error.

const FUNCTIONS: Record<string, (x: number) => number> = {
  sqrt: Math.sqrt, abs: Math.abs, round: Math.round, floor: Math.floor, ceil: Math.ceil,
  sin: Math.sin, cos: Math.cos, tan: Math.tan, ln: Math.log, log: Math.log10, exp: Math.exp,
};
const CONSTANTS: Record<string, number> = { pi: Math.PI, e: Math.E };
export const MAX_EXPRESSION = 300;

export function evaluate(expression: string): number {
  if (expression.length > MAX_EXPRESSION) throw new SyntaxError(`Expression longer than ${MAX_EXPRESSION} characters`);
  const tokens = expression.match(/\d+(?:[.,]\d+)?|[a-z]+|\*\*|[-+*/%^()]|\S/gi) ?? [];
  let pos = 0, depth = 0;
  const peek = () => tokens[pos];
  const take = () => tokens[pos++];

  function sum(): number {
    let value = product();
    while (peek() === "+" || peek() === "-") value = take() === "+" ? value + product() : value - product();
    return value;
  }
  function product(): number {
    let value = unary();
    while (peek() === "*" || peek() === "/" || peek() === "%") {
      const op = take(), right = unary();
      value = op === "*" ? value * right : op === "/" ? value / right : value % right;
    }
    return value;
  }
  // Sign before power, as in math: -2^2 = -(2^2) = -4, and 2^-1 = 0.5.
  function unary(): number {
    if (peek() === "-") { take(); return -unary(); }
    if (peek() === "+") { take(); return unary(); }
    return power();
  }
  function power(): number {
    const base = atom();
    if (peek() === "^" || peek() === "**") { take(); return base ** unary(); } // right associative
    return base;
  }
  function atom(): number {
    const token = take();
    if (token === undefined) throw new SyntaxError("Expression ends too early");
    if (token === "(") {
      if (++depth > 50) throw new SyntaxError("Too many nested parentheses");
      const value = sum();
      if (take() !== ")") throw new SyntaxError("Missing ')'");
      depth--;
      return value;
    }
    if (/^\d/.test(token)) return Number(token.replace(",", "."));
    const name = token.toLowerCase();
    if (name in CONSTANTS) return CONSTANTS[name];
    if (Object.hasOwn(FUNCTIONS, name)) {
      if (take() !== "(") throw new SyntaxError(`'${name}' needs parentheses: ${name}(…)`);
      const value = sum();
      if (take() !== ")") throw new SyntaxError("Missing ')'");
      return FUNCTIONS[name](value);
    }
    throw new SyntaxError(`Unexpected '${token}'`);
  }

  const result = sum();
  if (pos < tokens.length) throw new SyntaxError(`Unexpected '${tokens[pos]}'`);
  if (!Number.isFinite(result)) throw new RangeError("The result is not a finite number (division by zero?)");
  return result;
}
