import { View, Text, StyleSheet, TouchableOpacity, Image } from "react-native";
import { useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { storage } from "@/src/utils/storage";
import { useLanguage } from "@/src/contexts/LanguageContext";

const PENGUIN_URL =
  "https://customer-assets.emergentagent.com/job_76e38cfc-d123-494d-b977-20c7b50770d7/artifacts/3wlz6t8o_1782653108966.jpg";

// Decorative tilted floating tiles to mimic the reference screenshot.
function FloatingTile({
  style,
  icon,
}: {
  style: any;
  icon: keyof typeof Ionicons.glyphMap;
}) {
  return (
    <View style={[styles.tile, style]} pointerEvents="none">
      <Ionicons name={icon} size={26} color="#2A2A35" />
    </View>
  );
}

export default function WelcomeScreen() {
  const router = useRouter();
  const { t } = useLanguage();

  const handleContinue = async () => {
    await storage.setItem("@app/onboarded", true);
    router.replace("/library");
  };

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      {/* Decorative tiles */}
      <FloatingTile
        style={{ top: 60, left: -20, transform: [{ rotate: "-15deg" }] }}
        icon="musical-note"
      />
      <FloatingTile
        style={{ top: 80, right: -30, transform: [{ rotate: "20deg" }] }}
        icon="disc"
      />
      <FloatingTile
        style={{ bottom: 220, left: -25, transform: [{ rotate: "12deg" }] }}
        icon="pulse"
      />
      <FloatingTile
        style={{ bottom: 200, right: -20, transform: [{ rotate: "-18deg" }] }}
        icon="play"
      />

      <View style={styles.center}>
        <Image source={{ uri: PENGUIN_URL }} style={styles.logo} />
        <Text style={styles.title}>
          {t("onboarding_title_1")}{" "}
          <Text style={styles.titleAccent}>{t("onboarding_title_2")}</Text>
        </Text>
        <Text style={styles.subtitle}>
          {t("onboarding_subtitle_a")}
          <Text style={styles.subtitleAccent}>{t("onboarding_subtitle_b")}</Text>
          {t("onboarding_subtitle_c")}
        </Text>
      </View>

      <View style={styles.footer}>
        <TouchableOpacity
          style={styles.cta}
          activeOpacity={0.9}
          onPress={handleContinue}
          testID="welcome-continue-button"
        >
          <Ionicons name="musical-notes" size={20} color="#0A0A0E" />
          <Text style={styles.ctaText}>{t("allow_access")}</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: "#0A0A0E" },
  center: { flex: 1, alignItems: "center", justifyContent: "center", paddingHorizontal: 32 },
  logo: { width: 140, height: 140, borderRadius: 28, marginBottom: 28 },
  title: {
    color: "#FFFFFF",
    fontSize: 34,
    fontWeight: "800",
    textAlign: "center",
    letterSpacing: 0.3,
  },
  titleAccent: { color: "#F5C518" },
  subtitle: {
    marginTop: 14,
    color: "#A0A0A5",
    fontSize: 17,
    textAlign: "center",
    lineHeight: 26,
    fontWeight: "500",
  },
  subtitleAccent: { color: "#F5C518", fontWeight: "700" },
  footer: { paddingHorizontal: 24, paddingBottom: 16 },
  cta: {
    backgroundColor: "#F5C518",
    paddingVertical: 18,
    borderRadius: 999,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 10,
    shadowColor: "#F5C518",
    shadowOpacity: 0.35,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  ctaText: { fontSize: 18, fontWeight: "800", color: "#0A0A0E", letterSpacing: 0.3 },
  tile: {
    position: "absolute",
    width: 100,
    height: 100,
    borderRadius: 18,
    backgroundColor: "#15151B",
    borderWidth: 1,
    borderColor: "#1F1F27",
    alignItems: "center",
    justifyContent: "center",
    opacity: 0.7,
  },
});
