// /setup: a phone-friendly page where the owner links Stone by pasting the link copied from Stone.
// Protected by the hub token typed in the form (no cookies, so no CSRF), with every value escaped.

export const SETUP_HEADERS = {
  "Content-Type": "text/html; charset=utf-8",
  "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
};

export function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));
}

export interface SetupView { linked: boolean; message?: string; error?: boolean; modules: string[] }

export function setupPage(view: SetupView): string {
  const notice = view.message
    ? `<p class="${view.error ? "bad" : "good"}">${escapeHtml(view.message)}</p>` : "";
  return `<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Stone Hub</title>
<style>
:root{color-scheme:light dark;--bg:#111015;--card:#1d1b24;--text:#f3f0fa;--muted:#a49eb3;--gold:#e8b931;--bad:#ff7a7a;--good:#7ddc9a}
@media (prefers-color-scheme:light){:root{--bg:#f6f4fa;--card:#fff;--text:#1b1724;--muted:#5d566b}}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,sans-serif}
main{max-width:480px;margin:0 auto;padding:24px 16px}h1{font-size:22px;margin:0 0 4px}
.card{background:var(--card);border-radius:16px;padding:16px;margin:16px 0}
label{display:block;font-weight:600;margin:12px 0 4px}small,.muted{color:var(--muted)}
input{box-sizing:border-box;width:100%;padding:12px;border-radius:10px;border:1px solid var(--muted);background:transparent;color:inherit;font-size:15px}
button{margin-top:14px;width:100%;padding:12px;border:0;border-radius:10px;background:var(--gold);color:#1b1724;font-weight:700;font-size:16px}
button.secondary{background:transparent;color:var(--text);border:1px solid var(--muted)}
.good{color:var(--good)}.bad{color:var(--bad)}
</style></head><body><main>
<h1>Stone Hub</h1><p class="muted">Un solo enlace MCP para: ${escapeHtml(view.modules.join(" · "))}</p>
${notice}
<div class="card"><b>Música (Stone):</b> ${view.linked ? '<span class="good">vinculado</span>' : '<span class="bad">sin vincular</span>'}
<form method="post" autocomplete="off">
<label for="token">Token del hub</label><input id="token" name="token" type="password" required>
<label for="link">Enlace de Stone</label>
<input id="link" name="link" type="url" placeholder="https://…/d/…/mcp?token=stn_…">
<small>Stone › Ajustes › Modo API › crea un token solo para el hub y toca Copiar.</small>
<button name="action" value="link">Vincular Stone</button>
${view.linked ? '<button class="secondary" name="action" value="unlink">Desvincular</button>' : ""}
</form></div>
<p class="muted">Conecta tu IA con <b>https://&lt;este-sitio&gt;/mcp?token=…</b> o con la cabecera Authorization: Bearer.</p>
</main></body></html>`;
}
