import { useEffect, useState } from "react";
import { Modal, Pressable, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

type Props = { visible: boolean; initialMinutes: number; onClose: () => void; onSet: (minutes: number) => void };

export function CustomSleepTimerSheet({ visible, initialMinutes, onClose, onSet }: Props) {
  const [hours, setHours] = useState(0);
  const [minutes, setMinutes] = useState(0);
  useEffect(() => {
    if (!visible) return;
    setHours(Math.floor(initialMinutes / 60));
    setMinutes(initialMinutes % 60);
  }, [initialMinutes, visible]);
  const total = hours * 60 + minutes;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
        <View style={styles.sheet}>
          <View style={styles.handle} />
          <Text style={styles.title}>Tiempo personalizado</Text>
          <View style={styles.pickers}>
            <NumberWheel value={hours} max={23} label="Hora" onChange={setHours} />
            <NumberWheel value={minutes} max={59} label="Min" onChange={setMinutes} />
          </View>
          <TouchableOpacity
            disabled={total <= 0}
            style={[styles.setButton, total <= 0 && styles.setButtonDisabled]}
            onPress={() => onSet(total)}
          >
            <Text style={styles.setText}>Establecer temporizador</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

function NumberWheel({ value, max, label, onChange }: { value: number; max: number; label: string; onChange: (value: number) => void }) {
  const change = (delta: number) => onChange((value + delta + max + 1) % (max + 1));
  return (
    <View style={styles.wheel}>
      <TouchableOpacity onPress={() => change(-1)}><Ionicons name="chevron-up" size={26} color="#8B6F16" /></TouchableOpacity>
      <Text style={styles.neighbor}>{(value - 1 + max + 1) % (max + 1)}</Text>
      <View style={styles.selectedRow}>
        <Text style={styles.value}>{value}</Text>
        <Text style={styles.label}>{label}</Text>
      </View>
      <Text style={styles.neighbor}>{(value + 1) % (max + 1)}</Text>
      <TouchableOpacity onPress={() => change(1)}><Ionicons name="chevron-down" size={26} color="#8B6F16" /></TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.58)", justifyContent: "flex-end" },
  sheet: { minHeight: "68%", borderTopLeftRadius: 30, borderTopRightRadius: 30, backgroundColor: "#17151C", padding: 24 },
  handle: { width: 58, height: 5, borderRadius: 3, backgroundColor: "#3A343F", alignSelf: "center", marginBottom: 28 },
  title: { color: "#FFFFFF", fontSize: 24, fontWeight: "800" },
  pickers: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 44 },
  wheel: { width: 130, alignItems: "center", gap: 8 },
  neighbor: { color: "#665720", fontSize: 28, fontWeight: "700" },
  selectedRow: { width: "100%", minHeight: 58, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10, borderTopWidth: 1, borderBottomWidth: 1, borderColor: "#3A343F" },
  value: { color: "#F5C518", fontSize: 42, fontWeight: "800" },
  label: { color: "#E8E6EE", fontSize: 20, fontWeight: "700" },
  setButton: { height: 58, borderRadius: 29, alignItems: "center", justifyContent: "center", backgroundColor: "#F5C518" },
  setButtonDisabled: { backgroundColor: "#2A2730" },
  setText: { color: "#17151C", fontSize: 17, fontWeight: "800" },
});
