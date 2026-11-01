import { StyleSheet } from "react-native";

import { coreStyles } from "./styles/core";
import { deletedStyles } from "./styles/deleted";
import { downloadStyles } from "./styles/download";
import { hiddenStyles } from "./styles/hidden";
import { mediaStyles } from "./styles/media";
import { playbackStyles } from "./styles/playback";

export const styles = StyleSheet.create<any>({
  ...coreStyles,
  ...playbackStyles,
  ...deletedStyles,
  ...downloadStyles,
  ...hiddenStyles,
  ...mediaStyles,
});
