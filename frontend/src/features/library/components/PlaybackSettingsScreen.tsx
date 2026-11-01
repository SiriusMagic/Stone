import Slider from "@react-native-community/slider";
import { Text, View } from "react-native";

import { styles } from "../styles";
import { ScreenSheet, SettingToggleRow } from "./shared";

type Props = {
  visible: boolean;
  playWithOthers: boolean;
  gaplessPlayback: boolean;
  crossfadeEnabled: boolean;
  crossfadeSeconds: number;
  onClose: () => void;
  onTogglePlayWithOthers: () => void;
  onToggleGapless: () => void;
  onToggleCrossfade: () => void;
  onChangeCrossfadeSeconds: (value: number) => void;
};

export function PlaybackSettingsScreen(props: Props) {
  return (
    <ScreenSheet visible={props.visible} title="Ajustes de reproduccion" onClose={props.onClose}>
      <SettingToggleRow
        icon="headset-outline"
        title="Reproduce junto con otras aplicaciones"
        value={props.playWithOthers}
        onToggle={props.onTogglePlayWithOthers}
      />
      <SettingToggleRow
        icon="pulse-outline"
        title="Reproduccion sin pausas"
        subtitle="Elimina cualquier espacio o pausa que pueda producirse entre pistas"
        value={props.gaplessPlayback}
        onToggle={props.onToggleGapless}
      />
      <SettingToggleRow
        icon="analytics-outline"
        title="Crossfade"
        subtitle="Establezca el desvanecimiento y la superposicion entre pistas"
        value={props.crossfadeEnabled}
        onToggle={props.onToggleCrossfade}
      />
      {props.crossfadeEnabled ? (
        <View style={styles.crossfadeWrap}>
          <Text style={styles.crossfadeValue}>{props.crossfadeSeconds}s</Text>
          <Slider
            style={styles.crossfadeSlider}
            minimumValue={0}
            maximumValue={12}
            step={1}
            value={props.crossfadeSeconds}
            minimumTrackTintColor="#A79AB8"
            maximumTrackTintColor="#5A5365"
            thumbTintColor="#A79AB8"
            onValueChange={props.onChangeCrossfadeSeconds}
          />
        </View>
      ) : null}
    </ScreenSheet>
  );
}
