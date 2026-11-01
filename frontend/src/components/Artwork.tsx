import { useEffect, useState } from "react";
import { Image, ImageStyle, StyleProp, StyleSheet, View, ViewStyle } from "react-native";
import { Ionicons } from "@expo/vector-icons";

type Props = {
  uri?: string | null;
  style: StyleProp<ImageStyle>;
  iconSize?: number;
};

export function Artwork({ uri, style, iconSize = 26 }: Props) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [uri]);

  if (!uri || failed) {
    return (
      <View style={[styles.fallback, style as StyleProp<ViewStyle>]}>
        <Ionicons name="musical-notes" size={iconSize} color="#8E86A1" />
      </View>
    );
  }

  return <Image source={{ uri }} style={style} onError={() => setFailed(true)} />;
}

const styles = StyleSheet.create({
  fallback: {
    alignItems: "center",
    backgroundColor: "#1A1823",
    borderWidth: 1,
    borderColor: "#262233",
    justifyContent: "center",
    overflow: "hidden",
  },
});
