import { memo } from "react";
import { Image, Platform, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Track } from "@/src/data/tracks";

type Props = {
  dragging: boolean;
  index: number;
  isCurrent: boolean;
  item: Track;
  onDragEnd: () => void;
  onDragStart: (index: number) => void;
  onDrop: (index: number) => void;
  onPlay: (item: Track) => void;
};

export const QueueRow = memo(function QueueRow(props: Props) {
  return (
    <View
      style={[styles.row, props.isCurrent && styles.active, props.dragging && styles.dragging]}
      {...(Platform.OS === "web" ? ({
        onDragOver: (event: any) => event.preventDefault?.(),
        onDrop: (event: any) => { event.preventDefault?.(); props.onDrop(props.index); },
      } as any) : {})}
    >
      <TouchableOpacity style={styles.main} onPress={() => props.onPlay(props.item)}>
        <Image source={{ uri: props.item.cover }} style={styles.cover} />
        <View style={styles.info}>
          <Text style={styles.title} numberOfLines={1}>{props.item.title}</Text>
          <Text style={styles.subtitle} numberOfLines={1}>{props.item.artist}</Text>
        </View>
      </TouchableOpacity>
      <View
        style={styles.handle}
        {...(Platform.OS === "web" ? ({
          draggable: true,
          onDragStart: () => props.onDragStart(props.index),
          onDragEnd: props.onDragEnd,
        } as any) : {})}
      >
        <Ionicons name="reorder-three-outline" size={28} color="#E8E6EE" />
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  row: { height: 62, flexDirection: "row", alignItems: "center", paddingHorizontal: 8, paddingVertical: 8, borderRadius: 16, marginBottom: 4 },
  active: { backgroundColor: "rgba(255,255,255,0.05)" },
  dragging: { backgroundColor: "rgba(255,255,255,0.1)" },
  main: { flex: 1, flexDirection: "row", alignItems: "center" },
  cover: { width: 46, height: 46, borderRadius: 12, backgroundColor: "#6D4B4D" },
  info: { flex: 1, marginLeft: 10, paddingRight: 8 },
  title: { color: "#FFFFFF", fontSize: 14, fontWeight: "700" },
  subtitle: { color: "#D7D0D5", fontSize: 12, marginTop: 2 },
  handle: { width: 36, height: 36, alignItems: "center", justifyContent: "center", marginLeft: 6 },
});
