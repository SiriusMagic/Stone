import { useEffect, useMemo, useState } from "react";
import { Alert, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import Slider from "@react-native-community/slider";
import { formatMs } from "@/src/data/tracks";

const CLIP_MS = 30000;
type Props = {
  title: string; durationMs: number; positionMs: number; isPlaying: boolean;
  onBack: () => void; onToggle: () => void; onSeek: (ms: number) => void; onConfirm: (startMs: number) => Promise<void>;
};

export function RingtoneEditor({ title, durationMs, positionMs, isPlaying, onBack, onToggle, onSeek, onConfirm }: Props) {
  const maxStart = Math.max(0, durationMs - CLIP_MS);
  const [startMs, setStartMs] = useState(Math.min(positionMs, maxStart));
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const bars = useMemo(() => Array.from({ length: 54 }, (_, index) => 18 + ((index * 31 + title.length * 7) % 72)), [title]);

  useEffect(() => {
    if (previewing && positionMs >= Math.min(durationMs, startMs + CLIP_MS)) {
      if (isPlaying) onToggle();
      setPreviewing(false);
    }
  }, [durationMs, isPlaying, onToggle, positionMs, previewing, startMs]);

  const preview = () => {
    if (!previewing) { onSeek(startMs); if (!isPlaying) onToggle(); setPreviewing(true); }
    else { if (isPlaying) onToggle(); setPreviewing(false); }
  };
  const save = async () => {
    setSaving(true);
    try { await onConfirm(startMs); Alert.alert("Tono establecido", "Stone cambió el tono de llamada correctamente."); onBack(); }
    catch (error) { Alert.alert("No se pudo cambiar", error instanceof Error ? error.message : "Comprueba el permiso de Android."); }
    finally { setSaving(false); }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}><TouchableOpacity onPress={onBack}><Ionicons name="chevron-back" size={30} color="#FFFFFF" /></TouchableOpacity><Text style={styles.heading}>Crear tono de llamada</Text></View>
      <View style={styles.timeline}><Text style={styles.time}>{formatMs(startMs)}</Text><Text style={styles.time}>{formatMs(Math.min(durationMs, startMs + CLIP_MS))}</Text></View>
      <View style={styles.waveBox}>
        <View style={styles.wave}>{bars.map((height, index) => <View key={index} style={[styles.bar, { height: `${height}%` }]} />)}</View>
        <View pointerEvents="none" style={styles.selection} />
      </View>
      <Slider style={styles.slider} minimumValue={0} maximumValue={maxStart || 1} value={startMs} onValueChange={setStartMs} minimumTrackTintColor="#2F9CFF" maximumTrackTintColor="#242428" thumbTintColor="#2F9CFF" />
      <Text style={styles.help}>Desliza para elegir un fragmento de 30 segundos</Text>
      <TouchableOpacity style={styles.preview} onPress={preview}><Ionicons name={previewing && isPlaying ? "pause" : "play"} size={48} color="#FFFFFF" /></TouchableOpacity>
      <TouchableOpacity disabled={saving} style={[styles.save, saving && styles.disabled]} onPress={save}><Text style={styles.saveText}>{saving ? "Estableciendo..." : "Establecer como tono de llamada"}</Text></TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: "#070708", paddingHorizontal: 26 }, header: { height: 72, flexDirection: "row", alignItems: "center", gap: 18 }, heading: { color: "#FFFFFF", fontSize: 22, fontWeight: "900" },
  timeline: { flexDirection: "row", justifyContent: "space-between", marginTop: 62, paddingHorizontal: 20 }, time: { color: "#D7D7DB", fontSize: 16, fontWeight: "700" },
  waveBox: { height: 220, borderRadius: 28, backgroundColor: "#171719", marginTop: 14, overflow: "hidden", justifyContent: "center" }, wave: { height: "72%", flexDirection: "row", alignItems: "center", paddingHorizontal: 10 },
  bar: { flex: 1, minHeight: 4, marginHorizontal: 1, backgroundColor: "#2F9CFF", borderRadius: 2 }, selection: { ...StyleSheet.absoluteFillObject, borderLeftWidth: 4, borderRightWidth: 4, borderColor: "#F4F4F5", backgroundColor: "rgba(47,156,255,0.08)" },
  slider: { width: "100%", height: 46, marginTop: 10 }, help: { color: "#77777D", textAlign: "center", fontSize: 13 }, preview: { width: 104, height: 104, borderRadius: 52, backgroundColor: "#242428", alignSelf: "center", alignItems: "center", justifyContent: "center", marginTop: 42 },
  save: { height: 62, borderRadius: 31, backgroundColor: "#2F9CFF", alignItems: "center", justifyContent: "center", marginTop: "auto", marginBottom: 14 }, saveText: { color: "#FFFFFF", fontSize: 18, fontWeight: "900" }, disabled: { opacity: 0.5 },
});
