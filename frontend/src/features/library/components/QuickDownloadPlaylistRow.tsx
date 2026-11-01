import { ReactNode } from "react";
import { Image, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { DownloadPlaylistEntry, DownloadQueueItem } from "../types";

type Props = {
  entry: DownloadPlaylistEntry;
  item: DownloadQueueItem | null | undefined;
  selected: boolean;
  isBlocked: boolean;
  renderDownloadButton: (
    item: DownloadQueueItem | null | undefined,
    onPress: () => void,
  ) => ReactNode;
  onToggle: (entryId: string) => void;
  onPlay: (entry: DownloadPlaylistEntry) => void;
  onDownload: (entry: DownloadPlaylistEntry) => void;
  onDelete: (entryId: string) => void;
  onPrioritize: (entryId: string) => void;
};

export function QuickDownloadPlaylistRow(props: Props) {
  const canPlay =
    props.item?.status === "done" && Boolean(props.item.downloadUrl);

  return (
    <View
      style={[
        styles.quickDownloadPlaylistTableRow,
        props.isBlocked && { opacity: 0.55 },
      ]}
    >
      <TouchableOpacity
        style={styles.quickDownloadPlaylistTableMain}
        activeOpacity={0.88}
        onPress={
          props.isBlocked ? undefined : () => props.onToggle(props.entry.id)
        }
      >
        <View
          style={[
            styles.quickDownloadChecklist,
            props.selected && styles.quickDownloadChecklistActive,
          ]}
        >
          {props.selected ? (
            <Ionicons name="checkmark" size={16} color="#111117" />
          ) : null}
        </View>
        <Text style={styles.quickDownloadPlaylistTableRowIndex}>
          {props.entry.index}
        </Text>
        {props.entry.thumbnail ? (
          <Image
            source={{ uri: props.entry.thumbnail }}
            style={styles.quickDownloadPlaylistTableThumb}
          />
        ) : (
          <View style={styles.quickDownloadPlaylistTableThumbFallback}>
            <Ionicons
              name="musical-notes-outline"
              size={18}
              color="#F59E0B"
            />
          </View>
        )}
        <Text
          style={styles.quickDownloadPlaylistTableRowTitle}
          numberOfLines={2}
        >
          {props.entry.title}
        </Text>
        <Text style={styles.quickDownloadPlaylistTableRowDuration}>
          {props.entry.durationLabel}
        </Text>
      </TouchableOpacity>

      <View style={styles.quickDownloadPlaylistTableRowActions}>
        <TouchableOpacity
          style={[
            styles.quickDownloadEnhancedIconBtn,
            !canPlay && { opacity: 0.5 },
          ]}
          activeOpacity={0.88}
          onPress={() => props.onPlay(props.entry)}
        >
          <Ionicons name="play-outline" size={16} color="#FFFFFF" />
        </TouchableOpacity>

        {props.renderDownloadButton(
          props.item,
          () => props.onDownload(props.entry),
        )}

        <TouchableOpacity
          style={styles.quickDownloadEnhancedIconBtn}
          activeOpacity={0.88}
          onPress={() => props.onDelete(props.entry.id)}
        >
          <Ionicons name="trash-outline" size={16} color="#FFFFFF" />
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickDownloadEnhancedIconBtn}
          activeOpacity={0.88}
          onPress={() => props.onPrioritize(props.entry.id)}
        >
          <Ionicons
            name="reorder-three-outline"
            size={18}
            color="#FFFFFF"
          />
        </TouchableOpacity>
      </View>
    </View>
  );
}
