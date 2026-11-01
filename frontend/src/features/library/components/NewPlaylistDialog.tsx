import { Modal, Pressable, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";

type Props = {
  visible: boolean;
  value: string;
  onChange: (value: string) => void;
  onClose: () => void;
  onCreate: () => void;
};

export function NewPlaylistDialog({ visible, value, onChange, onClose, onCreate }: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.card}>
          <Text style={styles.title}>Nueva playlist</Text>
          <TextInput
            value={value}
            onChangeText={onChange}
            placeholder="Nombre de playlist"
            placeholderTextColor="#8D8798"
            style={styles.input}
            maxLength={200}
            autoFocus
          />
          <Text style={styles.counter}>{`${value.length}/200`}</Text>
          <View style={styles.actions}>
            <TouchableOpacity onPress={onClose} style={styles.actionBtn}>
              <Text style={styles.cancelText}>Cancelar</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={onCreate} style={styles.actionBtn} disabled={!value.trim()}>
              <Text style={[styles.createText, !value.trim() && styles.createTextDisabled]}>Crear</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.55)", justifyContent: "center", padding: 24 },
  card: { backgroundColor: "#ECE7F7", borderRadius: 28, padding: 24 },
  title: { color: "#16131D", fontSize: 22, fontWeight: "800", marginBottom: 26 },
  input: { color: "#1F1A28", fontSize: 18, borderBottomWidth: 2, borderBottomColor: "#746E84", paddingBottom: 10 },
  counter: { color: "#8D8798", fontSize: 14, alignSelf: "flex-end", marginTop: 10 },
  actions: { flexDirection: "row", justifyContent: "flex-end", gap: 24, marginTop: 24 },
  actionBtn: { minWidth: 88, alignItems: "center" },
  cancelText: { color: "#4A4556", fontSize: 18, fontWeight: "700" },
  createText: { color: "#4A4556", fontSize: 18, fontWeight: "800" },
  createTextDisabled: { opacity: 0.4 },
});
