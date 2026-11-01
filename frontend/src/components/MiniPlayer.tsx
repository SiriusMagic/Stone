import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";

import { Artwork } from "@/src/components/Artwork";
import { useAudio, useAudioProgress } from "@/src/contexts/AudioContext";
import { useLanguage } from "@/src/contexts/LanguageContext";

export default function MiniPlayer() {
  const router = useRouter();
  const { current, toggle, favoriteTrackIds, toggleFavorite } = useAudio();
  const { isPlaying } = useAudioProgress();
  const { t } = useLanguage();

  if (!current) return null;
  const isFavorite = favoriteTrackIds.includes(current.id);

  return (
    <View style={styles.host}>
      <TouchableOpacity
        activeOpacity={0.92}
        style={styles.wrap}
        onPress={() => router.push("/player")}
        testID="mini-player"
      >
        <Artwork uri={current.cover} style={styles.cover} iconSize={20} />
        <View style={styles.info}>
          <Text style={styles.title} numberOfLines={1}>{current.title}</Text>
          <Text style={styles.subtitle} numberOfLines={1}>
            {isPlaying ? t("now_playing") : t("tap_to_play")}
          </Text>
        </View>
        <TouchableOpacity
          style={styles.iconButton}
          onPress={(event) => {
            event.stopPropagation();
            toggleFavorite(current.id);
          }}
          testID="mini-player-favorite-button"
        >
          <Ionicons
            name={isFavorite ? "heart" : "heart-outline"}
            size={22}
            color={isFavorite ? "#F5C518" : "#F3F0F6"}
          />
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.iconButton}
          onPress={(event) => {
            event.stopPropagation();
            router.push({ pathname: "/player", params: { open: "options" } });
          }}
          testID="mini-player-options-button"
        >
          <Ionicons name="ellipsis-horizontal" size={22} color="#F3F0F6" />
        </TouchableOpacity>
        <TouchableOpacity
          style={styles.playButton}
          onPress={(event) => {
            event.stopPropagation();
            toggle();
          }}
          testID="mini-player-play-button"
        >
          <Ionicons name={isPlaying ? "pause" : "play"} size={24} color="#F5C518" />
        </TouchableOpacity>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  host: { paddingHorizontal: 16, paddingBottom: 10 },
  wrap: {
    alignItems: "center",
    backgroundColor: "rgba(68,49,0,0.95)",
    borderColor: "rgba(245,197,24,0.42)",
    borderRadius: 24,
    borderWidth: 1,
    elevation: 10,
    flexDirection: "row",
    minHeight: 76,
    padding: 10,
    shadowColor: "#F5C518",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.24,
    shadowRadius: 14,
  },
  cover: { backgroundColor: "#1A1823", borderRadius: 14, height: 56, width: 56 },
  info: { flex: 1, marginLeft: 12, minWidth: 0 },
  title: { color: "#FFFFFF", fontSize: 16, fontWeight: "800" },
  subtitle: { color: "#E8C35D", fontSize: 12, marginTop: 4 },
  iconButton: { alignItems: "center", height: 42, justifyContent: "center", width: 38 },
  playButton: {
    alignItems: "center",
    borderColor: "#F5C518",
    borderRadius: 23,
    borderWidth: 2,
    height: 46,
    justifyContent: "center",
    marginLeft: 2,
    width: 46,
  },
});
