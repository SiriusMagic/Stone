import { Text, TouchableOpacity, ScrollView, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { TabKey } from "../types";

export function LibraryTabs({
  tab,
  onChange,
  t,
}: {
  tab: TabKey;
  onChange: (tab: TabKey) => void;
  t: (key: string) => string;
}) {
  return (
    <View style={styles.chipsRow}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.chipsContent}
      >
        {(
          [
            { key: "videos", icon: "play-circle-outline", label: t("videos"), showIcon: true },
            { key: "songs", icon: "musical-note-outline", label: t("songs_tab"), showIcon: false },
            { key: "playlists", icon: "musical-note", label: t("playlists"), showIcon: false },
            { key: "folders", icon: "folder-open", label: t("folders"), showIcon: false },
            { key: "artists", icon: "person", label: t("artists"), showIcon: false },
            { key: "albums", icon: "disc", label: t("albums"), showIcon: false },
          ] as { key: TabKey; icon: keyof typeof Ionicons.glyphMap; label: string; showIcon: boolean }[]
        ).map((item) => {
          const active = tab === item.key;

          return (
            <TouchableOpacity
              key={item.key}
              onPress={() => onChange(item.key)}
              activeOpacity={0.85}
              style={[styles.chip, active && styles.chipActive]}
              testID={`library-tab-${item.key}`}
            >
              {item.showIcon ? (
                <Ionicons
                  name={item.icon}
                  size={16}
                  color={active ? "#FFFFFF" : "#8B8B92"}
                />
              ) : null}
              <Text style={[styles.chipText, active && styles.chipTextActive]}>
                {item.label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );
}
