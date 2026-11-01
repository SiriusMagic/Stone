import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Pressable,
  Image,
  useWindowDimensions,
  Modal,
  Platform,
  ScrollView,
  TextInput,
  Linking,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import Slider from "@react-native-community/slider";
import { useAudio, useAudioProgress } from "@/src/contexts/AudioContext";
import { usePlaybackTools } from "@/src/contexts/PlaybackToolsContext";
import { useLanguage } from "@/src/contexts/LanguageContext";
import { formatMs, LyricLine } from "@/src/data/tracks";
import { Artwork } from "@/src/components/Artwork";
import { QueueSheet } from "@/src/features/player/QueueSheet";
import { CustomSleepTimerSheet } from "@/src/features/library/components/CustomSleepTimerSheet";
import { findActiveLyricIndex, LyricsChip, parseEmbeddedLrc } from "@/src/features/player/LyricsChip";
import { LyricsSheet } from "@/src/features/player/LyricsSheet";
import { LyricsAddFlow } from "@/src/features/player/LyricsAddFlow";
import { EnergySaverScreen } from "@/src/features/player/EnergySaverScreen";
import { RingtoneEditor } from "@/src/features/player/RingtoneEditor";
import { setStoneRingtone } from "@/src/native/StoneRingtone";
import { shareStoneAudio } from "@/src/native/StoneShare";
import { parseLyricsDocument, readLocalLyrics } from "@/src/features/library/library-lyrics";
import {
  AndroidTrackMetadata,
  readAndroidTrackMetadata,
  writeAndroidTrackMetadata,
} from "@/src/features/library/android-media-metadata";

type PlayerIconName = keyof typeof Ionicons.glyphMap;
type PlayerOptionAction = "vinyl" | "equalizer" | "speed" | "tags" | "sleep" | "battery" | "ringtone" | "share" | "hide" | "delete";
type PlayerOptionItem = {
  icon: PlayerIconName;
  label: string;
  action?: PlayerOptionAction;
  divider?: boolean;
};
type EqualizerPreset = {
  icon: PlayerIconName;
  label: string;
  values: number[];
};
const EMPTY_SYNCED_LYRICS: LyricLine[] = [];

function metadataText(value?: string | null) {
  return value?.trim() || "Sin información";
}

