import { useState } from "react";
import { Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { styles } from "../styles";
import { SleepTimerOption } from "../types";
import { BottomSheet } from "./shared";
import { CustomSleepTimerSheet } from "./CustomSleepTimerSheet";

export function SleepTimerSheet({
  visible,
  onClose,
  option,
  customMinutes,
  finishTrack,
  onChangeOption,
  onChangeCustomMinutes,
  onToggleFinishTrack,
}: {
  visible: boolean;
  onClose: () => void;
  option: SleepTimerOption;
  customMinutes: number;
  finishTrack: boolean;
  onChangeOption: (option: SleepTimerOption) => void;
  onChangeCustomMinutes: (minutes: number) => void;
  onToggleFinishTrack: () => void;
}) {
  const [customVisible, setCustomVisible] = useState(false);
  return (
    <>
    <BottomSheet visible={visible} title="Temporizador para dormir" onClose={onClose}>
      {([
        { key: "off", label: "Apagar" },
        { key: "10", label: "10 minutos más tarde" },
        { key: "20", label: "20 minutos más tarde" },
        { key: "30", label: "30 minutos más tarde" },
        { key: "60", label: "60 minutos más tarde" },
      ] as { key: Exclude<SleepTimerOption, "custom">; label: string }[]).map((item) => (
        <TouchableOpacity
          key={item.key}
          style={styles.sleepTimerOptionRow}
          onPress={() => onChangeOption(item.key)}
        >
          <Text style={styles.sleepTimerOptionText}>{item.label}</Text>
          {option === item.key ? <Ionicons name="checkmark" size={28} color="#DCD4F3" /> : null}
        </TouchableOpacity>
      ))}

      <TouchableOpacity
        style={styles.sleepTimerCustomRow}
        onPress={() => setCustomVisible(true)}
      >
        <Text style={styles.sleepTimerOptionText}>Personalizado</Text>
        <View style={styles.sleepTimerCustomInfo}>
          <Text style={styles.sleepTimerCustomValue}>
            {option === "custom" ? `${customMinutes} min` : "Establecer"}
          </Text>
          <Ionicons name="chevron-forward" size={18} color="#A79AB8" />
        </View>
      </TouchableOpacity>

      <View style={styles.deletedActionDivider} />

      <View style={styles.sleepTimerFooterRow}>
        <Text style={styles.sleepTimerFooterText}>Reproducir la última hasta el final</Text>
        <TouchableOpacity
          style={[styles.switchTrack, finishTrack && styles.switchTrackActive]}
          onPress={onToggleFinishTrack}
        >
          <View style={[styles.switchThumb, finishTrack && styles.switchThumbActive]} />
        </TouchableOpacity>
      </View>
    </BottomSheet>
    <CustomSleepTimerSheet
      visible={customVisible}
      initialMinutes={customMinutes}
      onClose={() => setCustomVisible(false)}
      onSet={(minutes) => {
        onChangeCustomMinutes(minutes);
        onChangeOption("custom");
        setCustomVisible(false);
        onClose();
      }}
    />
    </>
  );
}
