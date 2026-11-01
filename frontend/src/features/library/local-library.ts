import * as FileSystem from "expo-file-system/legacy";
import { Platform } from "react-native";

import { LyricLine, Track } from "@/src/data/tracks";
import { storage } from "@/src/utils/storage";
import { readAndroidTrackMetadata } from "@/src/features/library/android-media-metadata";
import { parseLyricsDocument, readLocalLyrics } from "@/src/features/library/library-lyrics";

type LyricsDocument = { lyrics: string[]; syncedLyrics: LyricLine[] };

const LIBRARY_DIRECTORY_URI_KEY = "@app/library-directory-uri";
const LIBRARY_DIRECTORY_LABEL_KEY = "@app/library-directory-label";
const LIBRARY_DIRECTORY_GRANTED_AT_KEY = "@app/library-directory-granted-at";
const LIBRARY_TRACKS_KEY = "@app/library-tracks";
const AUDIO_EXTENSIONS = new Set(["mp3", "m4a", "aac", "wav", "flac", "ogg"]);
const VIDEO_EXTENSIONS = new Set(["mp4", "mkv", "mov", "webm"]);

export type LibraryDirectoryPreference = {
  directoryUri: string | null;
  label: string;
  grantedAt: number | null;
};

function trackLibraryKey(track: Track) {
  return track.localUri || track.url || track.id;
}

function preserveKnownMetadata(track: Track, previous?: Track): Track {
  if (!previous) return track;
  return {
    ...track,
    title: track.title || previous.title,
    artist: track.artist === "Stone" ? previous.artist || track.artist : track.artist,
    album: track.album === track.folder ? previous.album || track.album : track.album,
    cover: track.cover || previous.cover,
    durationMs: track.durationMs || previous.durationMs,
    lyrics: track.lyrics?.length ? track.lyrics : previous.lyrics ?? [],
    syncedLyrics: track.syncedLyrics?.length ? track.syncedLyrics : previous.syncedLyrics ?? [],
  };
}

function getLabelFromUri(directoryUri: string) {
  const decoded = decodeURIComponent(directoryUri);
  const parts = decoded.split(/[/:]/).filter(Boolean);
  return parts[parts.length - 1] || "Stone";
}

function getFileNameFromUri(uri: string) {
  const decoded = decodeURIComponent(uri);
  const value = decoded.split("/").pop() || decoded.split(":").pop() || "track";
  return value.replace(/%2F/gi, "/").split("/").pop() || "track";
}

