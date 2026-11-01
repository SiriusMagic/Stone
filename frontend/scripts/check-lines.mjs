import fs from "fs";
import path from "path";

const limits = {
  ".tsx": 80,
  ".ts": 60,
  ".md": 40,
};

const roots = ["app", "src"];

function walk(dir) {
  if (!fs.existsSync(dir)) return;

  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const stat = fs.statSync(full);

    if (stat.isDirectory()) {
      walk(full);
      continue;
    }

    check(full);
  }
}

function check(file) {
  const ext = path.extname(file);
  const max = limits[ext];
  if (!max) return;

  const lines = fs.readFileSync(file, "utf8").split("\n").length;

  if (lines > max) {
    console.error(`${file}: ${lines}/${max} líneas`);
    process.exitCode = 1;
  }
}

roots.forEach(walk);
