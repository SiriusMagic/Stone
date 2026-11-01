import { Image, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../../styles";
import { PENGUIN_URL } from "../../constants";
import { IconButton } from "../../shared/IconButton";

type Props = {
  searchVisible: boolean;
  query: string;
  placeholder: string;
  sortDirection: "asc" | "desc";
  onOpenLibraryFolder: () => void;
  onScanLibrary: () => void;
  onToggleSearch: () => void;
  onChangeQuery: (value: string) => void;
  onClearQuery: () => void;
  onOpenQuickDownload: () => void;
  onOpenSort: () => void;
  onOpenSettings: () => void;
};

export function HeaderZone(props: Props) {
  return (
    <>
      <View style={styles.topBar} testID="library-top-bar">
        <Image source={{ uri: PENGUIN_URL }} style={styles.brandLogo} />
        <View style={styles.topActions}>
          <IconButton icon="search" onPress={props.onToggleSearch} testID="library-search-toggle" />
          <IconButton icon="folder-open-outline" onPress={props.onOpenLibraryFolder} testID="library-folder-button" />
          <IconButton icon="scan-outline" onPress={props.onScanLibrary} testID="library-scan-button" />
          <IconButton icon="download-outline" onPress={props.onOpenQuickDownload} testID="library-quick-download-button" />
          <IconButton
            icon={props.sortDirection === "asc" ? "swap-vertical-outline" : "swap-vertical"}
            onPress={props.onOpenSort}
            testID="library-sort-button"
          />
          <IconButton icon="settings-outline" onPress={props.onOpenSettings} testID="library-settings-button" />
        </View>
      </View>

      {props.searchVisible ? (
        <View style={styles.searchBox}>
          <Ionicons name="search" size={18} color="#8B8B92" />
          <TextInput
            value={props.query}
            onChangeText={props.onChangeQuery}
            placeholder={props.placeholder}
            placeholderTextColor="#8B8B92"
            style={styles.searchInput}
            autoFocus
            testID="library-search-input"
          />
          {props.query ? (
            <TouchableOpacity onPress={props.onClearQuery}>
              <Ionicons name="close-circle" size={18} color="#8B8B92" />
            </TouchableOpacity>
          ) : null}
        </View>
      ) : null}
    </>
  );
}
