import { ReactNode } from "react";
import {
  Image,
  Modal,
  Pressable,
  ScrollView,
  Text,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { Artwork } from "@/src/components/Artwork";

import { styles } from "../styles";

export function EmptyHint({ t }: { t: (key: string) => string }) {
  return <EmptyState icon="musical-notes-outline" text={t("no_songs")} />;
}

export function EmptyState({
  icon,
  text,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  text: string;
}) {
  return (
    <View style={styles.emptyHint}>
      <Ionicons name={icon} size={40} color="#2A2A35" />
      <Text style={styles.emptyText}>{text}</Text>
    </View>
  );
}

export function RowItem({
  cover,
  iconNode,
  title,
  subtitle,
  onPress,
}: {
  cover?: string;
  iconNode?: ReactNode;
  title: string;
  subtitle: string;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      activeOpacity={0.85}
      style={styles.row}
      onPress={onPress}
      testID="library-row-item"
    >
      {cover ? <Artwork uri={cover} style={styles.rowCover} iconSize={22} /> : iconNode}
      <View style={styles.rowInfo}>
        <Text style={styles.rowTitle} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.rowSubtitle} numberOfLines={1}>
          {subtitle}
        </Text>
      </View>
    </TouchableOpacity>
  );
}

export function BottomSheet({
  visible,
  title,
  onClose,
  children,
}: {
  visible: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.modalBackdrop}>
        <Pressable style={styles.modalBackdropPressable} onPress={onClose} />
        <TouchableWithoutFeedback>
          <View style={styles.sheet}>
          <View style={styles.sheetHandle} />
          <Text style={styles.sheetTitle}>{title}</Text>
          <ScrollView
            style={styles.sheetScroll}
            contentContainerStyle={styles.sheetScrollContent}
            showsVerticalScrollIndicator={false}
          >
            {children}
          </ScrollView>
          </View>
        </TouchableWithoutFeedback>
      </View>
    </Modal>
  );
}

export function ScreenSheet({
  visible,
  title,
  onClose,
  children,
}: {
  visible: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.fullScreen}>
        <View style={styles.fullScreenHeader}>
          <TouchableOpacity style={styles.fullScreenBackBtn} onPress={onClose}>
            <Ionicons name="chevron-back" size={28} color="#FFFFFF" />
          </TouchableOpacity>
          <Text style={styles.fullScreenTitle}>{title}</Text>
        </View>
        <ScrollView
          style={styles.fullScreenScroll}
          contentContainerStyle={styles.fullScreenScrollContent}
          showsVerticalScrollIndicator={false}
        >
          {children}
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

export function SettingToggleRow({
  title,
  subtitle,
  value,
  onToggle,
  icon,
}: {
  title: string;
  subtitle?: string;
  value: boolean;
  onToggle: () => void;
  icon?: keyof typeof Ionicons.glyphMap;
}) {
  return (
    <View style={styles.toggleRow}>
      {icon ? (
        <View style={styles.toggleRowIconWrap}>
          <Ionicons name={icon} size={28} color="#8F879A" />
        </View>
      ) : null}
      <View style={styles.toggleRowTextWrap}>
        <Text style={styles.toggleRowTitle}>{title}</Text>
        {subtitle ? <Text style={styles.toggleRowSubtitle}>{subtitle}</Text> : null}
      </View>
      <TouchableOpacity
        style={[styles.switchTrack, value && styles.switchTrackActive]}
        onPress={onToggle}
      >
        <View style={[styles.switchThumb, value && styles.switchThumbActive]} />
      </TouchableOpacity>
    </View>
  );
}
