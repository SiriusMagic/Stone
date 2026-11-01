import { useState } from "react";
import { ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

const FAQ = [
  ["¿Por qué no aparece una canción?", "Abre Carpeta de Stone, confirma la carpeta y usa Escanear biblioteca."],
  ["¿Cómo agrego letras?", "Abre Letras en el reproductor, pulsa + y pega texto o importa un archivo LRC."],
  ["¿Por qué no se reproduce un archivo?", "Comprueba que Android conserve acceso al archivo y que el formato sea compatible."],
  ["¿Cómo funciona el volumen 300%?", "Hasta 100% usa el volumen multimedia; de 100% a 300% usa el refuerzo interno."],
];
export function FeedbackFaqScreen({ onBack }: { onBack: () => void }) {
  const [query, setQuery] = useState(""); const [open, setOpen] = useState(-1);
  const visible = FAQ.filter(([question]) => question.toLowerCase().includes(query.toLowerCase()));
  return (
    <View style={styles.screen}><View style={styles.header}><TouchableOpacity onPress={onBack}><Ionicons name="chevron-back" size={30} color="#FFFFFF" /></TouchableOpacity><Text style={styles.title}>Preguntas frecuentes</Text></View>
      <View style={styles.search}><Ionicons name="search" size={22} color="#77727A" /><TextInput value={query} onChangeText={setQuery} placeholder="Buscar" placeholderTextColor="#77727A" style={styles.input} /></View>
      <ScrollView contentContainerStyle={styles.list}>{visible.map(([question, answer], index) => <TouchableOpacity key={question} style={styles.item} onPress={() => setOpen(open === index ? -1 : index)}><View style={styles.questionRow}><Text style={styles.question}>{question}</Text><Ionicons name={open === index ? "chevron-up" : "chevron-down"} size={21} color="#8D8790" /></View>{open === index ? <Text style={styles.answer}>{answer}</Text> : null}</TouchableOpacity>)}</ScrollView>
    </View>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#09090B", paddingHorizontal: 26 }, header: { height: 74, flexDirection: "row", alignItems: "center", gap: 18 }, title: { color: "#FFFFFF", fontSize: 22, fontWeight: "900" },
  search: { height: 50, borderRadius: 25, backgroundColor: "#1C1B1F", flexDirection: "row", alignItems: "center", paddingHorizontal: 18, gap: 10, marginTop: 16 }, input: { flex: 1, color: "#FFFFFF", fontSize: 16 }, list: { paddingTop: 22, paddingBottom: 40, gap: 12 },
  item: { backgroundColor: "#1C1B1F", borderRadius: 20, padding: 18 }, questionRow: { flexDirection: "row", alignItems: "center", gap: 12 }, question: { flex: 1, color: "#F3F0F4", fontSize: 16, fontWeight: "800" }, answer: { color: "#AAA5AD", fontSize: 15, lineHeight: 22, marginTop: 14 },
});
