import { type HubModule, ToolError, text } from "../types.ts";
import { evaluate, MAX_EXPRESSION } from "./parser.ts";

const round = (x: number) => Number(x.toPrecision(15));

export const calculatorModule: HubModule = {
  prefix: "calc_",
  title: "Calculadora",
  summary: "Exact arithmetic: expressions with + - * / % ^, parentheses, sqrt, sin, log… and percentages.",
  async tools() {
    return [
      { name: "calc_evaluate", description: "Evaluate a math expression, e.g. (150 * 0.15) + 20, sqrt(2)^2, 2^10, sin(pi/2). " +
          "Supports + - * / % ^, parentheses, sqrt abs round floor ceil sin cos tan ln log exp, pi and e.",
        inputSchema: { type: "object", additionalProperties: false, required: ["expression"],
          properties: { expression: { type: "string", minLength: 1, maxLength: MAX_EXPRESSION } } },
        annotations: { title: "Calcular", readOnlyHint: true } },
      { name: "calc_percentage", description: "What is `percent` % of `value` (15 % of 80 = 12).",
        inputSchema: { type: "object", additionalProperties: false, required: ["percent", "value"],
          properties: { percent: { type: "number" }, value: { type: "number" } } },
        annotations: { title: "Porcentaje", readOnlyHint: true } },
    ];
  },
  async call(name, args) {
    try {
      if (name === "calc_evaluate") {
        const result = round(evaluate(String(args.expression)));
        return text(`${args.expression} = ${result}`, { expression: args.expression, result });
      }
      if (name === "calc_percentage") {
        const result = round((args.percent as number) / 100 * (args.value as number));
        return text(`${args.percent} % of ${args.value} = ${result}`, { result });
      }
    } catch (error) {
      throw new ToolError("INVALID_EXPRESSION", (error as Error).message);
    }
    throw new ToolError("UNKNOWN_TOOL", `Unknown calculator tool '${name}'.`);
  },
};
