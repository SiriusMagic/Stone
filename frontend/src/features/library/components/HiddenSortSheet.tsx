import { Text, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { SortDirection, SortKey } from "../types";
import { styles } from "../styles";
import { BottomSheet } from "./shared";

type Props = {
  visible: boolean;
  sortKey: SortKey;
  sortDirection: SortDirection;
  onClose: () => void;
  onChange: (nextKey: SortKey) => void;
};

const ITEMS: { key: SortKey; label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { key: "recent", label: "Mas recientes", icon: "time" },
  { key: "mostPlayed", label: "Mas reproducido", icon: "bar-chart" },
  { key: "title", label: "A a Z", icon: "text" },
];

export function HiddenSortSheet({
  visible,
  sortKey,
  sortDirection,
  onClose,
  onChange,
}: Props) {
  return (
    <BottomSheet visible={visible} title="Ordenar videos por" onClose={onClose}>
      {ITEMS.map((item) => (
        <TouchableOpacity key={item.key} style={styles.sheetRow} onPress={() => onChange(item.key)}>
          <Ionicons name={item.icon} size={20} color="#F5C518" />
          <Text style={styles.sheetRowText}>{item.label}</Text>
          {sortKey === item.key ? (
            <Ionicons
              name={sortDirection === "asc" ? "arrow-up-outline" : "arrow-down-outline"}
              size={20}
              color="#F5C518"
            />
          ) : null}
        </TouchableOpacity>
      ))}
    </BottomSheet>
  );
}
