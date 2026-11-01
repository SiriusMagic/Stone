import { useEffect, useState } from "react";
import { Modal, SafeAreaView, StyleSheet } from "react-native";
import { addFeedback, FeedbackEntry, loadFeedback } from "../feedback-store";
import { FeedbackFaqScreen } from "./FeedbackFaqScreen";
import { FeedbackFormScreen } from "./FeedbackFormScreen";
import { FeedbackHomeScreen } from "./FeedbackHomeScreen";

type Mode = "home" | "form" | "faq";
export function FeedbackFlow({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const [mode, setMode] = useState<Mode>("home"); const [items, setItems] = useState<FeedbackEntry[]>([]);
  useEffect(() => { if (visible) { setMode("home"); void loadFeedback().then(setItems); } }, [visible]);
  const submit = async (category: string, details: string, attachments: string[]) => { setItems(await addFeedback({ category, details, attachments })); setMode("home"); };
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={mode === "home" ? onClose : () => setMode("home")}>
      <SafeAreaView style={styles.safe}>
        {mode === "home" ? <FeedbackHomeScreen items={items} onClose={onClose} onCreate={() => setMode("form")} onFaq={() => setMode("faq")} /> : null}
        {mode === "form" ? <FeedbackFormScreen onBack={() => setMode("home")} onFaq={() => setMode("faq")} onSubmit={submit} /> : null}
        {mode === "faq" ? <FeedbackFaqScreen onBack={() => setMode("home")} /> : null}
      </SafeAreaView>
    </Modal>
  );
}
const styles = StyleSheet.create({ safe: { flex: 1, backgroundColor: "#09090B" } });
