import { memo, useEffect, useRef, useState } from "react";
import { Animated, Easing, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { LyricLine } from "@/src/data/tracks";

export function findActiveLyricIndex(lines: LyricLine[], positionMs: number) {
  let low = 0;
  let high = lines.length - 1;
  let result = -1;
  while (low <= high) {
    const middle = (low + high) >> 1;
    if (lines[middle].timeMs <= positionMs) { result = middle; low = middle + 1; }
    else high = middle - 1;
  }
  return result;
}

const TIME_TAG = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/g;
const META_TAG = /^\[(?:ar|al|ti|by|offset|re|ve):.*\]$/i;

export function parseEmbeddedLrc(lines: string[] = []) {
  const synced: LyricLine[] = [];
  const plain: string[] = [];
  for (const source of lines.flatMap((block) => block.split(/\r?\n/))) {
    if (!source || META_TAG.test(source.trim())) continue;
    const text = source.replace(TIME_TAG, "").trim();
    if (!text) continue;
    const tags = [...source.matchAll(TIME_TAG)];
    if (!tags.length) plain.push(text);
    for (const tag of tags) {
      const fraction = (tag[3] || "0").padEnd(3, "0").slice(0, 3);
      synced.push({ timeMs: Number(tag[1]) * 60000 + Number(tag[2]) * 1000 + Number(fraction), text });
    }
  }
  synced.sort((a, b) => a.timeMs - b.timeMs);
  return { synced, plain: synced.length ? synced.map((line) => line.text) : plain };
}

type Props = { compact?: boolean; hasUnsyncedLyrics?: boolean; lines: LyricLine[]; positionMs: number; onPress: () => void };

export const LyricsChip = memo(function LyricsChip({ compact, hasUnsyncedLyrics, lines, positionMs, onPress }: Props) {
  const activeIndex = findActiveLyricIndex(lines, positionMs);
  const fallback = !lines.length && hasUnsyncedLyrics ? "Ver letra" : "Letras";
  const target = activeIndex >= 0 ? lines[activeIndex]?.text.trim() || fallback : fallback;
  const displayedRef = useRef("Letras");
  const [displayed, setDisplayed] = useState("Letras");
  const [outgoing, setOutgoing] = useState<string | null>(null);
  const incomingProgress = useRef(new Animated.Value(1)).current;
  const outgoingProgress = useRef(new Animated.Value(1)).current;
  const transitionId = useRef(0);

  useEffect(() => {
    if (target === displayedRef.current) return;
    const id = ++transitionId.current;
    incomingProgress.stopAnimation();
    outgoingProgress.stopAnimation();
    setOutgoing(displayedRef.current);
    displayedRef.current = target;
    setDisplayed(target);
    incomingProgress.setValue(0);
    outgoingProgress.setValue(0);
    requestAnimationFrame(() => Animated.parallel([
      Animated.timing(incomingProgress, { toValue: 1, duration: 230, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
      Animated.timing(outgoingProgress, { toValue: 1, duration: 190, easing: Easing.in(Easing.cubic), useNativeDriver: true }),
    ]).start(() => { if (transitionId.current === id) setOutgoing(null); }));
  }, [incomingProgress, outgoingProgress, target]);

  return (
    <TouchableOpacity
      style={[styles.chip, compact && styles.chipCompact]}
      onPress={onPress}
      activeOpacity={0.86}
      testID="player-lyrics-button"
    >
      <View style={styles.viewport}>
        {outgoing ? <LyricLayer text={outgoing} compact={compact} style={{
          opacity: outgoingProgress.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }),
          transform: [{ translateY: outgoingProgress.interpolate({ inputRange: [0, 1], outputRange: [0, -18] }) }],
        }} /> : null}
        <LyricLayer text={displayed} compact={compact} style={{
          opacity: incomingProgress,
          transform: [{ translateY: incomingProgress.interpolate({ inputRange: [0, 1], outputRange: [18, 0] }) }],
        }} />
      </View>
    </TouchableOpacity>
  );
});

function LyricLayer({ text, compact, style }: { text: string; compact?: boolean; style: object }) {
  const fallback = text === "Letras" || text === "Ver letra";
  return (
    <Animated.View style={[styles.layer, style]}>
      {fallback ? <Ionicons name="chatbox-ellipses-outline" size={compact ? 21 : 25} color="#E8E6EE" /> : null}
      <Text style={[styles.text, fallback && styles.fallbackText, compact && styles.textCompact]} numberOfLines={1} ellipsizeMode="tail">
        {text}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  chip: { flex: 1, minWidth: 120, maxWidth: 280, height: 66, marginHorizontal: 12, paddingHorizontal: 20, borderRadius: 33, backgroundColor: "#4A292B", overflow: "hidden" },
  chipCompact: { maxWidth: 210, height: 46, marginHorizontal: 8, paddingHorizontal: 12, borderRadius: 23 },
  viewport: { flex: 1, overflow: "hidden" },
  layer: { ...StyleSheet.absoluteFillObject, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8 },
  text: { flexShrink: 1, color: "#F5C518", fontSize: 15, fontWeight: "600", textAlign: "center" },
  fallbackText: { color: "#E8E6EE", fontSize: 24, fontWeight: "700" },
  textCompact: { fontSize: 13 },
});
