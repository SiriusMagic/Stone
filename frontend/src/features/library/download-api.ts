import {
  DownloadAnalyzeResult,
  DownloadQueueRequestItem,
  DownloadQueueSnapshot,
  PlaylistFormatsResult,
} from "./types";
import { getApiBaseUrl, getApiOrigin } from "@/src/config/api";

async function postJson<T>(path: string, body: unknown): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${getApiBaseUrl()}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    throw new Error(
      "No se pudo conectar con el servidor de descargas. Revisa la red o la direccion del backend.",
    );
  }

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const message =
      payload?.detail?.message ||
      payload?.message ||
      "No se pudo completar la solicitud.";
    throw new Error(message);
  }

  return payload as T;
}

async function getJson<T>(path: string): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${getApiBaseUrl()}${path}`);
  } catch {
    throw new Error(
      "No se pudo conectar con el servidor de descargas. Revisa la red o la direccion del backend.",
    );
  }

  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const message =
      payload?.detail?.message ||
      payload?.message ||
      "No se pudo completar la solicitud.";
    throw new Error(message);
  }

  return payload as T;
}

export function analyzeDownloadUrl(url: string) {
  return postJson<DownloadAnalyzeResult>("/downloads/analyze", { url });
}

export function fetchPlaylistFormats(url: string, selectedEntryIds: string[]) {
  return postJson<PlaylistFormatsResult>("/downloads/playlist-formats", {
    url,
    selected_entry_ids: selectedEntryIds,
  });
}

export function fetchDownloadQueue() {
  return getJson<DownloadQueueSnapshot>("/downloads/queue");
}

export function enqueueDownloads(
  mode: "audio" | "video",
  formatId: string,
  formatLabel: string,
  items: DownloadQueueRequestItem[],
) {
  return postJson<DownloadQueueSnapshot>("/downloads/queue/enqueue", {
    mode,
    format_id: formatId,
    format_label: formatLabel,
    items,
  });
}

export function removeQueuedDownloads(entryIds: string[]) {
  return postJson<DownloadQueueSnapshot>("/downloads/queue/remove", {
    entry_ids: entryIds,
  });
}

export function reorderQueuedDownloads(entryIds: string[]) {
  return postJson<DownloadQueueSnapshot>("/downloads/queue/reorder", {
    entry_ids: entryIds,
  });
}

export function cancelAllQueuedDownloads() {
  return postJson<DownloadQueueSnapshot>("/downloads/queue/cancel-all", {});
}

export function resolveDownloadMediaUrl(path: string | null | undefined) {
  if (!path) return "";
  return `${getApiOrigin()}${path}`;
}
