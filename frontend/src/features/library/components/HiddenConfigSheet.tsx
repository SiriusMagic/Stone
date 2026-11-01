import { Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { BottomSheet } from "./shared";

type Props = {
  visible: boolean;
  hiddenVideoToAudio: boolean;
  onClose: () => void;
  onToggleVideoToAudio: () => void;
};

export function HiddenConfigSheet({
  visible,
  hiddenVideoToAudio,
  onClose,
  onToggleVideoToAudio,
}: Props) {
  return (
    <BottomSheet visible={visible} title="Configuracion de archivo" onClose={onClose}>
      <View style={styles.hiddenConfigRow}>
        <Text style={styles.hiddenConfigTitle}>
          Generar automaticamente canciones a partir de videos musicales
        </Text>
        <TouchableOpacity
          style={[styles.switchTrack, hiddenVideoToAudio && styles.switchTrackActive]}
          onPress={onToggleVideoToAudio}
        >
          <View style={[styles.switchThumb, hiddenVideoToAudio && styles.switchThumbActive]} />
        </TouchableOpacity>
      </View>
      <View style={styles.deletedActionDivider} />
      <TouchableOpacity style={styles.hiddenConfigAction}>
        <View style={styles.hiddenConfigActionText}>
          <Text style={styles.deletedActionText}>No se encontro el audio de WhatsApp</Text>
          <Text style={styles.hiddenConfigSubtitle}>
            Haga clic para agregar WhatsApp audio a la lista
          </Text>
        </View>
        <Ionicons name="chevron-forward" size={20} color="#A79AB8" />
      </TouchableOpacity>
      <TouchableOpacity style={styles.hiddenConfigAction}>
        <View style={styles.hiddenConfigActionText}>
          <Text style={styles.deletedActionText}>No se ha encontrado mas audio</Text>
          <Text style={styles.hiddenConfigSubtitle}>Haga clic para autorizar nuevas carpetas</Text>
        </View>
        <Ionicons name="chevron-forward" size={20} color="#A79AB8" />
      </TouchableOpacity>
    </BottomSheet>
  );
}
