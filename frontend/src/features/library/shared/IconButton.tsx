import { TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";

type Props = {
  icon: keyof typeof Ionicons.glyphMap;
  color?: string;
  onPress: () => void;
  testID?: string;
};

export function IconButton({ icon, color = "#F5C518", onPress, testID }: Props) {
  return (
    <TouchableOpacity style={styles.iconBtn} onPress={onPress} testID={testID}>
      <Ionicons name={icon} size={22} color={color} />
    </TouchableOpacity>
  );
}
