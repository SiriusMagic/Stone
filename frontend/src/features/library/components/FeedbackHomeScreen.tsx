import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { FeedbackEntry } from "../feedback-store";

type Props = { items: FeedbackEntry[]; onClose: () => void; onCreate: () => void; onFaq: () => void };
export function FeedbackHomeScreen({ items, onClose, onCreate, onFaq }: Props) {
  return (
    <View style={styles.screen}>
      <View style={styles.header}><TouchableOpacity onPress={onClose}><Ionicons name="chevron-back" size={30} color="#F4F1F5" /></TouchableOpacity><Text style={styles.title}>Mis comentarios</Text><TouchableOpacity onPress={onFaq}><Ionicons name="help-circle-outline" size={30} color="#F4F1F5" /></TouchableOpacity></View>
      {items.length ? <View style={styles.list}>{items.map((item) => <View key={item.id} style={styles.card}><Text style={styles.date}>Creado el {new Date(item.createdAt).toLocaleString()}</Text><Text style={styles.category}>{item.category}</Text><Text style={styles.details} numberOfLines={2}>{item.details}</Text>{item.attachments.length ? <Text style={styles.files}>{item.attachments.length} archivo(s) adjunto(s)</Text> : null}</View>)}</View> : <View style={styles.empty}><Ionicons name="chatbubbles-outline" size={104} color="#242327" /><Text style={styles.emptyText}>Si tienes algún problema o sugerencia, envíanos tus comentarios.</Text><TouchableOpacity style={styles.create} onPress={onCreate}><Text style={styles.createText}>Enviar comentarios</Text></TouchableOpacity></View>}
      {items.length ? <TouchableOpacity style={styles.fab} onPress={onCreate}><Ionicons name="add" size={30} color="#111114" /></TouchableOpacity> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#09090B", paddingHorizontal: 26 }, header: { height: 74, flexDirection: "row", alignItems: "center", justifyContent: "space-between" }, title: { color: "#F7F5F8", fontSize: 23, fontWeight: "900" },
  empty: { flex: 1, alignItems: "center", justifyContent: "center", paddingBottom: 100 }, emptyText: { color: "#77737B", fontSize: 17, lineHeight: 24, textAlign: "center", maxWidth: 330, marginTop: 28 },
  create: { backgroundColor: "#242329", borderRadius: 24, paddingHorizontal: 28, paddingVertical: 13, marginTop: 26 }, createText: { color: "#F5C518", fontSize: 16, fontWeight: "800" },
  list: { gap: 14, paddingTop: 24 }, card: { backgroundColor: "#242327", borderRadius: 24, padding: 22 }, date: { color: "#77737B", fontSize: 13 }, category: { color: "#FFFFFF", fontSize: 18, fontWeight: "900", marginTop: 12 }, details: { color: "#E3DFE5", fontSize: 16, lineHeight: 22, marginTop: 4 }, files: { color: "#F5C518", fontSize: 12, marginTop: 12 },
  fab: { position: "absolute", right: 28, bottom: 34, width: 60, height: 60, borderRadius: 30, backgroundColor: "#F5C518", alignItems: "center", justifyContent: "center" },
});
