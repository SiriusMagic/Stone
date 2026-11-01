import { memo, useCallback, useRef, useState } from "react";
import { FlatList, Modal, Platform, Pressable, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Track } from "@/src/data/tracks";
import { QueueRow } from "./QueueRow";

type RepeatMode = "off" | "all" | "one";
type Props = {
  currentIndex: number;
  onClose: () => void;
  onPlay: (track: Track, queue: Track[]) => void;
  onReorder: (queue: Track[]) => void;
  onSetRepeat: (mode: RepeatMode) => void;
  onSetShuffle: (value: boolean) => void;
  queue: Track[];
  repeat: RepeatMode;
  shuffle: boolean;
};

export const QueueSheet = memo(function QueueSheet(props: Props) {
  const listRef = useRef<FlatList<Track>>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const drop = useCallback((target: number) => {
    if (dragIndex === null || dragIndex === target) return setDragIndex(null);
    const reordered = [...props.queue];
    const [moved] = reordered.splice(dragIndex, 1);
    reordered.splice(target, 0, moved);
    props.onReorder(reordered);
    setDragIndex(null);
  }, [dragIndex, props]);
  const renderItem = useCallback(({ item, index }: { item: Track; index: number }) => (
    <QueueRow
      item={item} index={index} isCurrent={index === props.currentIndex} dragging={dragIndex === index}
      onPlay={(track) => props.onPlay(track, props.queue)} onDragStart={setDragIndex}
      onDragEnd={() => setDragIndex(null)} onDrop={drop}
    />
  ), [dragIndex, drop, props]);

  return (
    <Modal visible transparent animationType="slide" hardwareAccelerated onRequestClose={props.onClose}>
      <View style={styles.backdrop}>
        <Pressable style={StyleSheet.absoluteFill} onPress={props.onClose} />
        <View style={styles.sheet}>
          <View style={styles.handle} />
          <View style={styles.header}>
            <Text style={styles.heading}>Fila</Text>
            <View style={styles.headerActions}>
              <TouchableOpacity style={styles.headerButton} onPress={() => listRef.current?.scrollToIndex({ index: Math.max(0, props.currentIndex), animated: true })}>
                <Ionicons name="locate-outline" size={24} color="#E8E6EE" />
              </TouchableOpacity>
              <TouchableOpacity style={styles.headerButton}><Ionicons name="brush-outline" size={24} color="#E8E6EE" /></TouchableOpacity>
              <TouchableOpacity style={styles.headerButton} onPress={props.onClose}><Ionicons name="close" size={22} color="#E8E6EE" /></TouchableOpacity>
            </View>
          </View>
          <FlatList
            ref={listRef} data={props.queue} renderItem={renderItem}
            keyExtractor={(item, index) => `${item.id}-${index}`}
            contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}
            initialNumToRender={8} maxToRenderPerBatch={8} updateCellsBatchingPeriod={16}
            windowSize={5} removeClippedSubviews={Platform.OS === "android"}
            getItemLayout={(_, index) => ({ length: 66, offset: 66 * index, index })}
            ListFooterComponent={<QueueFooter {...props} />}
          />
        </View>
      </View>
    </Modal>
  );
});

function QueueFooter(props: Props) {
  const buttons = [
    { icon: "repeat" as const, active: props.repeat === "one", press: () => props.onSetRepeat(props.repeat === "one" ? "off" : "one"), badge: "1" },
    { icon: "repeat" as const, active: props.repeat === "all", press: () => props.onSetRepeat(props.repeat === "all" ? "off" : "all") },
    { icon: "shuffle" as const, active: props.shuffle, press: () => props.onSetShuffle(!props.shuffle) },
  ];
  return <View style={styles.footer}>{buttons.map((button, index) => (
    <TouchableOpacity key={index} style={[styles.footerButton, button.active && styles.footerActive]} onPress={button.press}>
      <Ionicons name={button.icon} size={28} color={button.active ? "#4A292B" : "#E8E6EE"} />
      {button.badge && button.active ? <Text style={styles.badge}>{button.badge}</Text> : null}
    </TouchableOpacity>
  ))}</View>;
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.45)", justifyContent: "flex-end" },
  sheet: { backgroundColor: "#5A3436", borderTopLeftRadius: 28, borderTopRightRadius: 28, paddingTop: 10, paddingBottom: 10, height: "70%" },
  handle: { width: 56, height: 4, borderRadius: 2, backgroundColor: "#7A5759", alignSelf: "center", marginBottom: 14 },
  header: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: 18, marginBottom: 8 },
  heading: { color: "#FFFFFF", fontSize: 20, fontWeight: "800" },
  headerActions: { flexDirection: "row", alignItems: "center", gap: 6 },
  headerButton: { width: 40, height: 40, alignItems: "center", justifyContent: "center" },
  listContent: { paddingHorizontal: 12, paddingRight: 34, paddingBottom: 14 },
  footer: { flexDirection: "row", gap: 10, paddingHorizontal: 2, paddingTop: 12, paddingBottom: 4, marginTop: 8 },
  footerButton: { flex: 1, height: 46, borderRadius: 23, backgroundColor: "#4A292B", alignItems: "center", justifyContent: "center" },
  footerActive: { backgroundColor: "#E8E6EE" },
  badge: { position: "absolute", right: 18, top: 10, color: "#4A292B", fontSize: 11, fontWeight: "800" },
});
