import { Image, Modal, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { DELETED_ITEMS } from "../constants";

type DeletedItem = (typeof DELETED_ITEMS)[number] | null;

type Props = {
  item: DeletedItem;
  onClose: () => void;
};

export function DeletedItemActionSheet({ item, onClose }: Props) {
  return (
    <Modal visible={!!item} transparent animationType="slide" onRequestClose={onClose}>
      <TouchableOpacity style={styles.modalBackdrop} activeOpacity={1} onPress={onClose}>
        <View style={styles.deletedActionSheet}>
          <View style={styles.sheetHandle} />
          {item ? (
            <>
              <View style={styles.deletedActionHeader}>
                {item.cover ? (
                  <Image source={{ uri: item.cover }} style={styles.deletedActionCover} />
                ) : (
                  <View style={styles.deletedAudioCover}>
                    <Ionicons name="musical-note" size={22} color="#8F879A" />
                  </View>
                )}
                <Text style={styles.deletedActionTitle} numberOfLines={1}>
                  {item.title}
                </Text>
              </View>
              <TouchableOpacity style={styles.deletedActionRow}>
                <Ionicons name="refresh-outline" size={26} color="#D5CDEE" />
                <Text style={styles.deletedActionText}>Restaurar</Text>
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
