import * as FileSystem from "expo-file-system/legacy";
import { Platform } from "react-native";

import { Track } from "@/src/data/tracks";
import {
  getLibraryDirectoryPreference,
  pickLibraryDirectory,
  upsertLibraryTrack,
} from "@/src/features/library/local-library";
import { storage } from "@/src/utils/storage";

const LOCAL_TRACKS_KEY = "@app/library-local-downloads";
const DOWNLOADS_DIR = `${FileSystem.documentDirectory ?? ""}stone-downloads/`;
const FALLBACK_EXT: Record<string, string> = { audio: "mp3", video: "mp4" };

export type DownloadDirectoryPreference = {
  directoryUri: string | null;
  label: string;
};

function sanitizeName(value: string) {
  return value.replace(/[<>:"/\\|?*\u0000-\u001F]/g, "").trim() || "track";
}

function getTrackFileName(track: Track) {
  const ext = track.url.split(".").pop()?.split("?")[0] || FALLBACK_EXT[track.kind ?? "audio"];
  return `${sanitizeName(`${track.artist}-${track.title}`)}.${ext}`;
}

async function ensureDownloadDir() {
  if (Platform.OS === "web" || !FileSystem.documentDirectory) return;
  const info = await FileSystem.getInfoAsync(DOWNLOADS_DIR);
  if (!info.exists) await FileSystem.makeDirectoryAsync(DOWNLOADS_DIR, { intermediates: true });
}

function getMimeType(track: Track) {
  return track.kind === "video" ? "video/mp4" : "audio/mpeg";
}

export async function getDownloadDirectoryPreference(): Promise<DownloadDirectoryPreference> {
  const preference = await getLibraryDirectoryPreference();
  return {
    directoryUri: preference.directoryUri,
    label: preference.label || "Stone",
  };
}

export async function pickDownloadDirectory() {
  const preference = await pickLibraryDirectory();
  return {
    directoryUri: preference.directoryUri,
    label: preference.label || "Stone",
  };
}

async function mirrorTrackToSelectedDirectory(track: Track, localUri: string) {
  if (
    Platform.OS !== "android" ||
    !FileSystem.StorageAccessFramework?.createFileAsync
  ) {
    return;
  }

  const preference = await getDownloadDirectoryPreference();
  if (!preference.directoryUri) return;

  const ext =
    track.url.split(".").pop()?.split("?")[0] || FALLBACK_EXT[track.kind ?? "audio"];
  const fileName = sanitizeName(`${track.artist}-${track.title}`);
  const targetUri = await FileSystem.StorageAccessFramework.createFileAsync(
    preference.directoryUri,
    fileName,
    getMimeType(track),
  );

  await FileSystem.copyAsync({
    from: localUri,
    to: targetUri,
  });
}

export async function getLocalDownloadedTracks() {
  return (await storage.getItem<Track[]>(LOCAL_TRACKS_KEY, [])) ?? [];
}

export async function migrateLegacyDownloadedTracksToLibrary() {
  const current = await getLocalDownloadedTracks();
  if (!current.length) return 0;
  for (const track of current) {
    await upsertLibraryTrack(track);
  }
  await storage.setItem(LOCAL_TRACKS_KEY, []);
  return current.length;
}

export async function saveLocalDownloadedTrack(track: Track) {
  const current = await getLocalDownloadedTracks();
  const next = [...current.filter((item) => item.id !== track.id), track];
  await storage.setItem(LOCAL_TRACKS_KEY, next);
  return next;
}

export async function removeLocalDownloadedTrack(trackId: string) {
  const current = await getLocalDownloadedTracks();
  const next = current.filter((item) => item.id !== trackId);
  await storage.setItem(LOCAL_TRACKS_KEY, next);
  return next;
}

export async function materializeTrackForDevice(track: Track) {
  if (Platform.OS === "web" || !track.url.startsWith("http")) return track;
  await ensureDownloadDir();
  const localUri = `${DOWNLOADS_DIR}${getTrackFileName(track)}`;
  const existing = await FileSystem.getInfoAsync(localUri);
  if (!existing.exists) await FileSystem.downloadAsync(track.url, localUri);
  await mirrorTrackToSelectedDirectory(track, localUri).catch(() => {});
  return { ...track, localUri };
}

export async function persistDownloadedTrack(track: Track) {
  const materialized = await materializeTrackForDevice(track);
  await saveLocalDownloadedTrack(materialized);
  await upsertLibraryTrack(materialized);
  return materialized;
}
