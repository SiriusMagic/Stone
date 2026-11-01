import { useCallback, useEffect, useRef, useState } from "react";
import { AppState, BackHandler, Pressable, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import * as Brightness from "expo-brightness";
import { Artwork } from "@/src/components/Artwork";

const SAVER_BRIGHTNESS = 0.12;
const SCREEN_OFF_BRIGHTNESS = 0.01;
const SCREEN_OFF_DELAY_MS = 15000;

type Props = {
  cover?: string; title: string; artist: string; progress: number; isPlaying: boolean;
  onToggle: () => void; onNext: () => void; onPrevious: () => void; onExit: () => void;
};

export function EnergySaverScreen(props: Props) {
  const { cover, title, artist, progress, isPlaying, onToggle, onNext, onPrevious, onExit } = props;
  const [screenOff, setScreenOff] = useState(false);
  const originalBrightness = useRef<number | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const armScreenOff = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    setScreenOff(false);
    void Brightness.setBrightnessAsync(SAVER_BRIGHTNESS);
    timer.current = setTimeout(() => {
      setScreenOff(true);
      void Brightness.setBrightnessAsync(SCREEN_OFF_BRIGHTNESS);
    }, SCREEN_OFF_DELAY_MS);
  }, []);

  useEffect(() => {
    let mounted = true;
    Brightness.getBrightnessAsync().then((value) => {
      if (!mounted) return;
      originalBrightness.current = value;
      armScreenOff();
    });
    const back = BackHandler.addEventListener("hardwareBackPress", () => { onExit(); return true; });
    const appState = AppState.addEventListener("change", (state) => {
      if (state === "active") armScreenOff();
      else if (originalBrightness.current != null) void Brightness.setBrightnessAsync(originalBrightness.current);
    });
    return () => {
      mounted = false;
      if (timer.current) clearTimeout(timer.current);
      back.remove(); appState.remove();
      if (originalBrightness.current != null) void Brightness.setBrightnessAsync(originalBrightness.current);
    };
  }, [armScreenOff, onExit]);

  const action = (callback: () => void) => { armScreenOff(); callback(); };
  if (screenOff) return <Pressable accessibilityLabel="Encender pantalla" style={styles.blackout} onPress={armScreenOff} />;

  return (
    <Pressable style={styles.screen} onPress={armScreenOff}>
      <Artwork uri={cover} style={styles.cover} iconSize={48} />
      <Text style={styles.title} numberOfLines={2}>{title}</Text>
      <Text style={styles.artist} numberOfLines={1}>{artist}</Text>
      <View style={styles.progress}><View style={[styles.progressFill, { width: `${progress}%` }]} /></View>
      <View style={styles.controls}>
        <TouchableOpacity onPress={() => action(onPrevious)}><Ionicons name="play-skip-back" size={42} color="#FFFFFF" /></TouchableOpacity>
        <TouchableOpacity onPress={() => action(onToggle)}><Ionicons name={isPlaying ? "pause" : "play"} size={58} color="#FFFFFF" /></TouchableOpacity>
        <TouchableOpacity onPress={() => action(onNext)}><Ionicons name="play-skip-forward" size={42} color="#FFFFFF" /></TouchableOpacity>
      </View>
      <TouchableOpacity style={styles.exit} onPress={onExit}><Ionicons name="chevron-forward" size={28} color="#F5C518" /><Text style={styles.exitText}>Salir del modo ahorro</Text><Ionicons name="battery-charging" size={25} color="#27D6A1" /></TouchableOpacity>
      <Text style={styles.hint}>La pantalla se apagará tras 15 segundos</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#050505", paddingHorizontal: 34, paddingTop: 24, alignItems: "stretch" }, blackout: { flex: 1, backgroundColor: "#000000" },
  cover: { width: "100%", aspectRatio: 1, maxHeight: 470, borderRadius: 22, backgroundColor: "#111111", alignSelf: "center" },
  title: { color: "#F7F7F7", fontSize: 27, lineHeight: 35, fontWeight: "900", marginTop: 26 }, artist: { color: "#8F8F94", fontSize: 18, marginTop: 6 },
  progress: { height: 7, borderRadius: 4, backgroundColor: "#18181A", marginTop: "auto", overflow: "hidden" }, progressFill: { height: "100%", borderRadius: 4, backgroundColor: "#BDBDC1" },
  controls: { flexDirection: "row", alignItems: "center", justifyContent: "space-around", paddingVertical: 30 },
  exit: { height: 72, borderWidth: 2, borderColor: "#5B5B60", borderRadius: 36, flexDirection: "row", alignItems: "center", justifyContent: "space-around", paddingHorizontal: 22 },
  exitText: { color: "#FFFFFF", fontSize: 18, fontWeight: "900" }, hint: { color: "#55555A", fontSize: 12, textAlign: "center", marginVertical: 12 },
});
