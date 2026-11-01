import { Text, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { LANGUAGES, LangCode } from "@/src/i18n/translations";

import { styles } from "../styles";
import { BottomSheet } from "./shared";

export function LanguageSheet({
  visible,
  title,
  currentLang,
  onClose,
  onSelectLanguage,
}: {
  visible: boolean;
  title: string;
  currentLang: string;
  onClose: () => void;
  onSelectLanguage: (code: LangCode) => Promise<void> | void;
}) {
  return (
    <BottomSheet visible={visible} title={title} onClose={onClose}>
      {LANGUAGES.map((language) => (
        <TouchableOpacity
          key={language.code}
          style={styles.sheetRow}
          onPress={() => onSelectLanguage(language.code as LangCode)}
          testID={`lang-switch-${language.code}`}
        >
          <Ionicons name="globe-outline" size={20} color="#F5C518" />
          <Text style={styles.sheetRowText}>{language.label}</Text>
          {currentLang === language.code ? (
            <Ionicons name="checkmark" size={20} color="#F5C518" />
          ) : null}
        </TouchableOpacity>
      ))}
    </BottomSheet>
  );
}
