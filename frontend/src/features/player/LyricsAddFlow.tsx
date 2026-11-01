import { useState } from "react";
import { Modal, Pressable, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import * as Clipboard from "expo-clipboard";
import * as DocumentPicker from "expo-document-picker";
import * as FileSystem from "expo-file-system/legacy";

type Props = { visible: boolean; onClose: () => void; onSave: (value: string) => void };

export function LyricsAddFlow({ visible, onClose, onSave }: Props) {
  const [draft, setDraft] = useState("");
  const [editing, setEditing] = useState(false);
  const openEditor = (value: string) => { setDraft(value); setEditing(true); };
  const pasteLyrics = async () => openEditor(await Clipboard.getStringAsync());
  const importLyrics = async () => {
    const result = await DocumentPicker.getDocumentAsync({ type: ["text/plain", "application/octet-stream"], copyToCacheDirectory: true });
    if (!result.canceled && result.assets[0]) openEditor(await FileSystem.readAsStringAsync(result.assets[0].uri));
  };
  const closeAll = () => { setEditing(false); setDraft(""); onClose(); };
  const save = () => { if (!draft.trim()) return; onSave(draft); closeAll(); };

  if (editing) return (
    <Modal visible animationType="slide" onRequestClose={() => setEditing(false)}>
      <SafeAreaView style={styles.editor}>
        <View style={styles.editorHeader}>
          <TouchableOpacity onPress={() => setEditing(false)}><Ionicons name="chevron-back" size={30} color="#F4F1F5" /></TouchableOpacity>
          <Text style={styles.title}>Agregar letras</Text>
          <TouchableOpacity style={[styles.save, !draft.trim() && styles.disabled]} disabled={!draft.trim()} onPress={save}><Text style={styles.saveText}>Guardar</Text></TouchableOpacity>
        </View>
        <TextInput value={draft} onChangeText={setDraft} multiline autoFocus textAlignVertical="top" placeholder="Pega letras o contenido LRC..." placeholderTextColor="#6F6973" style={styles.input} />
      </SafeAreaView>
    </Modal>
  );

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={closeAll}>
      <View style={styles.backdrop}><Pressable style={styles.dismiss} onPress={closeAll} />
        <View style={styles.menu}>
          <View style={styles.handle} /><Text style={styles.menuTitle}>Agregar</Text>
          <TouchableOpacity style={styles.row} onPress={pasteLyrics}><Ionicons name="copy-outline" size={28} color="#AAA4AD" /><Text style={styles.rowText}>Pegar letras</Text></TouchableOpacity>
          <TouchableOpacity style={styles.row} onPress={importLyrics}><Ionicons name="document-text-outline" size={28} color="#AAA4AD" /><Text style={styles.rowText}>Importar archivo de letras</Text><Ionicons name="chevron-forward" size={22} color="#716B74" /></TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.55)" }, dismiss: { flex: 1 },
  menu: { backgroundColor: "#17151A", borderTopLeftRadius: 30, borderTopRightRadius: 30, padding: 28, paddingBottom: 42 },
  handle: { alignSelf: "center", width: 52, height: 5, borderRadius: 3, backgroundColor: "#332F35", marginBottom: 28 },
  menuTitle: { color: "#FFFFFF", fontSize: 29, fontWeight: "900", marginBottom: 22 },
  row: { minHeight: 76, flexDirection: "row", alignItems: "center", gap: 22 }, rowText: { flex: 1, color: "#F0EDF1", fontSize: 18, fontWeight: "700" },
  editor: { flex: 1, backgroundColor: "#0D0C0F" }, editorHeader: { height: 72, paddingHorizontal: 20, flexDirection: "row", alignItems: "center", gap: 16 },
  title: { flex: 1, color: "#F4F1F5", fontSize: 22, fontWeight: "800" }, save: { backgroundColor: "#F5C518", borderRadius: 22, paddingHorizontal: 20, paddingVertical: 11 },
  disabled: { opacity: 0.35 }, saveText: { color: "#17141B", fontWeight: "900" }, input: { flex: 1, color: "#E8E4EA", fontSize: 18, lineHeight: 27, paddingHorizontal: 28, paddingTop: 24 },
});
