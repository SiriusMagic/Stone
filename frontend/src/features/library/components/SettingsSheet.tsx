import { Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { BottomSheet } from "./shared";

export function SettingsSheet({
  visible,
  title,
  sleepTimerLabel,
  themeLabel,
  languageLabel,
  libraryFolderLabel,
  hiddenFilesCount,
  recentDeletedCount,
  onClose,
  onPickLibraryFolder,
  onRescanLibrary,
  onOpenTheme,
  onOpenSleepTimer,
  onOpenHiddenFiles,
  onOpenRecentDeleted,
  onOpenPlaybackSettings,
  onOpenNotifications,
  onOpenLanguage,
  onOpenFeedback,
}: {
  visible: boolean;
  title: string;
  sleepTimerLabel: string;
  themeLabel: string;
  languageLabel: string;
  libraryFolderLabel: string;
  hiddenFilesCount: number;
  recentDeletedCount: number;
  onClose: () => void;
  onPickLibraryFolder: () => void;
  onRescanLibrary: () => void;
  onOpenTheme: () => void;
  onOpenSleepTimer: () => void;
  onOpenHiddenFiles: () => void;
  onOpenRecentDeleted: () => void;
  onOpenPlaybackSettings: () => void;
  onOpenNotifications: () => void;
  onOpenLanguage: () => void;
  onOpenFeedback: () => void;
}) {
  const hiddenFilesLabel = `${hiddenFilesCount} archivo${hiddenFilesCount === 1 ? "" : "s"}`;
  const recentDeletedLabel = `${recentDeletedCount} archivo${recentDeletedCount === 1 ? "" : "s"}`;

  return (
    <BottomSheet visible={visible} title={title} onClose={onClose}>
      <View style={styles.settingsGroup}>
        <TouchableOpacity style={styles.settingsRow} onPress={onPickLibraryFolder}>
          <Ionicons name="folder-open-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Carpeta de Stone</Text>
          <Text style={styles.settingsRowMeta}>{libraryFolderLabel}</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.settingsRow} onPress={onRescanLibrary}>
          <Ionicons name="scan-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Escanear biblioteca</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.settingsRow} onPress={onOpenTheme}>
          <Ionicons name="shirt-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Tema</Text>
          <Text style={styles.settingsRowMeta}>{themeLabel}</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.settingsRow} onPress={onOpenSleepTimer}>
          <Ionicons name="timer-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Temporizador para dormir</Text>
          <Text style={styles.settingsRowMeta}>{sleepTimerLabel}</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
      </View>

      <View style={styles.settingsGroup}>
        <TouchableOpacity style={styles.settingsRow} onPress={onOpenHiddenFiles}>
          <Ionicons name="eye-off-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Archivos ocultos</Text>
          <Text style={styles.settingsRowMeta}>{hiddenFilesLabel}</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.settingsRow} onPress={onOpenRecentDeleted}>
          <Ionicons name="trash-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Eliminado recientemente</Text>
          <Text style={styles.settingsRowMeta}>{recentDeletedLabel}</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.settingsRow} onPress={onOpenPlaybackSettings}>
          <Ionicons name="headset-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Ajustes de reproducción</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.settingsRow} onPress={onOpenNotifications}>
          <Ionicons name="notifications-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Configuración de notificaciones</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
      </View>

      <View style={styles.settingsGroup}>
        <TouchableOpacity
          style={styles.settingsRow}
          onPress={onOpenLanguage}
          testID="settings-language-button"
        >
          <Ionicons name="globe-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Idioma</Text>
          <Text style={styles.settingsRowMeta}>{languageLabel}</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
      </View>

      <View style={styles.settingsGroup}>
        <TouchableOpacity style={styles.settingsRow} onPress={onOpenFeedback} testID="settings-feedback-button">
          <Ionicons name="mail-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Opinión</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.settingsRow}>
          <Ionicons name="information-circle-outline" size={22} color="#F5C518" />
          <Text style={styles.settingsRowText}>Sobre</Text>
          <Text style={styles.settingsRowMeta}>2026.8.6</Text>
          <Ionicons name="chevron-forward" size={18} color="#8B8B92" />
        </TouchableOpacity>
      </View>
    </BottomSheet>
  );
}
