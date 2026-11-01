import { Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { DownloadQueueSnapshot } from "../types";

type Props = {
  totalCount: number;
  selectedCount: number;
  allSelected: boolean;
  canCancelQueued: boolean;
  onToggleAll: () => void;
  onCancelAll: () => void;
  onDeleteSelected: () => void;
  onDownloadSelected: () => void;
  queueSnapshot: DownloadQueueSnapshot;
};

export function QuickDownloadPlaylistSummary(props: Props) {
  return (
    <>
      <Text style={styles.quickDownloadPlaylistInlineTitle}>
        Canciones de la playlist
      </Text>
      <Text style={styles.quickDownloadPlaylistSummary}>
        {props.selectedCount} de {props.totalCount} seleccionadas
      </Text>

      <View style={styles.quickDownloadPlaylistToolbar}>
        <TouchableOpacity
          style={[
            styles.quickDownloadPlaylistToolbarBtn,
            styles.quickDownloadPlaylistToolbarBtnAccent,
            !props.totalCount && { opacity: 0.45 },
          ]}
          activeOpacity={0.88}
          onPress={props.onToggleAll}
          disabled={!props.totalCount}
        >
          <Ionicons name="list-outline" size={18} color="#F59E0B" />
          <Text style={styles.quickDownloadPlaylistToolbarBtnText}>
            {props.allSelected ? "Deseleccionar todo" : "Seleccionar todo"}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.quickDownloadPlaylistToolbarBtn,
            !props.canCancelQueued && { opacity: 0.45 },
          ]}
          activeOpacity={0.88}
          onPress={props.onCancelAll}
          disabled={!props.canCancelQueued}
        >
          <Ionicons name="close-outline" size={18} color="#FFFFFF" />
          <Text style={styles.quickDownloadPlaylistToolbarBtnText}>
            Cancelar todo
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.quickDownloadPlaylistToolbarBtn,
            !props.selectedCount && { opacity: 0.45 },
          ]}
          activeOpacity={0.88}
          onPress={props.onDeleteSelected}
          disabled={!props.selectedCount}
        >
          <Ionicons name="trash-outline" size={18} color="#F87171" />
          <Text style={styles.quickDownloadPlaylistToolbarBtnText}>
            Borrar seleccionados
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.quickDownloadPlaylistToolbarBtn,
            styles.quickDownloadPlaylistToolbarBtnPrimary,
            !props.selectedCount && { opacity: 0.45 },
          ]}
          activeOpacity={0.88}
          onPress={props.onDownloadSelected}
          disabled={!props.selectedCount}
        >
          <Ionicons name="download-outline" size={18} color="#FFF4E6" />
          <Text style={styles.quickDownloadPlaylistToolbarBtnPrimaryText}>
            Descargar seleccionados ({props.selectedCount})
          </Text>
        </TouchableOpacity>
      </View>

      {props.queueSnapshot.totalCount ? (
        <View style={styles.quickDownloadPlaylistInlineProgress}>
          <View style={styles.quickDownloadPlaylistInlineProgressTop}>
            <Text style={styles.quickDownloadPlaylistInlineProgressLabel}>
              Descargando...
            </Text>
            <Text style={styles.quickDownloadPlaylistInlineProgressMeta}>
              {props.queueSnapshot.doneCount} / {props.queueSnapshot.totalCount} descargadas
            </Text>
            <View style={styles.quickDownloadGlobalProgressBadge}>
              <Text style={styles.quickDownloadGlobalProgressBadgeText}>
                {props.queueSnapshot.progressPercent}%
              </Text>
            </View>
          </View>

          <View style={styles.quickDownloadBarTrack}>
            <View
              style={[
                styles.quickDownloadBarFill,
                { width: `${props.queueSnapshot.progressPercent}%` },
              ]}
            />
          </View>

          <Text style={styles.quickDownloadGlobalProgressHint}>
            Tiempo restante estimado: {props.queueSnapshot.etaLabel ?? "--:--"}
          </Text>
        </View>
      ) : null}
    </>
  );
}
