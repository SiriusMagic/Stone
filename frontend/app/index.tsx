import { useEffect } from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import { storage } from "@/src/utils/storage";

export default function Index() {
  const router = useRouter();

  useEffect(() => {
    (async () => {
      const onboarded = await storage.getItem<boolean>("@app/onboarded", false);
      if (onboarded) {
        router.replace("/library");
      } else {
        router.replace("/onboarding/language");
      }
    })();
  }, [router]);

  return (
    <View style={styles.container} testID="boot-screen">
      <ActivityIndicator color="#F5C518" size="large" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0A0A0E",
    alignItems: "center",
    justifyContent: "center",
  },
});
