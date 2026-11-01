import { Stack } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { useEffect } from "react";
import { LogBox, StatusBar } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { GestureHandlerRootView } from "react-native-gesture-handler";

import { useIconFonts } from "@/src/hooks/use-icon-fonts";
import { LanguageProvider } from "@/src/contexts/LanguageContext";
import { AudioProvider } from "@/src/contexts/AudioContext";
import { PlaybackToolsProvider } from "@/src/contexts/PlaybackToolsContext";

LogBox.ignoreAllLogs(true);

// Keep the native splash visible from cold start until icon fonts register.
SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const [loaded, error] = useIconFonts();

  useEffect(() => {
    if (loaded || error) {
      SplashScreen.hideAsync();
    }
  }, [loaded, error]);

  if (!loaded && !error) return null;

  return (
    <GestureHandlerRootView style={{ flex: 1, backgroundColor: "#0A0A0E" }}>
      <SafeAreaProvider>
        <LanguageProvider>
          <AudioProvider>
            <PlaybackToolsProvider>
              <StatusBar barStyle="light-content" backgroundColor="#0A0A0E" />
              <Stack
              screenOptions={{
                headerShown: false,
                contentStyle: { backgroundColor: "#0A0A0E" },
                animation: "fade",
              }}
            >
              <Stack.Screen name="index" />
              <Stack.Screen name="onboarding/language" />
              <Stack.Screen name="onboarding/welcome" />
              <Stack.Screen name="library" />
              <Stack.Screen
                name="player"
                options={{ presentation: "modal", animation: "slide_from_bottom" }}
              />
              </Stack>
            </PlaybackToolsProvider>
          </AudioProvider>
        </LanguageProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