export default function PlayerScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ open?: string }>();
  const { t } = useLanguage();
  const { height: windowHeight, width: windowWidth } = useWindowDimensions();
  const {
    current,
    queue,
    currentIndex,
    favoriteTrackIds,
    toggle,
    next,
    prev,
    seekMs,
    playTrack,
    reorderQueue,
    updateTrackLyrics,
    shuffle,
    repeat,
    playbackRate,
    setShuffleEnabled,
    setRepeatMode,
    setPlaybackRate,
    toggleFavorite,
    hideTrack,
    deleteTrack,
  } = useAudio();
  const { isPlaying, positionMs, durationMs } = useAudioProgress();
  const {
    equalizerEnabled, equalizerValues, selectedPreset, applyEqualizerPreset,
    setEqualizerBand, setEqualizerEnabled, sleepTimerOption: sleepMode,
    sleepTimerCustomMinutes, sleepTimerFinishTrack: playLastTrackUntilEnd,
    setSleepTimerCustomMinutes, setSleepTimerOption, toggleSleepTimerFinishTrack,
  } = usePlaybackTools();

  const [isDragging, setIsDragging] = useState(false);
  const isDraggingRef = useRef(false);
  const [queueVisible, setQueueVisible] = useState(false);
  const closeQueue = useCallback(() => setQueueVisible(false), []);
  const [optionsVisible, setOptionsVisible] = useState(false);
  const [equalizerVisible, setEqualizerVisible] = useState(false);
  const [vinylVisible, setVinylVisible] = useState(false);
  const [speedVisible, setSpeedVisible] = useState(false);
  const [tagEditorVisible, setTagEditorVisible] = useState(false);
  const [lyricsVisible, setLyricsVisible] = useState(false);
  const [lyricsAddVisible, setLyricsAddVisible] = useState(false);
  const [sleepVisible, setSleepVisible] = useState(false);
  const [customSleepVisible, setCustomSleepVisible] = useState(false);
  const [resolvedSyncedLyrics, setResolvedSyncedLyrics] = useState<LyricLine[]>(EMPTY_SYNCED_LYRICS);
  const [hideConfirmVisible, setHideConfirmVisible] = useState(false);
  const [deleteConfirmVisible, setDeleteConfirmVisible] = useState(false);
  const [batterySaverEnabled, setBatterySaverEnabled] = useState(false);
  const [ringtoneVisible, setRingtoneVisible] = useState(false);
  const exitBatterySaver = useCallback(() => setBatterySaverEnabled(false), []);
  const [previewMs, setPreviewMs] = useState(0);
  const [trackEdits, setTrackEdits] = useState<Record<string, { title: string; artist: string; album: string }>>({});
  const [tagMetadata, setTagMetadata] = useState<AndroidTrackMetadata | null>(null);
  const [draftTitle, setDraftTitle] = useState("");
  const [draftArtist, setDraftArtist] = useState("");
  const [draftAlbum, setDraftAlbum] = useState("");
  const [draftGenre, setDraftGenre] = useState("");
  const [draftYear, setDraftYear] = useState("");
  const [draftTrackNumber, setDraftTrackNumber] = useState("");
  const equalizerListRef = useRef<ScrollView | null>(null);
  const optionsListRef = useRef<ScrollView | null>(null);
  const vinylLyricsRef = useRef<ScrollView | null>(null);
  const lyricsPanelRef = useRef<ScrollView | null>(null);
  const scrollOffsetRef = useRef(0);
  const dur = durationMs || current?.durationMs || 0;
  const pos = isDragging ? previewMs : positionMs;
  const compact = Platform.OS === "web" || windowHeight < 760;
  const isLandscape = windowWidth > windowHeight;
  const coverSize = Math.min(windowWidth - (compact ? 96 : 72), Math.max(150, windowHeight * (compact ? 0.22 : 0.34)));
  const embeddedLyrics = useMemo(() => parseEmbeddedLrc(current?.lyrics), [current?.lyrics]);
  const syncedLyrics = resolvedSyncedLyrics.length ? resolvedSyncedLyrics : embeddedLyrics.synced;
  const lyrics = useMemo(
    () => syncedLyrics.length ? syncedLyrics.map((line) => line.text) : embeddedLyrics.plain,
    [embeddedLyrics.plain, syncedLyrics],
  );
  const currentEdit = current ? trackEdits[current.id] : null;
  const displayTitle = currentEdit?.title || current?.title || "";
  const displayArtist = currentEdit?.artist || current?.artist || "";
  const displayAlbum = currentEdit?.album || current?.album || "";
  const isFavorite = current ? favoriteTrackIds.includes(current.id) : false;
  const activeLyricIndex = findActiveLyricIndex(syncedLyrics, Math.max(pos, 0));

  useEffect(() => {
    const stored = current?.syncedLyrics ?? EMPTY_SYNCED_LYRICS;
    setResolvedSyncedLyrics(stored);
    const uri = current?.localUri || current?.url;
    if (!current || stored.length || !uri) return;
    let active = true;
    readLocalLyrics(uri).then((document) => {
      if (active && document.syncedLyrics.length) setResolvedSyncedLyrics(document.syncedLyrics);
    }).catch(() => {});
    return () => { active = false; };
  }, [current?.id, current?.localUri, current?.syncedLyrics, current?.url]);

  useEffect(() => {
    if (Platform.OS === "android") setVinylVisible(isLandscape);
  }, [isLandscape]);

  useEffect(() => {
    if (!vinylVisible || !isLandscape || activeLyricIndex < 0 || !vinylLyricsRef.current) return;
    vinylLyricsRef.current.scrollTo({
      y: Math.max(0, activeLyricIndex * 56 - 84),
      animated: true,
    });
  }, [activeLyricIndex, isLandscape, vinylVisible]);

  useEffect(() => {
    if (!lyricsVisible || activeLyricIndex < 0 || !lyricsPanelRef.current) return;
    lyricsPanelRef.current.scrollTo({ y: Math.max(0, activeLyricIndex * 64 - 150), animated: true });
  }, [activeLyricIndex, lyricsVisible]);

  useEffect(() => {
    if (!batterySaverEnabled) return;
    setVinylVisible(false);
  }, [batterySaverEnabled]);

  useEffect(() => {
    if (params.open === "options") setOptionsVisible(true);
    if (params.open === "equalizer") setEqualizerVisible(true);
  }, [params.open]);

  const openLyricsSearch = useCallback(() => {
    const query = encodeURIComponent(`${displayTitle} ${displayArtist} lyrics`);
    const url = `https://www.google.com/search?q=${query}`;
    if (Platform.OS === "web") {
      window.open(url, "_blank", "noopener,noreferrer");
      return;
    }
    Linking.openURL(url);
  }, [displayArtist, displayTitle]);

  const saveLyrics = useCallback((value: string) => {
    if (!current) return;
    const document = parseLyricsDocument(value);
    updateTrackLyrics(current.id, document.lyrics, document.syncedLyrics);
    setResolvedSyncedLyrics(document.syncedLyrics);
  }, [current, updateTrackLyrics]);

  const scrollNodeBy = (scrollNode: any, delta: number) => {
    if (!scrollNode) return;
    if (scrollNode?.scrollTo) {
      const nextOffset = Math.max(0, scrollOffsetRef.current + delta);
      scrollNode.scrollTo({ y: nextOffset, animated: true });
      scrollOffsetRef.current = nextOffset;
      return;
    }
    if (scrollNode?.scrollToOffset) {
      const currentOffset = scrollNode?._listRef?._scrollMetrics?.offset ?? 0;
      const nextOffset = Math.max(0, currentOffset + delta);
      scrollNode.scrollToOffset({ offset: nextOffset, animated: true });
      scrollOffsetRef.current = nextOffset;
    }
  };

  const scrollNodeToStart = (scrollNode: any) => {
    if (!scrollNode) return;
    if (scrollNode?.scrollTo) {
      scrollNode.scrollTo({ y: 0, animated: true });
      scrollOffsetRef.current = 0;
      return;
    }
    scrollNode?.scrollToOffset?.({ offset: 0, animated: true });
    scrollOffsetRef.current = 0;
  };

  const scrollNodeToEnd = (scrollNode: any) => {
    if (!scrollNode) return;
    if (scrollNode?.scrollToEnd) {
      scrollNode.scrollToEnd({ animated: true });
      return;
    }
    const currentOffset = scrollNode?._listRef?._scrollMetrics?.offset ?? 0;
    scrollNode?.scrollToOffset?.({ offset: currentOffset + 9999, animated: true });
  };

  const focusScrollNode = useCallback((scrollNode: any) => {
    if (Platform.OS !== "web" || !scrollNode) return;
    const targetNode = scrollNode?.getScrollableNode?.() ?? scrollNode;
    targetNode?.setAttribute?.("tabindex", "0");
    targetNode?.focus?.();
  }, []);

  useEffect(() => {
    if (Platform.OS !== "web") return;

    const getActiveScrollNode = () => {
      if (equalizerVisible) return equalizerListRef.current;
      if (optionsVisible) return optionsListRef.current;
      if (vinylVisible && isLandscape && lyrics.length) return vinylLyricsRef.current;
      return null;
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      const scrollNode = getActiveScrollNode();
      if (!scrollNode) return;

      const key = event.key;
      const code = event.code;

      if (key === "ArrowDown") {
        event.preventDefault();
        scrollNodeBy(scrollNode, 72);
      }
      if (key === "ArrowUp") {
        event.preventDefault();
        scrollNodeBy(scrollNode, -72);
      }
      if (key === "PageDown" || key === "Next" || code === "PageDown") {
        event.preventDefault();
        scrollNodeBy(scrollNode, 220);
      }
      if (key === "PageUp" || key === "Prior" || code === "PageUp") {
        event.preventDefault();
        scrollNodeBy(scrollNode, -220);
      }
      if (key === "Home") {
        event.preventDefault();
        scrollNodeToStart(scrollNode);
      }
      if (key === "End") {
        event.preventDefault();
        scrollNodeToEnd(scrollNode);
      }
    };

    window.addEventListener("keydown", handleKeyDown, true);
    return () => window.removeEventListener("keydown", handleKeyDown, true);
  }, [equalizerVisible, isLandscape, lyrics.length, optionsVisible, vinylVisible]);

  useEffect(() => {
    if (Platform.OS !== "web") return;

    if (optionsVisible) {
      setTimeout(() => focusScrollNode(optionsListRef.current), 0);
      return;
    }

    if (equalizerVisible) {
      setTimeout(() => focusScrollNode(equalizerListRef.current), 0);
      return;
    }

  }, [equalizerVisible, focusScrollNode, optionsVisible]);

  if (!current) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.emptyWrap}>
          <Ionicons name="musical-notes-outline" size={56} color="#3A3A45" />
          <Text style={styles.emptyText}>{t("no_songs")}</Text>
          <TouchableOpacity style={styles.closeBtn} onPress={() => router.back()}>
            <Text style={styles.closeBtnText}>{t("cancel")}</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const optionItems: PlayerOptionItem[] = [
    { icon: "options-outline", label: "Ecualizador", action: "equalizer" as const },
    { icon: "list-outline", label: "Agregar a la lista de reproduccion" },
    { icon: "share-social-outline", label: "Compartir", action: "share" as const },
    { icon: "speedometer-outline", label: "Velocidad", action: "speed" as const, divider: true },
    { icon: "notifications-outline", label: "Establecer como tono de llamada", action: "ringtone" as const },
    { icon: "timer-outline", label: "Temporizador para dormir", action: "sleep" as const },
    { icon: batterySaverEnabled ? "battery-half-outline" : "battery-charging-outline", label: "Modo de ahorro de energia", action: "battery" as const },
    { icon: "eye-off-outline", label: "Ocultar", action: "hide" as const, divider: true },
    { icon: "trash-outline", label: "Borrar", action: "delete" as const },
  ] as const;

  const toolItems: PlayerOptionItem[] = [
    { icon: "pricetag-outline", label: "Editor de etiquetas", action: "tags" as const },
  ];

  const equalizerBands = ["31", "63", "125", "250", "500", "1K", "2K", "4K", "8K", "16K"];
  const equalizerPresets: EqualizerPreset[] = [
    { icon: "analytics-outline", label: "Personalizado", values: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] },
    { icon: "radio-outline", label: "Aumento de graves", values: [7, 6, 5, 4, 2, 0, -1, -2, -3, -4] },
    { icon: "mic-outline", label: "Aumento de voz", values: [-2, -1, 1, 3, 5, 6, 5, 3, 1, -1] },
    { icon: "volume-high-outline", label: "Aumento de agudos", values: [-4, -3, -2, -1, 0, 2, 4, 5, 6, 7] },
    { icon: "happy-outline", label: "Normal", values: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0] },
    { icon: "musical-notes-outline", label: "Pop", values: [-1, 2, 4, 5, 3, 0, -1, 1, 3, 4] },
  ];

  const handleBandChange = (index: number, value: number) => {
    setEqualizerBand(index, value);
  };

  const openTagEditor = async () => {
    if (!current) return;
    const metadata = await readAndroidTrackMetadata(current.localUri || current.url);
    setTagMetadata(metadata);
    setDraftTitle(metadata?.title || "");
    setDraftArtist(metadata?.artist || "");
    setDraftAlbum(metadata?.album || "");
    setDraftGenre(metadata?.genre || "");
    setDraftYear(metadata?.year || "");
    setDraftTrackNumber(metadata?.trackNumber || "");
    setTagEditorVisible(true);
  };

  const handleOptionPress = (action?: PlayerOptionAction) => {
    setOptionsVisible(false);
    const openAfterClose = (open: () => void) => setTimeout(open, 180);
    if (action === "vinyl") {
      openAfterClose(() => setVinylVisible(true));
      return;
    }
    if (action === "equalizer") {
      openAfterClose(() => setEqualizerVisible(true));
      return;
    }
    if (action === "speed") {
      openAfterClose(() => setSpeedVisible(true));
      return;
    }
    if (action === "tags") {
      void openTagEditor();
      return;
    }
    if (action === "sleep") {
      openAfterClose(() => setSleepVisible(true));
      return;
    }
    if (action === "battery") {
      setBatterySaverEnabled((value) => !value);
      return;
    }
    if (action === "ringtone") {
      openAfterClose(() => setRingtoneVisible(true));
      return;
    }
    if (action === "share") {
      openAfterClose(() => {
        void shareStoneAudio(current.localUri || current.url, displayTitle).catch((error) => {
          Alert.alert("No se pudo compartir", error instanceof Error ? error.message : "Comprueba que la canción esté descargada.");
        });
      });
      return;
    }
    if (action === "hide") {
      openAfterClose(() => setHideConfirmVisible(true));
      return;
    }
    if (action === "delete") {
      openAfterClose(() => setDeleteConfirmVisible(true));
    }
  };

  const confirmHideTrack = () => {
    if (!current) return;
    hideTrack(current);
    setHideConfirmVisible(false);
    router.replace("/library");
  };

  const confirmDeleteTrack = () => {
    if (!current) return;
    deleteTrack(current);
    setDeleteConfirmVisible(false);
    router.replace("/library");
  };

  const saveTagEdits = async () => {
    if (!current) return;
    const metadata = await writeAndroidTrackMetadata(current.localUri || current.url, {
      title: draftTitle,
      artist: draftArtist,
      album: draftAlbum,
      genre: draftGenre,
      year: draftYear,
      trackNumber: draftTrackNumber,
    });
    if (!metadata) return;
    setTagMetadata(metadata);
    setTrackEdits((currentEdits) => ({
      ...currentEdits,
      [current.id]: {
        title: metadata.title || current.title,
        artist: metadata.artist || current.artist,
        album: metadata.album || current.album,
      },
    }));
    setTagEditorVisible(false);
  };

  const speedOptions = [0.5, 0.75, 1, 1.25, 1.5, 2];
  const sleepOptions = [
    { key: "off", label: "Apagar" },
    { key: "10", label: "10 minutos mas tarde" },
    { key: "20", label: "20 minutos mas tarde" },
    { key: "30", label: "30 minutos mas tarde" },
    { key: "60", label: "60 minutos mas tarde" },
  ] as const;

  const applySleepMode = (mode: "off" | "10" | "20" | "30" | "60" | "custom") => {
    setSleepTimerOption(mode);
  };

  if (ringtoneVisible) {
    return (
      <RingtoneEditor
        title={displayTitle}
        durationMs={dur}
        positionMs={Math.max(pos, 0)}
        isPlaying={isPlaying}
        onBack={() => setRingtoneVisible(false)}
        onToggle={toggle}
        onSeek={seekMs}
        onConfirm={(startMs) => setStoneRingtone(current.localUri || current.url, displayTitle, startMs).then(() => undefined)}
      />
    );
  }

  if (batterySaverEnabled) {
    return (
      <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
        <EnergySaverScreen
          cover={current.cover}
          title={displayTitle}
          artist={displayArtist}
          progress={dur > 0 ? Math.min(100, (Math.max(pos, 0) / dur) * 100) : 0}
          isPlaying={isPlaying}
          onToggle={toggle}
          onNext={next}
          onPrevious={prev}
          onExit={exitBatterySaver}
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <View style={styles.screen}>
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() => router.back()}
            style={styles.hBtn}
            testID="player-close-button"
          >
            <Ionicons name="chevron-down" size={28} color="#FFFFFF" />
          </TouchableOpacity>
          <View style={{ alignItems: "center" }}>
            <Text style={styles.hLabel}>{t("now_playing")}</Text>
            <Text style={styles.hAlbum} numberOfLines={1}>
              {displayAlbum}
            </Text>
          </View>
          <View style={styles.hBtn} />
        </View>

        <View style={styles.coverWrap}>
          <Artwork uri={current.cover} style={[styles.cover, { width: coverSize, height: coverSize }]} iconSize={42} />
        </View>

        <View style={[styles.infoRow, compact && styles.infoRowCompact]}>
          <View style={styles.info}>
            <Text style={[styles.title, compact && styles.titleCompact]} numberOfLines={1}>
              {displayTitle}
            </Text>
            <Text style={[styles.artist, compact && styles.artistCompact]} numberOfLines={1}>
              {displayArtist}
            </Text>
          </View>
          <View style={styles.infoActions}>
            <TouchableOpacity
              onPress={() => current && toggleFavorite(current.id)}
              style={styles.iconBtn}
              testID="player-like-button"
            >
              <Ionicons
                name={isFavorite ? "heart" : "heart-outline"}
                size={28}
                color={isFavorite ? "#F5C518" : "#D7D7DB"}
              />
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => setOptionsVisible(true)}
              style={styles.iconBtn}
              testID="player-options-button"
            >
              <Ionicons name="ellipsis-vertical" size={24} color="#D7D7DB" />
            </TouchableOpacity>
          </View>
        </View>

        <View style={[styles.progressWrap, compact && styles.progressWrapCompact]}>
          <View style={styles.progressBg}>
            <Slider
              style={styles.slider}
              minimumValue={0}
              maximumValue={Math.max(dur, 1)}
              value={pos}
              minimumTrackTintColor="#F5C518"
              maximumTrackTintColor="#2A2A35"
              thumbTintColor="#F5C518"
              onSlidingStart={() => {
                isDraggingRef.current = true;
                setPreviewMs(positionMs);
                setIsDragging(true);
              }}
              onValueChange={(value) => {
                if (!isDraggingRef.current) {
                  isDraggingRef.current = true;
                  setIsDragging(true);
                }
                setPreviewMs(value);
              }}
              onSlidingComplete={async (value) => {
                setPreviewMs(value);
                try {
                  await seekMs(value);
                } finally {
                  isDraggingRef.current = false;
                  setIsDragging(false);
                }
              }}
            />
          </View>
          <View style={styles.times}>
            <Text style={styles.timeText}>{formatMs(pos)}</Text>
            <Text style={styles.timeText}>{formatMs(dur)}</Text>
          </View>
        </View>

        <View style={[styles.controls, compact && styles.controlsCompact]}>
          <TouchableOpacity
            onPress={prev}
            style={[styles.transportBtn, compact && styles.transportBtnCompact]}
            testID="player-prev-button"
          >
            <Ionicons name="play-skip-back" size={compact ? 56 : 72} color="#E8E6EE" />
          </TouchableOpacity>
          <TouchableOpacity
            onPress={toggle}
            style={[styles.transportBtn, compact && styles.transportBtnCompact]}
            testID="player-play-button"
          >
            <Ionicons
              name={isPlaying ? "pause" : "play"}
              size={compact ? 62 : 82}
              color="#E8E6EE"
            />
          </TouchableOpacity>
          <TouchableOpacity
            onPress={next}
            style={[styles.transportBtn, compact && styles.transportBtnCompact]}
            testID="player-next-button"
          >
            <Ionicons name="play-skip-forward" size={compact ? 56 : 72} color="#E8E6EE" />
          </TouchableOpacity>
        </View>

        <View style={[styles.utilityRow, compact && styles.utilityRowCompact]}>
          <View style={styles.utilitySideGroup}>
            <TouchableOpacity
              onPress={() => setEqualizerVisible(true)}
              style={[styles.utilityIconBtn, compact && styles.utilityIconBtnCompact]}
              testID="player-eq-button"
            >
              <Ionicons name="options-outline" size={compact ? 26 : 32} color="#E8E6EE" />
            </TouchableOpacity>
          </View>

          <LyricsChip
            compact={compact}
            hasUnsyncedLyrics={(current.lyrics?.length ?? 0) > 0}
            lines={syncedLyrics}
            positionMs={Math.max(pos, 0)}
            onPress={() => setLyricsVisible(true)}
          />

          <View style={{ flexDirection: "row", alignItems: "center", gap: compact ? 4 : 8 }}>
            <TouchableOpacity
              onPress={() => setQueueVisible(true)}
              style={[styles.utilityIconBtn, compact && styles.utilityIconBtnCompact]}
              testID="player-queue-button"
            >
              <Ionicons
                name="reorder-three-outline"
                size={compact ? 32 : 42}
                color="#E8E6EE"
              />
            </TouchableOpacity>
          </View>
        </View>
      </View>

      <LyricsSheet
        visible={lyricsVisible}
        title={displayTitle}
        artist={displayArtist}
        cover={current.cover}
        progress={dur > 0 ? Math.min(100, (Math.max(pos, 0) / dur) * 100) : 0}
        isPlaying={isPlaying}
        lyrics={lyrics}
        activeIndex={activeLyricIndex}
        scrollRef={lyricsPanelRef}
        onClose={() => setLyricsVisible(false)}
        onToggle={toggle}
        onAdd={() => setLyricsAddVisible(true)}
        onSearch={openLyricsSearch}
      />
      <LyricsAddFlow visible={lyricsAddVisible} onClose={() => setLyricsAddVisible(false)} onSave={saveLyrics} />

      <Modal
        visible={vinylVisible}
        transparent={false}
        animationType="fade"
        onRequestClose={() => setVinylVisible(false)}
      >
        <SafeAreaView style={styles.vinylSafe} edges={["top", "bottom"]}>
          <View style={styles.vinylScreen}>
            <TouchableOpacity
              onPress={() => setVinylVisible(false)}
              style={styles.vinylCloseBtn}
              testID="vinyl-close-button"
            >
              <Ionicons name="arrow-back" size={28} color="#FFFFFF" />
            </TouchableOpacity>

            <View style={styles.vinylContent}>
              <View style={styles.vinylDiscWrap}>
                <View style={styles.vinylDisc}>
                  <View style={styles.vinylRingOuter} />
                  <View style={styles.vinylRingMid} />
                  <View style={styles.vinylRingInner} />
                  <Artwork uri={current.cover} style={styles.vinylCenterCover} iconSize={28} />
                </View>
                <View style={styles.vinylNeedle} />
              </View>

              <View style={styles.vinylInfoWrap}>
                <Text style={styles.vinylTitle} numberOfLines={2}>
                  {displayTitle}
                </Text>
                <Text style={styles.vinylArtist} numberOfLines={1}>
                  {displayArtist}
                </Text>

                {isLandscape ? (
                  lyrics.length ? (
                    <View style={styles.vinylLyricsWrap}>
                      <ScrollView
                        ref={vinylLyricsRef}
                        style={styles.vinylLyricsBox}
                        contentContainerStyle={styles.vinylLyricsContent}
                        showsVerticalScrollIndicator={false}
                      >
                        {lyrics.map((line, index) => (
                          <Text
                            key={`${current.id}-lyric-${index}`}
                            style={[
                              styles.vinylLyricLine,
                              index === activeLyricIndex && styles.vinylLyricLineActive,
                            ]}
                          >
                            {line}
                          </Text>
                        ))}
                      </ScrollView>
                    </View>
                  ) : (
                    <View style={styles.vinylLyricsBox}>
                      <Text style={styles.vinylLyricsEmpty}>Esta cancion aun no tiene letras disponibles.</Text>
                    </View>
                  )
                ) : null}

                <View style={styles.vinylActions}>
                  <TouchableOpacity onPress={() => current && toggleFavorite(current.id)} style={styles.vinylIconBtn}>
                    <Ionicons
                      name={isFavorite ? "heart" : "heart-outline"}
                      size={28}
                      color={isFavorite ? "#F5C518" : "#A9AFBA"}
                    />
                  </TouchableOpacity>
                  <TouchableOpacity onPress={prev} style={styles.vinylControlBtn}>
                    <Ionicons name="play-skip-back" size={48} color="#FFFFFF" />
                  </TouchableOpacity>
                  <TouchableOpacity onPress={toggle} style={styles.vinylControlBtn}>
                    <Ionicons name={isPlaying ? "pause" : "play"} size={56} color="#FFFFFF" />
                  </TouchableOpacity>
                  <TouchableOpacity onPress={next} style={styles.vinylControlBtn}>
                    <Ionicons name="play-skip-forward" size={48} color="#FFFFFF" />
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          </View>
        </SafeAreaView>
      </Modal>

      {equalizerVisible ? <Modal
        visible={equalizerVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setEqualizerVisible(false)}
      >
        <View style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setEqualizerVisible(false)} />
          <View
            style={styles.equalizerSheet}
            {...(Platform.OS === "web"
              ? {
                  onWheel: (e: any) => {
                    if (!e?.deltaY) return;
                    e.preventDefault?.();
                    scrollNodeBy(equalizerListRef.current, e.deltaY);
                  },
                }
              : {})}
          >
            <View style={styles.equalizerHandle} />

            <View style={styles.equalizerHeader}>
              <Text style={styles.equalizerTitle}>Ecualizador</Text>
              <TouchableOpacity
                onPress={() => setEqualizerEnabled(!equalizerEnabled)}
                style={[
                  styles.equalizerToggle,
                  equalizerEnabled && styles.equalizerToggleActive,
                ]}
                testID="equalizer-toggle-button"
              >
                <View
                  style={[
                    styles.equalizerToggleThumb,
                    equalizerEnabled && styles.equalizerToggleThumbActive,
                  ]}
                />
              </TouchableOpacity>
            </View>

            <View style={styles.equalizerBandsWrap}>
              {equalizerBands.map((band, index) => (
                <View key={band} style={styles.equalizerBand}>
                  <Text style={styles.equalizerBandValue}>{equalizerValues[index]}</Text>
                  <View style={styles.equalizerTrackShell}>
                    <Slider
                      style={styles.equalizerSlider}
                      minimumValue={-10}
                      maximumValue={10}
                      step={1}
                      value={equalizerValues[index]}
                      minimumTrackTintColor="#F4EDF2"
                      maximumTrackTintColor="#704C4E"
                      thumbTintColor="#F4EDF2"
                      onValueChange={(value) => handleBandChange(index, value)}
                      disabled={!equalizerEnabled}
                    />
                  </View>
                  <Text style={styles.equalizerBandLabel}>{band}</Text>
                </View>
              ))}
            </View>

            <View style={styles.scrollAreaWrap}>
              <ScrollView
                ref={equalizerListRef}
                style={styles.equalizerPresetList}
                contentContainerStyle={styles.equalizerPresetListContent}
                showsVerticalScrollIndicator={false}
                nestedScrollEnabled
              >
                  <View style={styles.equalizerPresetGrid}>
                    {equalizerPresets.map((preset) => (
                      <TouchableOpacity
                        key={preset.label}
                        style={[
                          styles.equalizerPresetCard,
                          selectedPreset === preset.label && styles.equalizerPresetCardActive,
                        ]}
                        onPress={() => applyEqualizerPreset(preset.label, preset.values)}
                      >
                        <Ionicons
                          name={preset.icon}
                          size={28}
                          color={selectedPreset === preset.label ? "#FFFFFF" : "#D7D0D5"}
                        />
                        <Text style={styles.equalizerPresetText}>{preset.label}</Text>
                      </TouchableOpacity>
                    ))}
                </View>
              </ScrollView>
            </View>
          </View>
        </View>
      </Modal> : null}

      <Modal
        visible={optionsVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setOptionsVisible(false)}
      >
        <View style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setOptionsVisible(false)} />
          <View
            style={styles.optionsSheet}
            {...(Platform.OS === "web"
              ? {
                  onWheel: (e: any) => {
                    if (!e?.deltaY) return;
                    e.preventDefault?.();
                    scrollNodeBy(optionsListRef.current, e.deltaY);
                  },
                }
              : {})}
          >
            <View style={styles.optionsHandle} />

            <View style={styles.optionsHeader}>
              <Artwork uri={current.cover} style={styles.optionsCover} iconSize={22} />
              <View style={styles.optionsInfo}>
                <Text style={styles.optionsTitle} numberOfLines={1}>
                  {displayTitle}
                </Text>
                <Text style={styles.optionsArtist} numberOfLines={2}>
                  {displayArtist}
                </Text>
              </View>
              <TouchableOpacity
                style={styles.optionsRenameBtn}
                testID="options-rename-button"
                onPress={() => handleOptionPress("tags")}
              >
                <Ionicons name="pencil-outline" size={26} color="#E8E6EE" />
              </TouchableOpacity>
            </View>

            <View style={styles.scrollAreaWrap}>
              <ScrollView
                ref={optionsListRef}
                style={styles.optionsList}
                contentContainerStyle={styles.optionsListContent}
                showsVerticalScrollIndicator
                persistentScrollbar
                scrollEnabled
                nestedScrollEnabled
                keyboardShouldPersistTaps="always"
              >
                {toolItems.map((item) => (
                  <View key={item.label}>
                    <TouchableOpacity style={styles.optionRow} onPress={() => handleOptionPress(item.action)}>
                      <Ionicons name={item.icon} size={28} color="#D7D0D5" style={styles.optionIcon} />
                      <Text style={styles.optionText}>{item.label}</Text>
                    </TouchableOpacity>
                  </View>
                ))}

                <View style={styles.optionSectionDivider} />
                {optionItems.map((item) => (
                  <View key={item.label}>
                    <TouchableOpacity style={styles.optionRow} onPress={() => handleOptionPress(item.action)}>
                      <Ionicons name={item.icon} size={28} color="#D7D0D5" style={styles.optionIcon} />
                      <Text style={styles.optionText}>{item.label}</Text>
                    </TouchableOpacity>
                    {item.divider ? <View style={styles.optionDivider} /> : null}
                  </View>
                ))}
              </ScrollView>
            </View>
          </View>
        </View>
      </Modal>

      <Modal
        visible={hideConfirmVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setHideConfirmVisible(false)}
      >
        <View style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setHideConfirmVisible(false)} />
          <View style={styles.confirmDialog}>
            <Text style={styles.confirmTitle}>¿Ocultar canción?</Text>
            <Text style={styles.confirmText}>Esta canción se moverá a “Archivos ocultos”.</Text>
            <View style={styles.confirmActions}>
              <TouchableOpacity style={styles.confirmButton} onPress={() => setHideConfirmVisible(false)}>
                <Text style={styles.confirmButtonText}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.confirmButton} onPress={confirmHideTrack}>
                <Text style={styles.confirmButtonText}>Ocultar</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <Modal
        visible={deleteConfirmVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setDeleteConfirmVisible(false)}
      >
        <View style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setDeleteConfirmVisible(false)} />
          <View style={styles.confirmDialog}>
            <Text style={styles.confirmTitle}>¿Borrar canción?</Text>
            <Text style={styles.confirmText}>
              Esta canción se moverá a “Eliminado recientemente” y se eliminará después de 30 días.
            </Text>
            <View style={styles.confirmActions}>
              <TouchableOpacity style={styles.confirmButton} onPress={() => setDeleteConfirmVisible(false)}>
                <Text style={styles.confirmButtonText}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.confirmButton} onPress={confirmDeleteTrack}>
                <Text style={[styles.confirmButtonText, styles.confirmDeleteText]}>Borrar</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {queueVisible ? (
        <QueueSheet
          queue={queue}
          currentIndex={currentIndex}
          repeat={repeat}
          shuffle={shuffle}
          onClose={closeQueue}
          onPlay={playTrack}
          onReorder={reorderQueue}
          onSetRepeat={setRepeatMode}
          onSetShuffle={setShuffleEnabled}
        />
      ) : null}

      <Modal
        visible={speedVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setSpeedVisible(false)}
      >
        <View style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setSpeedVisible(false)} />
          <View style={styles.speedSheet}>
            <View style={styles.optionsHandle} />
            <Text style={styles.speedTitle}>Velocidad</Text>
            {speedOptions.map((speed) => {
              const active = playbackRate === speed;
              return (
                <TouchableOpacity
                  key={speed}
                  style={styles.speedRow}
                  onPress={() => {
                    setPlaybackRate(speed);
                    setSpeedVisible(false);
                  }}
                >
                  <Text style={[styles.speedText, active && styles.speedTextActive]}>
                    {speed === 1 ? "1.0X (Por defecto)" : `${speed.toFixed(speed % 1 === 0 ? 1 : 2)}X`}
                  </Text>
                  {active ? <Ionicons name="checkmark" size={28} color="#F3EDF1" /> : null}
                </TouchableOpacity>
              );
            })}
          </View>
        </View>
      </Modal>

      <Modal
        visible={sleepVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setSleepVisible(false)}
      >
        <View style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setSleepVisible(false)} />
          <View style={styles.sleepSheet}>
            <View style={styles.optionsHandle} />
            <Text style={styles.sleepTitle}>Temporizador para dormir</Text>
            {sleepOptions.map((option) => {
              const active = sleepMode === option.key;
              return (
                <TouchableOpacity
                  key={option.key}
                  style={styles.sleepRow}
                  onPress={() => applySleepMode(option.key)}
                >
                  <Text style={[styles.sleepText, active && styles.sleepTextActive]}>{option.label}</Text>
                  {active ? <Ionicons name="checkmark" size={28} color="#141218" /> : null}
                </TouchableOpacity>
              );
            })}
            <TouchableOpacity style={styles.sleepCustomRow} onPress={() => setCustomSleepVisible(true)}>
              <Text style={[styles.sleepText, styles.sleepCustomLabel, sleepMode === "custom" && styles.sleepTextActive]}>Personalizado</Text>
              <Text style={styles.sleepCustomValue}>{sleepTimerCustomMinutes} min</Text>
              <Ionicons name="chevron-forward" size={20} color="#D7D0D5" />
            </TouchableOpacity>
            <View style={styles.sleepFooterRow}>
              <Text style={styles.sleepFooterText}>Reproducir la ultima hasta el final</Text>
              <TouchableOpacity
                style={[styles.sleepToggle, playLastTrackUntilEnd && styles.sleepToggleActive]}
                onPress={toggleSleepTimerFinishTrack}
              >
                <View style={[styles.sleepToggleThumb, playLastTrackUntilEnd && styles.sleepToggleThumbActive]} />
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      <CustomSleepTimerSheet
        visible={customSleepVisible}
        initialMinutes={sleepTimerCustomMinutes}
        onClose={() => setCustomSleepVisible(false)}
        onSet={(minutes) => {
          setSleepTimerCustomMinutes(minutes);
          setSleepTimerOption("custom");
          setCustomSleepVisible(false);
          setSleepVisible(false);
        }}
      />

      <Modal
        visible={tagEditorVisible}
        transparent={false}
        animationType="slide"
        onRequestClose={() => setTagEditorVisible(false)}
      >
        <SafeAreaView style={styles.tagsSafe} edges={["top", "bottom"]}>
          <ScrollView style={styles.tagsScreen} contentContainerStyle={styles.tagsContent}>
            <View style={styles.tagsHeader}>
              <TouchableOpacity onPress={() => setTagEditorVisible(false)} style={styles.tagsBackBtn}>
                <Ionicons name="arrow-back" size={28} color="#1E1B21" />
              </TouchableOpacity>
              <Text style={styles.tagsHeaderTitle}>Editar etiquetas</Text>
              <TouchableOpacity onPress={saveTagEdits} style={styles.tagsSaveBtn}>
                <Ionicons name="save-outline" size={18} color="#FFFFFF" />
                <Text style={styles.tagsSaveText}>Guardar</Text>
              </TouchableOpacity>
            </View>

            <View style={styles.tagsHintBox}>
              <Text style={styles.tagsHintTitle}>Metadatos del MP3</Text>
              <Text style={styles.tagsHintLine}>Solo etiquetas reales incrustadas en el archivo.</Text>
            </View>

            <Text style={styles.tagsFieldLabel}>Nombre</Text>
            <TextInput value={draftTitle} onChangeText={setDraftTitle} style={styles.tagsInput} placeholderTextColor="#8A8397" />

            <Text style={styles.tagsFieldLabel}>Artista</Text>
            <TextInput value={draftArtist} onChangeText={setDraftArtist} style={styles.tagsInput} placeholderTextColor="#8A8397" />

            <Text style={styles.tagsFieldLabel}>Album</Text>
            <TextInput value={draftAlbum} onChangeText={setDraftAlbum} style={styles.tagsInput} placeholderTextColor="#8A8397" />

            <Text style={styles.tagsFieldLabel}>Género</Text>
            <TextInput value={draftGenre} onChangeText={setDraftGenre} style={styles.tagsInput} placeholder="Sin información" placeholderTextColor="#8A8397" />

            <Text style={styles.tagsFieldLabel}>Año</Text>
            <TextInput value={draftYear} onChangeText={setDraftYear} style={styles.tagsInput} keyboardType="numeric" placeholder="Sin información" placeholderTextColor="#8A8397" />

            <Text style={styles.tagsFieldLabel}>Pista</Text>
            <TextInput value={draftTrackNumber} onChangeText={setDraftTrackNumber} style={styles.tagsInput} keyboardType="numeric" placeholder="Sin información" placeholderTextColor="#8A8397" />

            <Text style={styles.tagsFieldLabel}>Duración</Text>
            <Text style={styles.tagsReadonlyValue}>{tagMetadata ? formatMs(tagMetadata.durationMs) : "Sin información"}</Text>

            <Text style={styles.tagsFieldLabel}>Portada</Text>
            {tagMetadata?.artworkUri ? (
              <Image source={{ uri: tagMetadata.artworkUri }} style={styles.tagsCover} />
            ) : (
              <Text style={styles.tagsReadonlyValue}>Sin información</Text>
            )}
          </ScrollView>
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: "#0A0A0E" },
  vinylSafe: { flex: 1, backgroundColor: "#13202B" },
  vinylScreen: { flex: 1, backgroundColor: "#13202B" },
  vinylCloseBtn: {
    width: 44,
    height: 44,
    marginLeft: 16,
    marginTop: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  vinylContent: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 28,
    paddingBottom: 32,
    gap: 18,
  },
  vinylDiscWrap: {
    flex: 1,
    minHeight: 260,
    alignItems: "center",
    justifyContent: "center",
    position: "relative",
  },
  vinylDisc: {
    width: 320,
    height: 320,
    borderRadius: 160,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#102231",
    borderWidth: 10,
    borderColor: "rgba(255,255,255,0.08)",
  },
  vinylRingOuter: {
    position: "absolute",
    width: 286,
    height: 286,
    borderRadius: 143,
    borderWidth: 2,
    borderColor: "rgba(255,255,255,0.06)",
  },
  vinylRingMid: {
    position: "absolute",
    width: 230,
    height: 230,
    borderRadius: 115,
    borderWidth: 2,
    borderColor: "rgba(255,255,255,0.05)",
  },
  vinylRingInner: {
    position: "absolute",
    width: 170,
    height: 170,
    borderRadius: 85,
    borderWidth: 2,
    borderColor: "rgba(255,255,255,0.05)",
  },
  vinylCenterCover: {
    width: 106,
    height: 106,
    borderRadius: 53,
    backgroundColor: "#1A2F40",
  },
  vinylNeedle: {
    position: "absolute",
    right: 38,
    top: 12,
    width: 6,
    height: 180,
    borderRadius: 6,
    backgroundColor: "#F1EEE8",
    transform: [{ rotate: "14deg" }],
  },
  vinylInfoWrap: {
    flex: 1,
    justifyContent: "center",
    paddingLeft: 8,
  },
  vinylTitle: {
    color: "#FFFFFF",
    fontSize: 34,
    fontWeight: "900",
    lineHeight: 42,
  },
  vinylArtist: {
    color: "#B8C3CF",
    fontSize: 18,
    marginTop: 10,
    marginBottom: 26,
  },
  vinylLyricsBox: {
    maxHeight: 180,
    marginBottom: 20,
    paddingRight: 6,
  },
  vinylLyricsWrap: {
    maxHeight: 180,
    marginBottom: 20,
    position: "relative",
  },
  vinylLyricsContent: {
    paddingBottom: 12,
  },
  vinylLyricLine: {
    color: "rgba(255,255,255,0.26)",
    fontSize: 20,
    fontWeight: "700",
    lineHeight: 28,
    marginBottom: 12,
  },
  vinylLyricLineActive: {
    color: "#F3E9EE",
  },
  vinylLyricsEmpty: {
    color: "#8F98A5",
    fontSize: 16,
    lineHeight: 24,
  },
  vinylActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 18,
  },
  vinylIconBtn: {
    width: 56,
    height: 56,
    alignItems: "center",
    justifyContent: "center",
  },
  vinylControlBtn: {
    width: 82,
    height: 82,
    alignItems: "center",
    justifyContent: "center",
  },
  screen: {
    flex: 1,
    justifyContent: "space-between",
    paddingBottom: 10,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  hBtn: { width: 44, height: 44, alignItems: "center", justifyContent: "center" },
  hLabel: { color: "#8B8B92", fontSize: 12, letterSpacing: 1, textTransform: "uppercase" },
  hAlbum: { color: "#FFFFFF", fontSize: 14, fontWeight: "600", maxWidth: 200 },
  coverWrap: {
    alignItems: "center",
    marginTop: 8,
    marginHorizontal: 24,
  },
  cover: {
    borderRadius: 24,
    backgroundColor: "#15151B",
  },
  infoRow: {
    flexDirection: "row",
    alignItems: "center",
    marginTop: 16,
    paddingHorizontal: 24,
  },
  infoRowCompact: {
    marginTop: 12,
    paddingHorizontal: 18,
  },
  info: { flex: 1, paddingRight: 8 },
  infoActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  title: { color: "#FFFFFF", fontSize: 26, fontWeight: "800" },
  titleCompact: { fontSize: 20 },
  artist: { color: "#A0A0A5", fontSize: 16, marginTop: 6 },
  artistCompact: { fontSize: 13, marginTop: 4 },
  progressWrap: { marginTop: 12, paddingHorizontal: 28 },
  progressWrapCompact: { marginTop: 8, paddingHorizontal: 20 },
  progressBg: {
    width: "100%",
    height: 30,
    justifyContent: "center",
  },
  slider: {
    width: "100%",
    height: 30,
  },
  times: { flexDirection: "row", justifyContent: "space-between", marginTop: 2 },
  timeText: { color: "#8B8B92", fontSize: 12 },
  controls: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 10,
    paddingHorizontal: 56,
    marginBottom: 6,
  },
  controlsCompact: { paddingHorizontal: 28, marginTop: 2, marginBottom: 2 },
  utilityRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 22,
    marginBottom: 2,
  },
  utilityRowCompact: { paddingHorizontal: 14, marginBottom: 0 },
  utilitySideGroup: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.45)",
    justifyContent: "flex-end",
  },
  confirmDialog: {
    marginHorizontal: 18,
    marginBottom: 120,
    borderRadius: 28,
    backgroundColor: "#E9E3F5",
    paddingHorizontal: 26,
    paddingTop: 28,
    paddingBottom: 22,
    gap: 18,
  },
  confirmTitle: {
    color: "#17141B",
    fontSize: 22,
    fontWeight: "800",
  },
  confirmText: {
    color: "#7B7485",
    fontSize: 17,
    lineHeight: 24,
  },
  confirmActions: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 28,
  },
  confirmButton: {
    minHeight: 42,
    justifyContent: "center",
  },
  confirmButtonText: {
    color: "#3B3742",
    fontSize: 17,
    fontWeight: "700",
  },
  confirmDeleteText: {
    color: "#C3483E",
  },
  equalizerSheet: {
    backgroundColor: "#5A3436",
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingTop: 10,
    paddingBottom: 16,
    height: "74%",
  },
  equalizerHandle: {
    width: 56,
    height: 4,
    borderRadius: 2,
    backgroundColor: "#7A5759",
    alignSelf: "center",
    marginBottom: 18,
  },
  equalizerHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 24,
    marginBottom: 18,
  },
  equalizerTitle: {
    color: "#FFFFFF",
    fontSize: 22,
    fontWeight: "800",
  },
  equalizerToggle: {
    width: 58,
    height: 34,
    borderRadius: 18,
    backgroundColor: "#6E4A4D",
    paddingHorizontal: 4,
    justifyContent: "center",
  },
  equalizerToggleActive: {
    backgroundColor: "#8A5A5D",
  },
  equalizerToggleThumb: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: "#EDE8EF",
  },
  equalizerToggleThumbActive: {
    alignSelf: "flex-end",
  },
  equalizerBandsWrap: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingHorizontal: 14,
    marginBottom: 20,
  },
  equalizerBand: {
    alignItems: "center",
    flex: 1,
  },
  equalizerBandValue: {
    color: "#A9969A",
    fontSize: 12,
    marginBottom: 12,
  },
  equalizerTrackShell: {
    width: 18,
    height: 150,
    alignItems: "center",
    justifyContent: "center",
  },
  equalizerSlider: {
    width: 150,
    height: 24,
    transform: [{ rotate: "-90deg" }],
  },
  equalizerBandLabel: {
    color: "#A9969A",
    fontSize: 12,
    marginTop: 10,
  },
  equalizerPresetList: {
    flex: 1,
  },
  equalizerPresetListContent: {
    paddingHorizontal: 22,
    paddingRight: 34,
    paddingBottom: 12,
  },
  equalizerPresetGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "space-between",
    gap: 14,
  },
  equalizerPresetCard: {
    width: "47%",
    minHeight: 132,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.12)",
    backgroundColor: "rgba(78,44,46,0.55)",
    paddingHorizontal: 18,
    paddingVertical: 18,
    justifyContent: "space-between",
  },
  equalizerPresetCardActive: {
    borderColor: "rgba(255,255,255,0.28)",
    backgroundColor: "rgba(104,58,61,0.9)",
  },
  equalizerPresetText: {
    color: "#F3EDF1",
    fontSize: 15,
    fontWeight: "700",
    lineHeight: 22,
  },
  optionsSheet: {
    backgroundColor: "#5A3436",
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingTop: 10,
    paddingBottom: 16,
    height: "86%",
  },
  optionsHandle: {
    width: 56,
    height: 4,
    borderRadius: 2,
    backgroundColor: "#7A5759",
    alignSelf: "center",
    marginBottom: 18,
  },
  optionsHeader: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 20,
    paddingBottom: 14,
  },
  optionsCover: {
    width: 62,
    height: 62,
    borderRadius: 14,
    backgroundColor: "#6D4B4D",
  },
  optionsInfo: {
    flex: 1,
    marginLeft: 16,
    paddingRight: 12,
  },
  optionsTitle: {
    color: "#FFFFFF",
    fontSize: 17,
    fontWeight: "800",
  },
  optionsArtist: {
    color: "#D7D0D5",
    fontSize: 13,
    marginTop: 4,
    lineHeight: 18,
  },
  optionsRenameBtn: {
    width: 44,
    height: 44,
    alignItems: "center",
    justifyContent: "center",
  },
  optionsList: {
    flex: 1,
  },
  optionsListContent: {
    paddingHorizontal: 18,
    paddingRight: 34,
    paddingTop: 2,
    paddingBottom: 52,
    flexGrow: 1,
  },
  scrollAreaWrap: {
    flex: 1,
    position: "relative",
  },
  optionSectionDivider: {
    height: 1,
    backgroundColor: "rgba(255,255,255,0.09)",
    marginHorizontal: 8,
    marginVertical: 5,
  },
  optionRow: {
    flexDirection: "row",
    alignItems: "center",
    minHeight: 52,
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  optionIcon: {
    width: 38,
    marginRight: 18,
  },
  optionText: {
    flex: 1,
    color: "#F3EDF1",
    fontSize: 15,
    fontWeight: "600",
  },
  optionDivider: {
    height: 1,
    backgroundColor: "rgba(255,255,255,0.08)",
    marginHorizontal: 8,
    marginVertical: 10,
  },
  speedSheet: {
    backgroundColor: "#E9E3F5",
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingTop: 10,
    paddingBottom: 24,
    paddingHorizontal: 24,
  },
  speedTitle: {
    color: "#141218",
    fontSize: 22,
    fontWeight: "800",
    marginBottom: 22,
  },
  speedRow: {
    minHeight: 58,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  speedText: {
    color: "#17141B",
    fontSize: 18,
    fontWeight: "500",
  },
  speedTextActive: {
    color: "#6A6277",
    fontWeight: "700",
  },
  sleepSheet: {
    backgroundColor: "#E9E3F5",
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingTop: 10,
    paddingBottom: 24,
    paddingHorizontal: 24,
  },
  sleepTitle: {
    color: "#141218",
    fontSize: 22,
    fontWeight: "800",
    marginBottom: 10,
  },
  sleepRow: {
    minHeight: 58,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  sleepText: {
    color: "#4C4654",
    fontSize: 18,
    fontWeight: "500",
  },
  sleepTextActive: {
    color: "#141218",
    fontWeight: "700",
  },
  sleepCustomRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingTop: 12,
    paddingBottom: 18,
    borderTopWidth: 1,
    borderTopColor: "rgba(20,18,24,0.08)",
  },
  sleepCustomLabel: { flex: 1 },
  sleepCustomValue: { color: "#6E6578", fontSize: 15, fontWeight: "700" },
  sleepFooterRow: {
    marginTop: 8,
    paddingTop: 20,
    borderTopWidth: 1,
    borderTopColor: "rgba(20,18,24,0.08)",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 12,
  },
  sleepFooterText: {
    flex: 1,
    color: "#4C4654",
    fontSize: 17,
    fontWeight: "600",
  },
  sleepToggle: {
    width: 58,
    height: 34,
    borderRadius: 17,
    backgroundColor: "#8F879A",
    paddingHorizontal: 4,
    justifyContent: "center",
  },
  sleepToggleActive: {
    backgroundColor: "#6E6680",
  },
  sleepToggleThumb: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: "#F6F1FF",
  },
  sleepToggleThumbActive: {
    alignSelf: "flex-end",
  },
  tagsSafe: {
    flex: 1,
    backgroundColor: "#E9E3F5",
  },
  tagsScreen: {
    flex: 1,
    backgroundColor: "#E9E3F5",
  },
  tagsContent: {
    paddingHorizontal: 24,
    paddingBottom: 40,
  },
  tagsHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingTop: 10,
    marginBottom: 18,
  },
  tagsBackBtn: {
    width: 42,
    height: 42,
    alignItems: "center",
    justifyContent: "center",
  },
  tagsHeaderTitle: {
    flex: 1,
    color: "#141218",
    fontSize: 20,
    fontWeight: "800",
    marginLeft: 10,
  },
  tagsSaveBtn: {
    minWidth: 108,
    height: 44,
    borderRadius: 22,
    backgroundColor: "#6A6277",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingHorizontal: 16,
  },
  tagsSaveText: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "700",
  },
  tagsHintBox: {
    borderWidth: 1,
    borderColor: "#A79EB8",
    borderRadius: 18,
    padding: 18,
    backgroundColor: "rgba(255,255,255,0.3)",
    marginBottom: 24,
  },
  tagsHintTitle: {
    color: "#6A6277",
    fontSize: 16,
    fontWeight: "700",
    marginBottom: 12,
  },
  tagsHintLine: {
    color: "#2D2733",
    fontSize: 15,
    marginBottom: 6,
  },
  tagsFieldLabel: {
    color: "#8D8699",
    fontSize: 18,
    fontWeight: "700",
    marginBottom: 10,
    marginTop: 12,
  },
  tagsInput: {
    borderBottomWidth: 1,
    borderBottomColor: "#B4AFC0",
    color: "#16131A",
    fontSize: 17,
    paddingVertical: 10,
  },
  tagsReadonlyValue: {
    borderBottomWidth: 1,
    borderBottomColor: "#B4AFC0",
    color: "#16131A",
    fontSize: 17,
    paddingVertical: 10,
  },
  tagsCover: {
    width: 152,
    height: 152,
    borderRadius: 28,
    marginTop: 8,
    backgroundColor: "#6D4B4D",
  },
  iconBtn: {
    width: 44,
    height: 44,
    alignItems: "center",
    justifyContent: "center",
  },
  transportBtn: {
    width: 88,
    height: 88,
    alignItems: "center",
    justifyContent: "center",
  },
  transportBtnCompact: {
    width: 60,
    height: 60,
  },
  utilityIconBtn: {
    width: 56,
    height: 56,
    alignItems: "center",
    justifyContent: "center",
  },
  utilityIconBtnCompact: {
    width: 40,
    height: 40,
  },
  repeatDot: {
    position: "absolute",
    bottom: 8,
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: "#F5C518",
  },
  repeatDotAlt: {
    position: "absolute",
    right: 5,
    top: 10,
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: "#E8E6EE",
  },
  emptyWrap: { flex: 1, alignItems: "center", justifyContent: "center", gap: 16 },
  emptyText: { color: "#A0A0A5" },
  closeBtn: {
    backgroundColor: "#15151B",
    borderRadius: 999,
    paddingVertical: 12,
    paddingHorizontal: 24,
  },
  closeBtnText: { color: "#FFFFFF", fontWeight: "600" },
});
