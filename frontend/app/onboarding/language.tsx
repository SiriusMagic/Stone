import { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Image,
  Linking,
} from "react-native";
import { useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useLanguage } from "@/src/contexts/LanguageContext";
import { LANGUAGES, LangCode } from "@/src/i18n/translations";

const PENGUIN_URL =
  "https://customer-assets.emergentagent.com/job_76e38cfc-d123-494d-b977-20c7b50770d7/artifacts/3wlz6t8o_1782653108966.jpg";

export default function LanguageScreen() {
  const router = useRouter();
  const { lang, setLang, t } = useLanguage();
  const [selected, setSelected] = useState<LangCode>(lang);

  const handleStart = async () => {
    await setLang(selected);
    router.replace("/onboarding/welcome");
  };

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <ScrollView
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.logoWrap}>
          <Image source={{ uri: PENGUIN_URL }} style={styles.logo} />
        </View>

        <Text style={styles.title} testID="language-screen-title">
          {t("choose_language")}
        </Text>

        <View style={styles.card} testID="language-list">
          {LANGUAGES.map((l, idx) => {
            const active = selected === l.code;
            return (
              <TouchableOpacity
                key={l.code}
                activeOpacity={0.8}
                style={[styles.row, idx > 0 && styles.rowDivider]}
                onPress={() => setSelected(l.code)}
                testID={`language-option-${l.code}`}
              >
                <Ionicons
                  name="globe-outline"
                  size={22}
                  color={active ? "#F5C518" : "#8B8B92"}
                  style={{ marginRight: 14 }}
                />
                <Text style={[styles.rowLabel, active && styles.rowLabelActive]}>
                  {l.label}
                </Text>
                {active && (
                  <Ionicons name="checkmark" size={22} color="#F5C518" />
                )}
              </TouchableOpacity>
            );
          })}
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity
          style={styles.cta}
          activeOpacity={0.9}
          onPress={handleStart}
          testID="language-start-button"
        >
          <Text style={styles.ctaText}>{t("start")}</Text>
        </TouchableOpacity>
        <Text style={styles.terms}>
          {t("terms_notice")}{" "}
          <Text
            style={styles.link}
            onPress={() => Linking.openURL("https://example.com/terms")}
          >
            {t("terms")}
          </Text>{" "}
          {t("and")}{" "}
          <Text
            style={styles.link}
            onPress={() => Linking.openURL("https://example.com/privacy")}
          >
            {t("privacy")}
          </Text>
          .
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: "#0A0A0E" },
  content: {
    paddingHorizontal: 24,
    paddingTop: 16,
    paddingBottom: 24,
    alignItems: "stretch",
  },
  logoWrap: { alignItems: "center", marginTop: 24, marginBottom: 24 },
  logo: { width: 120, height: 120, borderRadius: 24 },
  title: {
    fontSize: 28,
    fontWeight: "800",
    color: "#FFFFFF",
    textAlign: "center",
    marginBottom: 28,
    letterSpacing: 0.2,
  },
  card: {
    backgroundColor: "#15151B",
    borderRadius: 18,
    paddingHorizontal: 6,
    borderWidth: 1,
    borderColor: "#22222B",
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 18,
    paddingHorizontal: 14,
  },
  rowDivider: {
    borderTopWidth: 1,
    borderTopColor: "#22222B",
  },
  rowLabel: {
    flex: 1,
    color: "#FFFFFF",
    fontSize: 18,
    fontWeight: "600",
  },
  rowLabelActive: { color: "#FFFFFF" },
  footer: {
    paddingHorizontal: 24,
    paddingBottom: 12,
  },
  cta: {
    backgroundColor: "#F5C518",
    paddingVertical: 18,
    borderRadius: 999,
    alignItems: "center",
    shadowColor: "#F5C518",
    shadowOpacity: 0.35,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  ctaText: {
    fontSize: 18,
    fontWeight: "800",
    color: "#0A0A0E",
    letterSpacing: 0.3,
  },
  terms: {
    marginTop: 14,
    textAlign: "center",
    color: "#8B8B92",
    fontSize: 12,
    lineHeight: 18,
  },
  link: {
    color: "#F5C518",
    textDecorationLine: "underline",
  },
});
