const http = require("http");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..", "dist");
const port = 8081;

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8",
  ".map": "application/json; charset=utf-8",
  ".webp": "image/webp",
};

function sendFile(filePath, res) {
  fs.readFile(filePath, (error, data) => {
    if (error) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Not found");
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, {
      "Content-Type": mimeTypes[ext] || "application/octet-stream",
      "Cache-Control": "no-cache",
    });
    res.end(data);
  });
}

http.createServer((req, res) => {
  const requestPath = decodeURIComponent((req.url || "/").split("?")[0]);
  const safePath = requestPath === "/" ? "/index.html" : requestPath;
  const candidate = path.join(root, safePath);

  if (!candidate.startsWith(root)) {
    res.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Forbidden");
    return;
  }

  fs.stat(candidate, (statError, stats) => {
    if (!statError && stats.isFile()) {
      sendFile(candidate, res);
      return;
    }

    sendFile(path.join(root, "index.html"), res);
  });
}).listen(port, "127.0.0.1", () => {
  console.log(`Dist server running at http://127.0.0.1:${port}`);
});
