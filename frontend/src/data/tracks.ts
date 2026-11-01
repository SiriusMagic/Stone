export type LyricLine = { timeMs: number; text: string };

export type Track = {
  id: string;
  title: string;
  artist: string;
  album: string;
  folder: string;
  kind?: "audio" | "video";
  cover: string;
  url: string;
  localUri?: string;
  durationMs: number;
  lyrics?: string[];
  syncedLyrics?: LyricLine[];
};

export const DEMO_TRACKS: Track[] = [];

export function formatMs(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}
