import { Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { BottomSheet } from "./shared";

type ThemeMode = "dark" | "gray" | "light";

const OPTIONS: { id: ThemeMode; label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { id: "light", label: "Claro", icon: "sunny-outline" },
  { id: "gray", label: "Gris", icon: "cloud-outline" },
  { id: "dark", label: "Negro", icon: "moon-outline" },
];

export function ThemeSheet({
  visible,
  currentTheme,
  onClose,
  onSelectTheme,
}: {
  visible: boolean;
  currentTheme: ThemeMode;
  onClose: () => void;
  onSelectTheme: (theme: ThemeMode) => void;
}) {
  return (
    <BottomSheet visible={visible} title="Tema" onClose={onClose}>
      <View style={styles.settingsGroup}>
        {OPTIONS.map((option) => (
          <TouchableOpacity
            key={option.id}
            style={styles.settingsRow}
            onPress={() => onSelectTheme(option.id)}
          >
            <Ionicons name={option.icon} size={22} color="#F5C518" />
            <Text style={styles.settingsRowText}>{option.label}</Text>
            {currentTheme === option.id ? (
              <Ionicons name="checkmark-circle" size={20} color="#F5C518" />
            ) : (
              <Ionicons name="ellipse-outline" size={20} color="#8B8B92" />
            )}
          </TouchableOpacity>
        ))}
      </View>
    </BottomSheet>
  );
}
