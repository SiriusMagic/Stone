import { memo, RefObject } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Artwork } from "@/src/components/Artwork";

type Props = {
  visible: boolean;
  title: string;
  artist: string;
  cover?: string;
  progress: number;
  isPlaying: boolean;
  lyrics: string[];
  activeIndex: number;
  scrollRef: RefObject<ScrollView | null>;
  onClose: () => void;
  onToggle: () => void;
  onAdd: () => void;
  onSearch: () => void;
};

export const LyricsSheet = memo(function LyricsSheet(props: Props) {
  const { visible, title, artist, cover, progress, isPlaying, lyrics, activeIndex, scrollRef, onClose, onToggle, onAdd, onSearch } = props;
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <Pressable style={styles.dismissArea} onPress={onClose} />
        <View style={styles.sheet}>
          <View style={styles.handle} />
          <View style={styles.trackRow}>
            <Artwork uri={cover} style={styles.cover} iconSize={18} />
            <View style={styles.trackInfo}>
              <Text style={styles.trackTitle} numberOfLines={1}>{title}</Text>
              <Text style={styles.artist} numberOfLines={1}>{artist}</Text>
              <View style={styles.progress}><View style={[styles.progressFill, { width: `${progress}%` }]} /></View>
            </View>
            <TouchableOpacity onPress={onToggle} style={styles.playButton}>
              <Ionicons name={isPlaying ? "pause" : "play"} size={25} color="#17141B" />
            </TouchableOpacity>
          </View>
          <View style={styles.headingRow}>
            <Text style={styles.heading}>Letras</Text>
            <TouchableOpacity onPress={onClose} style={styles.closeButton}>
              <Ionicons name="chevron-down" size={24} color="#E8E6EE" />
            </TouchableOpacity>
          </View>
          {lyrics.length ? (
            <ScrollView ref={scrollRef} style={styles.scroll} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
              {lyrics.map((line, index) => (
                <Text key={`${index}-${line}`} style={[styles.line, index === activeIndex && styles.activeLine]}>{line}</Text>
              ))}
            </ScrollView>
          ) : (
            <View style={styles.empty}>
              <Ionicons name="chatbox-ellipses-outline" size={64} color="#6D626F" />
              <Text style={styles.emptyTitle}>Sin letras</Text>
              <View style={styles.actions}>
                <TouchableOpacity accessibilityLabel="Agregar letras" style={styles.roundSecondary} onPress={onAdd}><Ionicons name="add" size={32} color="#E8E6EE" /></TouchableOpacity>
                <TouchableOpacity accessibilityLabel="Buscar letras" style={styles.roundPrimary} onPress={onSearch}><Ionicons name="search" size={27} color="#17141B" /></TouchableOpacity>
              </View>
            </View>
          )}
        </View>
      </View>
    </Modal>
  );
});

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.58)" },
  dismissArea: { height: "12%" },
  sheet: { flex: 1, backgroundColor: "#17151B", borderTopLeftRadius: 30, borderTopRightRadius: 30, paddingHorizontal: 24, paddingBottom: 28 },
  handle: { width: 52, height: 5, borderRadius: 3, backgroundColor: "#3A353D", alignSelf: "center", marginVertical: 12 },
  trackRow: { flexDirection: "row", alignItems: "center", gap: 13, paddingBottom: 20 },
  cover: { width: 64, height: 64, borderRadius: 14, backgroundColor: "#312D34" },
  trackInfo: { flex: 1 },
  trackTitle: { color: "#F7F4F8", fontSize: 17, fontWeight: "800" },
  artist: { color: "#A8A1AB", fontSize: 14, marginTop: 3, marginBottom: 9 },
  progress: { height: 5, borderRadius: 3, backgroundColor: "#302C33", overflow: "hidden" },
  progressFill: { height: "100%", borderRadius: 3, backgroundColor: "#F5C518" },
  playButton: { width: 46, height: 46, borderRadius: 23, backgroundColor: "#F5C518", alignItems: "center", justifyContent: "center" },
  headingRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 18 },
  heading: { color: "#FFFFFF", fontSize: 28, fontWeight: "900" },
  closeButton: { width: 42, height: 42, alignItems: "center", justifyContent: "center" },
  scroll: { flex: 1 },
  content: { paddingTop: 4, paddingBottom: 220, gap: 24 },
  line: { color: "#77717B", fontSize: 22, lineHeight: 30, fontWeight: "700" },
  activeLine: { color: "#F5C518", fontSize: 24, lineHeight: 32 },
  empty: { flex: 1, alignItems: "center", justifyContent: "center", gap: 18 },
  emptyTitle: { color: "#C6C0C9", fontSize: 18, fontWeight: "700", textAlign: "center" },
  actions: { flexDirection: "row", gap: 12 },
  roundSecondary: { width: 62, height: 62, borderWidth: 1, borderColor: "#625B66", borderRadius: 31, alignItems: "center", justifyContent: "center" },
  roundPrimary: { width: 62, height: 62, backgroundColor: "#F5C518", borderRadius: 31, alignItems: "center", justifyContent: "center" },
});
