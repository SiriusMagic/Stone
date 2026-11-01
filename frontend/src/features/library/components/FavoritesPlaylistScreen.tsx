import { useMemo, useState } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View, Image } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { Track } from "@/src/data/tracks";

type Props = {
  visible: boolean;
  title: string;
  tracks: Track[];
  headerMode?: "menu" | "check" | "sort-check";
  onClose: () => void;
  onPlay: (track: Track, queue: Track[]) => void;
  onShuffle: () => void;
};

export function FavoritesPlaylistScreen({
  visible,
  title,
  tracks,
  headerMode = "menu",
  onClose,
  onPlay,
  onShuffle,
}: Props) {
  const [menuVisible, setMenuVisible] = useState(false);
  const [reversedOrder, setReversedOrder] = useState(false);
  const cover = tracks[0]?.cover ?? "https://placehold.co/400x400/png";
  const visibleTracks = useMemo(
    () => (reversedOrder ? [...tracks].reverse() : tracks),
    [reversedOrder, tracks],
  );
  const countLabel = `${tracks.length} ${tracks.length === 1 ? "canción" : "Canciones"}`;

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.screen}>
        <View style={styles.header}>
          <TouchableOpacity style={styles.headerBtn} onPress={onClose}>
            <Ionicons name="chevron-back" size={28} color="#16131D" />
          </TouchableOpacity>
          <View style={styles.headerActions}>
            {headerMode !== "check" ? (
              <TouchableOpacity style={styles.headerActionPill} onPress={() => setReversedOrder((value) => !value)}>
                <Ionicons name="swap-vertical-outline" size={24} color="#16131D" />
              </TouchableOpacity>
            ) : null}
            <TouchableOpacity
              style={styles.headerActionPill}
              onPress={headerMode === "menu" ? () => setMenuVisible(true) : onClose}
            >
              <Ionicons name={headerMode === "menu" ? "ellipsis-vertical" : "checkmark-circle-outline"} size={22} color="#16131D" />
            </TouchableOpacity>
          </View>
        </View>

        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <Image source={{ uri: cover }} style={styles.cover} />
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.count}>{countLabel}</Text>

          <View style={styles.actionRow}>
            <TouchableOpacity
              style={[styles.actionButton, !tracks.length && styles.actionButtonDisabled]}
              onPress={() => tracks[0] && onPlay(tracks[0], tracks)}
              disabled={!tracks.length}
            >
              <Ionicons name="play" size={22} color="#4F4A61" />
              <Text style={styles.actionText}>Reproducir</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.actionButton, !tracks.length && styles.actionButtonDisabled]}
              onPress={onShuffle}
              disabled={!tracks.length}
            >
              <Ionicons name="shuffle" size={22} color="#4F4A61" />
              <Text style={styles.actionText}>Mezclar todo</Text>
            </TouchableOpacity>
          </View>

          {visibleTracks.map((track) => (
            <TouchableOpacity
              key={track.id}
              style={styles.trackRow}
              activeOpacity={0.85}
              onPress={() => onPlay(track, visibleTracks)}
            >
              <Image source={{ uri: track.cover }} style={styles.trackCover} />
              <View style={styles.trackText}>
                <Text style={styles.trackTitle} numberOfLines={1}>
                  {track.title}
                </Text>
                <Text style={styles.trackSubtitle} numberOfLines={1}>
                  {`${track.artist} - ${track.album}`}
                </Text>
              </View>
            </TouchableOpacity>
          ))}

          <Pressable style={styles.addRow}>
            <Ionicons name="add" size={30} color="#9C96AE" />
            <Text style={styles.addText}>Agregar canciones</Text>
          </Pressable>
        </ScrollView>

        <Modal visible={headerMode === "menu" && menuVisible} transparent animationType="fade" onRequestClose={() => setMenuVisible(false)}>
          <Pressable style={styles.sheetBackdrop} onPress={() => setMenuVisible(false)}>
            <Pressable style={styles.sheetCard}>
              <View style={styles.sheetHandle} />
              <TouchableOpacity style={styles.sheetRow} onPress={() => setMenuVisible(false)}>
                <Ionicons name="add-circle-outline" size={28} color="#6F677B" />
                <Text style={styles.sheetText}>Agregar canciones</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.sheetRow} onPress={() => setMenuVisible(false)}>
                <Ionicons name="checkmark-circle-outline" size={28} color="#6F677B" />
                <Text style={styles.sheetText}>Administrar</Text>
              </TouchableOpacity>
            </Pressable>
          </Pressable>
        </Modal>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#E8DFFF" },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingHorizontal: 24, paddingTop: 8 },
  headerBtn: { width: 44, height: 44, borderRadius: 22, alignItems: "center", justifyContent: "center", backgroundColor: "rgba(255,255,255,0.22)" },
  headerActions: { flexDirection: "row", gap: 10 },
  headerActionPill: { width: 54, height: 44, borderRadius: 22, alignItems: "center", justifyContent: "center", backgroundColor: "rgba(255,255,255,0.22)" },
  content: { paddingHorizontal: 24, paddingBottom: 40 },
  cover: { width: 288, height: 288, borderRadius: 28, alignSelf: "center", marginTop: 20 },
  title: { marginTop: 22, color: "#16131D", fontSize: 28, fontWeight: "900", textAlign: "center" },
  count: { marginTop: 10, color: "#9A92A8", fontSize: 16, textAlign: "center" },
  actionRow: { flexDirection: "row", gap: 12, marginTop: 24, marginBottom: 24 },
  actionButton: { flex: 1, minHeight: 56, borderRadius: 20, backgroundColor: "#D8CBFF", flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10 },
  actionButtonDisabled: { opacity: 0.45 },
  actionText: { color: "#4F4A61", fontSize: 17, fontWeight: "800" },
  trackRow: { flexDirection: "row", alignItems: "center", gap: 14, marginBottom: 18 },
  trackCover: { width: 52, height: 52, borderRadius: 14 },
  trackText: { flex: 1 },
  trackTitle: { color: "#16131D", fontSize: 16, fontWeight: "800" },
  trackSubtitle: { color: "#6F677B", fontSize: 14, marginTop: 4 },
  addRow: { flexDirection: "row", alignItems: "center", gap: 14, marginTop: 6, paddingVertical: 10 },
  addText: { color: "#9C96AE", fontSize: 18, fontWeight: "700" },
  sheetBackdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.18)", justifyContent: "flex-end" },
  sheetCard: { backgroundColor: "#F1EBFF", borderTopLeftRadius: 32, borderTopRightRadius: 32, paddingHorizontal: 28, paddingTop: 12, paddingBottom: 28 },
  sheetHandle: { alignSelf: "center", width: 56, height: 6, borderRadius: 999, backgroundColor: "#D9D1EC", marginBottom: 22 },
  sheetRow: { flexDirection: "row", alignItems: "center", gap: 18, paddingVertical: 18 },
  sheetText: { color: "#16131D", fontSize: 18, fontWeight: "700" },
});
