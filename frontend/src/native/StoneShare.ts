import { NativeModules, Platform } from "react-native";

type StoneShareModule = { shareAudio(uri: string, title: string): Promise<boolean> };
const native = NativeModules.StoneShare as StoneShareModule | undefined;

export async function shareStoneAudio(uri: string, title: string) {
  if (Platform.OS !== "android" || !native) throw new Error("Compartir archivos solo está disponible en Android.");
  return native.shareAudio(uri, title);
}
