import { View } from "react-native";

import MiniPlayer from "@/src/components/MiniPlayer";
import { styles } from "../../styles";

export function MiniPlayerZone() {
  return (
    <View style={styles.miniWrap}>
      <MiniPlayer />
    </View>
  );
}
