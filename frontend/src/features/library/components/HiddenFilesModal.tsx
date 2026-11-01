import { Image, Modal, ScrollView, Text, TextInput, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { DeletedTab, HiddenCategory, HiddenItem } from "../types";

export function HiddenFilesModal({
  visible,
  searchVisible,
  hiddenTab,
  hiddenCategory,
  hiddenQuery,
  items,
  onClose,
  onBack,
  onShowSearch,
  onOpenSort,
  onOpenConfig,
  onChangeQuery,
  onSelectTab,
  onSelectCategory,
  onOpenItem,
}: {
  visible: boolean;
  searchVisible: boolean;
  hiddenTab: DeletedTab;
  hiddenCategory: HiddenCategory;
  hiddenQuery: string;
  items: HiddenItem[];
  onClose: () => void;
  onBack: () => void;
  onShowSearch: () => void;
  onOpenSort: () => void;
  onOpenConfig: () => void;
  onChangeQuery: (value: string) => void;
  onSelectTab: (tab: DeletedTab) => void;
  onSelectCategory: (category: HiddenCategory) => void;
  onOpenItem: (id: string) => void;
}) {
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.fullScreen}>
        <View style={styles.fullScreenHeader}>
          <TouchableOpacity style={styles.fullScreenBackBtn} onPress={onBack}>
            <Ionicons name="chevron-back" size={28} color="#FFFFFF" />
          </TouchableOpacity>
          {searchVisible ? (
            <View style={styles.hiddenSearchBar}>
              <TextInput
                value={hiddenQuery}
                onChangeText={onChangeQuery}
                placeholder="Buscar mi música y vídeos"
                placeholderTextColor="#7E7591"
                style={styles.hiddenSearchInput}
                autoFocus
              />
              <Ionicons name="search" size={22} color="#8F879A" />
            </View>
          ) : (
            <>
              <Text style={styles.fullScreenTitle}>Archivos ocultos</Text>
              <View style={styles.hiddenHeaderActions}>
                <TouchableOpacity style={styles.iconBtn} onPress={onShowSearch}>
                  <Ionicons name="search" size={22} color="#D8D1E7" />
                </TouchableOpacity>
                <TouchableOpacity style={styles.iconBtn} onPress={onOpenSort}>
                  <Ionicons name="swap-vertical" size={22} color="#D8D1E7" />
                </TouchableOpacity>
                <TouchableOpacity style={styles.iconBtn} onPress={onOpenConfig}>
                  <Ionicons name="ellipsis-vertical" size={22} color="#D8D1E7" />
                </TouchableOpacity>
              </View>
            </>
          )}
        </View>

        {!searchVisible ? (
          <>
            <View style={styles.deletedTabs}>
              {([
                { key: "audio", label: "Audio", icon: "headset-outline" },
                { key: "video", label: "Vídeo", icon: "videocam-outline" },
              ] as { key: DeletedTab; label: string; icon: keyof typeof Ionicons.glyphMap }[]).map((item) => {
                const active = hiddenTab === item.key;

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

            {hiddenTab === "audio" ? (
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={styles.hiddenChipsRow}
              >
                {([
                  { key: "all", label: "Todo" },
                  { key: "voice", label: "Mensajes de voz" },
                  { key: "recordings", label: "Grabaciones" },
                  { key: "other", label: "Otros" },
                ] as { key: HiddenCategory; label: string }[]).map((item) => {
                  const active = hiddenCategory === item.key;

                  return (
                    <TouchableOpacity
                      key={item.key}
                      style={[styles.hiddenChip, active && styles.hiddenChipActive]}
                      onPress={() => onSelectCategory(item.key)}
                    >
                      <Text style={[styles.hiddenChipText, active && styles.hiddenChipTextActive]}>
                        {item.label}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>
            ) : null}
          </>
        ) : null}

        <ScrollView
          style={[styles.fullScreenScroll, !searchVisible && styles.hiddenListScroll]}
          contentContainerStyle={[
            styles.fullScreenScrollContent,
            !searchVisible && styles.hiddenListContent,
          ]}
          showsVerticalScrollIndicator={false}
        >
          {items.length ? (
            items.map((item) => (
              <TouchableOpacity
                key={item.id}
                style={styles.deletedRow}
                activeOpacity={0.85}
                onPress={() => onOpenItem(item.id)}
              >
                <View style={styles.deletedCoverWrap}>
                  {item.cover ? (
                    <Image source={{ uri: item.cover }} style={styles.deletedCover} />
                  ) : (
                    <View style={styles.deletedAudioCover}>
                      <Ionicons name="musical-note" size={22} color="#8F879A" />
                      <Ionicons
                        name="eye-off-outline"
                        size={18}
                        color="#D8D1E7"
                        style={styles.hiddenEyeIcon}
                      />
                    </View>
                  )}
                </View>
                <View style={styles.deletedInfo}>
                  <Text style={styles.deletedTitle} numberOfLines={item.type === "video" ? 2 : 1}>
                    {item.title}
                  </Text>
                  <Text style={styles.deletedSubtitle} numberOfLines={1}>
                    {item.subtitle}
                  </Text>
                </View>
                {!searchVisible ? (
                  <TouchableOpacity style={styles.deletedMoreBtn} onPress={() => onOpenItem(item.id)}>
                    <Ionicons name="ellipsis-vertical" size={22} color="#A79AB8" />
                  </TouchableOpacity>
                ) : null}
              </TouchableOpacity>
            ))
          ) : (
            <Text style={styles.hiddenEmptyText}>
              {searchVisible
                ? "No se encontraron canciones"
                : hiddenTab === "video"
                  ? "Sin vídeos ocultos"
                  : "Sin audios ocultos"}
            </Text>
          )}
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}
