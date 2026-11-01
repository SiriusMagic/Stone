import * as FileSystem from "expo-file-system/legacy";
import { LyricLine } from "@/src/data/tracks";

const LRC_EXTENSIONS = new Set(["lrc", "txt"]);

export function getSiblingLyricsUri(uri: string) {
  const cleanUri = uri.split(/[?#]/, 1)[0];
  const extensionIndex = cleanUri.lastIndexOf(".");
  const baseUri = extensionIndex > cleanUri.lastIndexOf("/") ? cleanUri.slice(0, extensionIndex) : cleanUri;
  return Array.from(LRC_EXTENSIONS).map((ext) => `${baseUri}.${ext}`);
}

export function parseLyricsText(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => line.replace(/\[[^\]]+\]/g, "").trim())
    .filter(Boolean);
}

export function parseLyricsDocument(value: string) {
  const offset = Number(value.match(/\[offset:([+-]?\d+)\]/i)?.[1] ?? 0);
  const syncedLyrics: LyricLine[] = [];
  for (const rawLine of value.split(/\r?\n/)) {
    const text = rawLine.replace(/\[[^\]]+\]/g, "").trim();
    if (!text) continue;
    const timestamps = [...rawLine.matchAll(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g)];
    timestamps.forEach((match) => {
      const fraction = match[3] ?? "0";
      const fractionMs = Number(fraction.padEnd(3, "0").slice(0, 3));
      const timeMs = Number(match[1]) * 60_000 + Number(match[2]) * 1000 + fractionMs + offset;
      syncedLyrics.push({ timeMs: Math.max(0, timeMs), text });
    });
  }
  syncedLyrics.sort((left, right) => left.timeMs - right.timeMs);
  return { lyrics: parseLyricsText(value), syncedLyrics };
}

export async function readLocalLyrics(uri: string) {
  for (const lyricUri of getSiblingLyricsUri(uri)) {
    try {
      const text = await FileSystem.readAsStringAsync(lyricUri);
      const document = parseLyricsDocument(text);
      if (document.lyrics.length > 0) return document;
    } catch {
      continue;
    }
  }
  return { lyrics: [], syncedLyrics: [] };
}
