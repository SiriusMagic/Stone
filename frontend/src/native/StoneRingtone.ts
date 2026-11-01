import { NativeModules, Platform } from "react-native";

type StoneRingtoneModule = { setRingtone(uri: string, title: string, startMs: number): Promise<boolean> };
const native = NativeModules.StoneRingtone as StoneRingtoneModule | undefined;

export async function setStoneRingtone(uri: string, title: string, startMs: number) {
  if (Platform.OS !== "android" || !native) throw new Error("Esta función solo está disponible en Android.");
  return native.setRingtone(uri, title, Math.max(0, Math.round(startMs)));
}
