import { Image, Modal, ScrollView, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { DeletedItem, DeletedTab } from "../types";

export function RecentDeletedModal({
  visible,
  selectionMode,
  deletedTab,
  selectedDeletedIds,
  items,
  onClose,
  onSelectTab,
  onToggleSelection,
  onOpenItemActions,
  onSelectAll,
}: {
  visible: boolean;
  selectionMode: boolean;
  deletedTab: DeletedTab;
  selectedDeletedIds: string[];
  items: DeletedItem[];
  onClose: () => void;
  onSelectTab: (tab: DeletedTab) => void;
  onToggleSelection: (id: string) => void;
  onOpenItemActions: (id: string) => void;
  onSelectAll: () => void;
}) {
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.fullScreen}>
        <View style={styles.fullScreenHeader}>
          <TouchableOpacity style={styles.fullScreenBackBtn} onPress={onClose}>
            <Ionicons name="chevron-back" size={28} color="#FFFFFF" />
          </TouchableOpacity>
          <Text style={styles.fullScreenTitle}>
            {selectionMode ? `${selectedDeletedIds.length} seleccionado` : "Eliminado recientemente"}
          </Text>
        </View>

        <View style={styles.deletedTabs}>
          {([
            { key: "audio", label: "Audio", icon: "headset-outline" },
            { key: "video", label: "Vídeo", icon: "videocam-outline" },
          ] as { key: DeletedTab; label: string; icon: keyof typeof Ionicons.glyphMap }[]).map((item) => {
            const active = deletedTab === item.key;

            return (
              <TouchableOpacity
                key={item.key}
                style={[styles.deletedTabBtn, active && styles.deletedTabBtnActive]}
                onPress={() => onSelectTab(item.key)}
              >
                <Ionicons name={item.icon} size={20} color={active ? "#F3EDF9" : "#B4ABC3"} />
                <Text style={[styles.deletedTabText, active && styles.deletedTabTextActive]}>
                  {item.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>

        <ScrollView
          style={styles.fullScreenScroll}
          contentContainerStyle={[
            styles.fullScreenScrollContent,
            { paddingBottom: selectionMode ? 120 : 40 },
          ]}
          showsVerticalScrollIndicator={false}
        >
          {selectionMode ? (
            <TouchableOpacity style={styles.selectAllRow} onPress={onSelectAll}>
              <Ionicons
                name={
                  selectedDeletedIds.length === items.length && items.length > 0
                    ? "checkmark-circle"
                    : "ellipse-outline"
                }
                size={34}
                color="#B7AEC7"
              />
              <Text style={styles.selectAllText}>Seleccionar todos</Text>
            </TouchableOpacity>
          ) : null}

          {items.map((item) => {
            const selected = selectedDeletedIds.includes(item.id);

            return (
              <TouchableOpacity
                key={item.id}
                style={styles.deletedRow}
                activeOpacity={0.85}
                onLongPress={() => onToggleSelection(item.id)}
                delayLongPress={180}
                onPress={() => {
                  if (selectionMode) {
                    onToggleSelection(item.id);
                  } else {
                    onOpenItemActions(item.id);
                  }
                }}
              >
                {selectionMode ? (
                  <Ionicons
                    name={selected ? "checkmark-circle" : "ellipse-outline"}
                    size={34}
                    color={selected ? "#B7AEC7" : "#7C738A"}
                    style={styles.deletedSelectIcon}
                  />
                ) : null}
                <View style={styles.deletedCoverWrap}>
                  {item.cover ? (
                    <Image source={{ uri: item.cover }} style={styles.deletedCover} />
                  ) : (
                    <View style={styles.deletedAudioCover}>
                      <Ionicons name="musical-note" size={22} color="#8F879A" />
                    </View>
                  )}
                  <Text style={styles.deletedDays}>{item.daysAgo} días</Text>
                </View>
                <View style={styles.deletedInfo}>
                  <Text style={styles.deletedTitle} numberOfLines={item.type === "video" ? 2 : 1}>
                    {item.title}
                  </Text>
                  <Text style={styles.deletedSubtitle} numberOfLines={1}>
                    {item.subtitle}
                  </Text>
                </View>
                {!selectionMode ? (
                  <TouchableOpacity style={styles.deletedMoreBtn} onPress={() => onOpenItemActions(item.id)}>
                    <Ionicons name="ellipsis-vertical" size={22} color="#A79AB8" />
                  </TouchableOpacity>
                ) : null}
              </TouchableOpacity>
            );
          })}

          <Text style={styles.deletedHint}>
            {deletedTab === "audio"
              ? "Los audios se eliminarán de forma permanente después de 30 días."
              : "Los videos se eliminarán permanentemente después de 30 días."}
          </Text>
        </ScrollView>

        {selectionMode ? (
          <View style={styles.deletedBottomBar}>
            <TouchableOpacity style={styles.deletedBottomAction}>
              <Ionicons name="refresh-outline" size={28} color="#C7BED7" />
              <Text style={styles.deletedBottomText}>Restaurar</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.deletedBottomAction}>
              <Ionicons name="trash-outline" size={28} color="#C7BED7" />
              <Text style={styles.deletedBottomText}>Borrar</Text>
            </TouchableOpacity>
          </View>
        ) : null}
      </SafeAreaView>
    </Modal>
  );
}
