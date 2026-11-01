import { useState } from "react";
import { ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import * as DocumentPicker from "expo-document-picker";
import * as FileSystem from "expo-file-system/legacy";

const CATEGORIES = ["Letras", "Archivo no encontrado", "No se puede reproducir", "Se detiene la música", "Aplicación lenta", "Otros"];
type Props = { onBack: () => void; onSubmit: (category: string, details: string, attachments: string[]) => Promise<void>; onFaq: () => void };
export function FeedbackFormScreen({ onBack, onSubmit, onFaq }: Props) {
  const [category, setCategory] = useState(""); const [details, setDetails] = useState(""); const [attachments, setAttachments] = useState<string[]>([]); const [saving, setSaving] = useState(false);
  const pick = async () => { const result = await DocumentPicker.getDocumentAsync({ type: ["image/*", "video/*"], multiple: true, copyToCacheDirectory: true }); const root = FileSystem.documentDirectory ?? FileSystem.cacheDirectory; if (!result.canceled && root) { const folder = `${root}stone-feedback/`; await FileSystem.makeDirectoryAsync(folder, { intermediates: true }); const saved = await Promise.all(result.assets.slice(0, 4).map(async (asset, index) => { const target = `${folder}${Date.now()}-${index}-${asset.name.replace(/[^\w.-]/g, "_")}`; await FileSystem.copyAsync({ from: asset.uri, to: target }); return target; })); setAttachments(saved); } };
  const submit = async () => { if (!category || !details.trim()) return; setSaving(true); await onSubmit(category, details.trim(), attachments); setSaving(false); };
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <View style={styles.header}><TouchableOpacity onPress={onBack}><Ionicons name="chevron-back" size={30} color="#FFFFFF" /></TouchableOpacity><Text style={styles.title}>Cuéntanos el problema</Text><TouchableOpacity onPress={onFaq}><Ionicons name="help-circle-outline" size={30} color="#FFFFFF" /></TouchableOpacity></View>
      <View style={styles.chips}>{CATEGORIES.map((item) => <TouchableOpacity key={item} style={[styles.chip, category === item && styles.chipActive]} onPress={() => setCategory(item)}><Text style={[styles.chipText, category === item && styles.chipTextActive]}>{item}</Text></TouchableOpacity>)}</View>
      <Text style={styles.label}>Detalles <Text style={styles.required}>*</Text></Text><View style={styles.inputWrap}><TextInput value={details} onChangeText={(value) => setDetails(value.slice(0, 500))} multiline textAlignVertical="top" placeholder="Explique lo que ha pasado, o sugerencias." placeholderTextColor="#6E6971" style={styles.input} /><Text style={styles.count}>{details.length}/500</Text></View>
      <Text style={styles.label}>Capturas de pantalla o videos (Opcional)</Text><View style={styles.attachRow}><TouchableOpacity style={styles.add} onPress={pick}><Ionicons name="add" size={38} color="#88838C" /></TouchableOpacity>{attachments.map((uri, index) => <View key={uri} style={styles.attachment}><Ionicons name="document-attach-outline" size={25} color="#F5C518" /><Text style={styles.attachmentText}>{index + 1}</Text></View>)}</View>
      <TouchableOpacity disabled={!category || !details.trim() || saving} style={[styles.submit, (!category || !details.trim() || saving) && styles.disabled]} onPress={submit}><Text style={styles.submitText}>{saving ? "Guardando..." : "Entregar"}</Text></TouchableOpacity>
    </ScrollView>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#09090B" }, content: { paddingHorizontal: 26, paddingBottom: 30 }, header: { height: 74, flexDirection: "row", alignItems: "center", justifyContent: "space-between" }, title: { color: "#FFFFFF", fontSize: 22, fontWeight: "900" },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: 10, marginTop: 28 }, chip: { paddingHorizontal: 18, paddingVertical: 12, borderRadius: 24, backgroundColor: "#1D1C20" }, chipActive: { backgroundColor: "#F5C518" }, chipText: { color: "#AAA5AD", fontWeight: "700" }, chipTextActive: { color: "#17141B" },
  label: { color: "#F4F1F5", fontSize: 19, fontWeight: "900", marginTop: 34, marginBottom: 14 }, required: { color: "#F5C518" }, inputWrap: { height: 200, borderRadius: 24, backgroundColor: "#19191C", padding: 18 }, input: { flex: 1, color: "#FFFFFF", fontSize: 17, lineHeight: 24 }, count: { color: "#79747C", textAlign: "right" },
  attachRow: { flexDirection: "row", gap: 10 }, add: { width: 74, height: 74, borderRadius: 20, backgroundColor: "#222226", alignItems: "center", justifyContent: "center" }, attachment: { width: 62, height: 74, borderRadius: 16, backgroundColor: "#222226", alignItems: "center", justifyContent: "center" }, attachmentText: { color: "#FFFFFF", marginTop: 3 },
  submit: { height: 60, borderRadius: 30, backgroundColor: "#F5C518", alignItems: "center", justifyContent: "center", marginTop: 46 }, disabled: { opacity: 0.3 }, submitText: { color: "#17141B", fontSize: 18, fontWeight: "900" },
});
