import { NativeModules, Platform } from "react-native";

export type AndroidTrackMetadata = {
  title: string | null;
  artist: string | null;
  album: string | null;
  genre: string | null;
  year: string | null;
  trackNumber: string | null;
  durationMs: number;
  artworkUri: string | null;
  lyrics: string[];
};

export type EditableTrackMetadata = Pick<
  AndroidTrackMetadata,
  "title" | "artist" | "album" | "genre" | "year" | "trackNumber"
>;

type StoneMediaMetadataModule = {
  readMetadata(uri: string): Promise<AndroidTrackMetadata | null>;
  writeMetadata(uri: string, values: EditableTrackMetadata): Promise<AndroidTrackMetadata | null>;
};

const moduleRef = NativeModules.StoneMediaMetadata as StoneMediaMetadataModule | undefined;

export async function readAndroidTrackMetadata(uri: string) {
  if (Platform.OS !== "android" || !moduleRef?.readMetadata) return null;
  try {
    return await moduleRef.readMetadata(uri);
  } catch {
    return null;
  }
}

export async function writeAndroidTrackMetadata(uri: string, values: EditableTrackMetadata) {
  if (Platform.OS !== "android" || !moduleRef?.writeMetadata) return null;
  try {
    return await moduleRef.writeMetadata(uri, values);
  } catch {
    return null;
  }
}