function sanitizeName(value: string) {
  return value.replace(/[<>:"/\\|?*\u0000-\u001F]/g, "").trim() || "track";
}

function getMimeType(ext: string, kind: "audio" | "video") {
  if (kind === "video") return "video/mp4";
  if (ext === "m4a") return "audio/mp4";
  if (ext === "ogg") return "audio/ogg";
  if (ext === "wav") return "audio/wav";
  if (ext === "flac") return "audio/flac";
  return "audio/mpeg";
}

function isInsideDirectory(sourceUri: string, directoryUri: string) {
  return decodeURIComponent(sourceUri).startsWith(decodeURIComponent(directoryUri));
}

async function createTrackFromUri(
  fileUri: string,
  folder: string,
  fileName?: string,
  lyricsOverride?: LyricsDocument,
) {
  const name = fileName || getFileNameFromUri(fileUri);
  const ext = name.split(".").pop()?.toLowerCase() || "";
  const kind = VIDEO_EXTENSIONS.has(ext) ? "video" : AUDIO_EXTENSIONS.has(ext) ? "audio" : null;
  if (!kind) return null;
  const fallbackTitle = name.replace(/\.[^.]+$/, "").replace(/[_-]+/g, " ").trim() || "Sin nombre";
  const metadata = await readAndroidTrackMetadata(fileUri);
  const sidecarLyrics = kind === "audio" ? await readLocalLyrics(fileUri) : { lyrics: [], syncedLyrics: [] };
  const lyricDocument = lyricsOverride ?? (sidecarLyrics.lyrics.length
    ? sidecarLyrics
    : { lyrics: metadata?.lyrics ?? [], syncedLyrics: [] });
  return {
    id: `local:${fileUri}`,
    title: metadata?.title || fallbackTitle,
    artist: metadata?.artist || "Stone",
    album: metadata?.album || folder,
    folder,
    kind,
    cover: metadata?.artworkUri || "",
    url: fileUri,
    localUri: fileUri,
    durationMs: metadata?.durationMs || 0,
    lyrics: lyricDocument.lyrics,
    syncedLyrics: lyricDocument.syncedLyrics,
  } satisfies Track;
}

async function copyUriIntoLibraryDirectory(fileUri: string, folder: string) {
  if (Platform.OS !== "android" || !FileSystem.StorageAccessFramework?.createFileAsync) {
    return { uri: fileUri, folder };
  }
  const preference = await getLibraryDirectoryPreference();
  if (!preference.directoryUri || isInsideDirectory(fileUri, preference.directoryUri)) {
    return { uri: fileUri, folder: preference.label || folder };
  }
  const sourceName = getFileNameFromUri(fileUri);
  const ext = sourceName.split(".").pop()?.toLowerCase() || "";
  const kind = VIDEO_EXTENSIONS.has(ext) ? "video" : "audio";
  const baseName = sanitizeName(sourceName.replace(/\.[^.]+$/, ""));
  const targetName = `${baseName}.${ext || (kind === "video" ? "mp4" : "mp3")}`;

  try {
    const targetUri = await FileSystem.StorageAccessFramework.createFileAsync(
      preference.directoryUri,
      targetName,
      getMimeType(ext, kind),
    );
    await FileSystem.copyAsync({ from: fileUri, to: targetUri });
    return { uri: targetUri, folder: preference.label || folder };
  } catch {
    const fallbackUri = await FileSystem.StorageAccessFramework.createFileAsync(
      preference.directoryUri,
      `${baseName}-${Date.now()}.${ext || (kind === "video" ? "mp4" : "mp3")}`,
      getMimeType(ext, kind),
    );
    await FileSystem.copyAsync({ from: fileUri, to: fallbackUri });
    return { uri: fallbackUri, folder: preference.label || folder };
  }
}

async function walkDirectory(directoryUri: string, folder = "Stone"): Promise<Track[]> {
  const children = await FileSystem.StorageAccessFramework.readDirectoryAsync(directoryUri);
  const lyricFiles = new Map<string, LyricsDocument>();
  const nestedTracks: Track[] = [];
  const mediaEntries: Array<{ uri: string; fileName: string }> = [];

  for (const childUri of children) {
    try {
      nestedTracks.push(...(await walkDirectory(childUri, getFileNameFromUri(childUri))));
      continue;
    } catch {
      const fileName = getFileNameFromUri(childUri);
      const ext = fileName.split(".").pop()?.toLowerCase() || "";
      if (ext === "lrc" || ext === "txt") {
        try {
          const text = await FileSystem.readAsStringAsync(childUri);
          lyricFiles.set(fileName.replace(/\.[^.]+$/, "").toLowerCase(), parseLyricsDocument(text));
        } catch {
          continue;
        }
        continue;
      }
      mediaEntries.push({ uri: childUri, fileName });
    }
  }

  for (const mediaEntry of mediaEntries) {
    const baseName = mediaEntry.fileName.replace(/\.[^.]+$/, "").toLowerCase();
    const track = await createTrackFromUri(
      mediaEntry.uri,
      folder,
      mediaEntry.fileName,
      lyricFiles.get(baseName),
    );
    if (track) nestedTracks.push(track);
  }

  return nestedTracks;
}

export async function getLibraryDirectoryPreference(): Promise<LibraryDirectoryPreference> {
  const directoryUri = await storage.getItem<string | null>(LIBRARY_DIRECTORY_URI_KEY, null);
  const label = await storage.getItem<string>(LIBRARY_DIRECTORY_LABEL_KEY, "Stone");
  const grantedAt = await storage.getItem<number | null>(LIBRARY_DIRECTORY_GRANTED_AT_KEY, null);
  return { directoryUri, label: label || "Stone", grantedAt };
}

export async function pickLibraryDirectory() {
  if (
    Platform.OS !== "android" ||
    !FileSystem.StorageAccessFramework?.requestDirectoryPermissionsAsync
  ) {
    return getLibraryDirectoryPreference();
  }
  const permission =
    await FileSystem.StorageAccessFramework.requestDirectoryPermissionsAsync();
  if (!permission.granted || !permission.directoryUri) {
    return getLibraryDirectoryPreference();
  }
  const label = getLabelFromUri(permission.directoryUri);
  const grantedAt = Date.now();
  await storage.setItem(LIBRARY_DIRECTORY_URI_KEY, permission.directoryUri);
  await storage.setItem(LIBRARY_DIRECTORY_LABEL_KEY, label);
  await storage.setItem(LIBRARY_DIRECTORY_GRANTED_AT_KEY, grantedAt);
  return { directoryUri: permission.directoryUri, label, grantedAt };
}

export async function getLibraryTracks() {
  return (await storage.getItem<Track[]>(LIBRARY_TRACKS_KEY, [])) ?? [];
}

export async function saveLibraryTracks(tracks: Track[]) {
  const seen = new Set<string>();
  const deduped = tracks.filter((track) => {
    const key = trackLibraryKey(track);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  await storage.setItem(LIBRARY_TRACKS_KEY, deduped);
  return deduped;
}

export async function upsertLibraryTrack(track: Track) {
  const current = await getLibraryTracks();
  const currentKey = trackLibraryKey(track);
  const next = [track, ...current.filter((item) => trackLibraryKey(item) !== currentKey)];
  return saveLibraryTracks(next);
}

export async function scanLibraryDirectory() {
  const { directoryUri, label } = await getLibraryDirectoryPreference();
  if (!directoryUri || Platform.OS !== "android") return [];
  const tracks = await walkDirectory(directoryUri, label);
  const previous = await getLibraryTracks();
  const previousByUri = new Map(previous.map((track) => [trackLibraryKey(track), track]));
  return saveLibraryTracks(
    tracks.map((track) => preserveKnownMetadata(track, previousByUri.get(trackLibraryKey(track)))),
  );
}

export async function importTrackFromUri(fileUri: string) {
  const { label } = await getLibraryDirectoryPreference();
  const imported = await copyUriIntoLibraryDirectory(fileUri, label || "Importados");
  const track = await createTrackFromUri(imported.uri, imported.folder, getFileNameFromUri(fileUri));
  if (!track) return null;
  const current = await getLibraryTracks();
  const existing = current.find((item) => trackLibraryKey(item) === trackLibraryKey(track));
  if (existing) return existing;
  await upsertLibraryTrack(track);
  return track;
}
