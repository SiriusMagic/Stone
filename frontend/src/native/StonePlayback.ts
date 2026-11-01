import { NativeEventEmitter, NativeModules, Platform } from "react-native";

export type PlaybackStatus = {
  durationSeconds: number;
  playing: boolean;
  positionSeconds: number;
  rate: number;
  volumePercent: number;
};

const native = NativeModules.StonePlayback;
const emitter = Platform.OS === "android" && native ? new NativeEventEmitter(native) : null;

export const usesStonePlayback = Platform.OS === "android" && Boolean(native);

export function listenStonePlayback(listener: (status: PlaybackStatus) => void) {
  return emitter?.addListener("StonePlaybackStatus", listener) ?? { remove() {} };
}

export function listenStonePlaybackCommand(listener: (command: "next" | "previous" | "completed") => void) {
  return emitter?.addListener("StonePlaybackCommand", listener) ?? { remove() {} };
}

export const stonePlayback = {
  load: (uri: string, title: string, artist: string, artworkUri?: string | null) =>
    native?.load(uri, title, artist, artworkUri ?? null),
  play: () => native?.play(),
  pause: () => native?.pause(),
  seekTo: (seconds: number) => native?.seekTo(seconds),
  setRate: (rate: number) => native?.setPlaybackRate(rate),
  setEqualizer: (enabled: boolean, values: number[]) => native?.setEqualizer(enabled, values),
  setQueue: (items: { id: string; title: string; artist: string }[], currentIndex: number) => native?.setQueue(items, currentIndex),
  setVolumeFromUser: (percent: number): Promise<PlaybackStatus> | undefined => native?.setVolumePercentFromUser(percent),
  status: (): Promise<PlaybackStatus> | undefined => native?.getStatus(),
};
