import { Modal, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { HIDDEN_ITEMS } from "../constants";
import { styles } from "../styles";

type HiddenItem = (typeof HIDDEN_ITEMS)[number] | null;

type Props = {
  item: HiddenItem;
  onClose: () => void;
};

export function HiddenItemActionSheet({ item, onClose }: Props) {
  return (
    <Modal visible={!!item} transparent animationType="slide" onRequestClose={onClose}>
      <TouchableOpacity style={styles.modalBackdrop} activeOpacity={1} onPress={onClose}>
        <View style={styles.deletedActionSheet}>
          <View style={styles.sheetHandle} />
          {item ? (
            <>
              <View style={styles.deletedActionHeader}>
                <View style={styles.deletedAudioCover}>
                  <Ionicons name="musical-note" size={22} color="#8F879A" />
                </View>
                <Text style={styles.deletedActionTitle} numberOfLines={1}>
                  {item.title}
                </Text>
                <TouchableOpacity>
                  <Ionicons name="pencil-outline" size={26} color="#D5CDEE" />
                </TouchableOpacity>
              </View>
              <TouchableOpacity style={styles.deletedActionRow}>
                <Ionicons name="share-social-outline" size={26} color="#D5CDEE" />
                <Text style={styles.deletedActionText}>Compartir</Text>
              </TouchableOpacity>
              <View style={styles.deletedActionDivider} />
              <TouchableOpacity style={styles.deletedActionRow}>
                <Ionicons name="eye-outline" size={26} color="#D5CDEE" />
                <Text style={styles.deletedActionText}>Mostrar</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.deletedActionRow}>
                <Ionicons name="trash-outline" size={26} color="#D5CDEE" />
                <Text style={styles.deletedActionText}>Borrar</Text>
              </TouchableOpacity>
            </>
          ) : null}
        </View>
      </TouchableOpacity>
    </Modal>
  );
}
