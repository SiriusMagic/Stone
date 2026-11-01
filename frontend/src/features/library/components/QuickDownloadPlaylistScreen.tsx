import { ReactNode } from "react";
import { Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { QuickDownloadPlaylistRow } from "./QuickDownloadPlaylistRow";
import { QuickDownloadPlaylistSummary } from "./QuickDownloadPlaylistSummary";
import { styles } from "../styles";
import {
  DownloadPlaylistEntry,
  DownloadQueueItem,
  DownloadQueueSnapshot,
} from "../types";

type Props = {
  visible: boolean;
  entries: DownloadPlaylistEntry[];
  selectedIds: string[];
  queueItems: Map<string, DownloadQueueItem>;
  blockedEntryIds: Set<string>;
  queueSnapshot: DownloadQueueSnapshot;
  allSelected: boolean;
  renderDownloadButton: (
    item: DownloadQueueItem | null | undefined,
    onPress: () => void,
  ) => ReactNode;
  onToggleAll: () => void;
  onToggleEntry: (entryId: string) => void;
  onCancelAll: () => void;
  onDeleteSelected: () => void;
  onDownloadSelected: () => void;
  onPlayEntry: (entry: DownloadPlaylistEntry) => void;
  onDownloadEntry: (entry: DownloadPlaylistEntry) => void;
  onDeleteEntry: (entryId: string) => void;
  onPrioritizeEntry: (entryId: string) => void;
};

export function QuickDownloadPlaylistScreen(props: Props) {
  if (!props.visible) return null;

  const selectedCount = props.entries.filter((entry) =>
    props.selectedIds.includes(entry.id),
  ).length;
  const canCancelQueued = props.queueSnapshot.activeCount > 0;

  return (
    <View style={styles.quickDownloadBlock}>
      <QuickDownloadPlaylistSummary
        totalCount={props.entries.length}
        selectedCount={selectedCount}
        allSelected={props.allSelected}
        canCancelQueued={canCancelQueued}
        onToggleAll={props.onToggleAll}
        onCancelAll={props.onCancelAll}
        onDeleteSelected={props.onDeleteSelected}
        onDownloadSelected={props.onDownloadSelected}
        queueSnapshot={props.queueSnapshot}
      />

      <View style={styles.quickDownloadPlaylistTableHead}>
        <TouchableOpacity
          style={styles.quickDownloadPlaylistTableCheck}
          activeOpacity={0.88}
          onPress={props.onToggleAll}
        >
          <View
            style={[
              styles.quickDownloadChecklist,
              props.allSelected && styles.quickDownloadChecklistActive,
            ]}
          >
            {props.allSelected ? (
              <Ionicons name="checkmark" size={16} color="#111117" />
            ) : null}
          </View>
        </TouchableOpacity>
        <Text style={styles.quickDownloadPlaylistTableIndex}>#</Text>
        <Text style={styles.quickDownloadPlaylistTableTitle}>Titulo</Text>
        <Text style={styles.quickDownloadPlaylistTableDuration}>Duracion</Text>
        <Text style={styles.quickDownloadPlaylistTableActions}>Acciones</Text>
        <Text style={styles.quickDownloadPlaylistTablePriority}>Prioridad</Text>
      </View>

      <View style={styles.quickDownloadPlaylistTableList}>
        {props.entries.map((entry) => {
          return (
            <QuickDownloadPlaylistRow
              key={entry.id}
              entry={entry}
              item={props.queueItems.get(entry.id)}
              selected={props.selectedIds.includes(entry.id)}
              isBlocked={props.blockedEntryIds.has(entry.id)}
              renderDownloadButton={props.renderDownloadButton}
              onToggle={props.onToggleEntry}
              onPlay={props.onPlayEntry}
              onDownload={props.onDownloadEntry}
              onDelete={props.onDeleteEntry}
              onPrioritize={props.onPrioritizeEntry}
            />
          );
        })}
      </View>

      {!props.entries.length ? (
        <View style={styles.quickDownloadEmptyState}>
          <Text style={styles.quickDownloadEmptyText}>
            Ya no quedan canciones visibles en esta playlist.
          </Text>
        </View>
      ) : null}
    </View>
  );
}
