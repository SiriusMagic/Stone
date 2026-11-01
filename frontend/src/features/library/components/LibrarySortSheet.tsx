import { Text, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { SortDirection, SortKey } from "../types";
import { BottomSheet } from "./shared";

export function LibrarySortSheet({
  visible,
  title,
  sortKey,
  sortDirection,
  onClose,
  onChange,
}: {
  visible: boolean;
  title: string;
  sortKey: SortKey;
  sortDirection: SortDirection;
  onClose: () => void;
  onChange: (key: SortKey) => void;
}) {
  return (
    <BottomSheet visible={visible} title={title} onClose={onClose}>
      {([
        { key: "recent", label: "Más recientes", icon: "time" },
        { key: "mostPlayed", label: "Más reproducido", icon: "bar-chart" },
        { key: "title", label: "A a Z", icon: "text" },
        { key: "artist", label: "Artista", icon: "person" },
        { key: "duration", label: "Duración más larga", icon: "hourglass-outline" },
      ] as { key: SortKey; label: string; icon: keyof typeof Ionicons.glyphMap }[]).map(
        (option) => (
          <TouchableOpacity
            key={option.key}
            style={styles.sheetRow}
            onPress={() => onChange(option.key)}
            testID={`sort-option-${option.key}`}
          >
            <Ionicons name={option.icon} size={20} color="#F5C518" />
            <Text style={styles.sheetRowText}>{option.label}</Text>
            {sortKey === option.key ? (
              <Ionicons
                name={sortDirection === "asc" ? "arrow-up-outline" : "arrow-down-outline"}
                size={20}
                color="#F5C518"
              />
            ) : null}
          </TouchableOpacity>
        )
      )}
    </BottomSheet>
  );
}
