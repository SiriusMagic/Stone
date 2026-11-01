import { useEffect, useMemo, useRef, useState } from "react";
import {
  AppState,
  Animated,
  Easing,
  Linking,
  PanResponder,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAudio } from "@/src/contexts/AudioContext";
import { useLanguage } from "@/src/contexts/LanguageContext";
import { usePlaybackTools } from "@/src/contexts/PlaybackToolsContext";
import { Track } from "@/src/data/tracks";
import { HOME_TABS } from "@/src/features/library/constants";
import { groupBy } from "@/src/features/library/helpers";
import {
  getLibraryDirectoryPreference,
  getLibraryTracks,
  importTrackFromUri,
  pickLibraryDirectory,
  scanLibraryDirectory,
} from "@/src/features/library/local-library";
import { styles } from "@/src/features/library/styles";
import { DeletedTab, HiddenCategory, SortDirection, SortKey, TabKey } from "@/src/features/library/types";
import { HeaderZone } from "@/src/features/library/zones/01-header/HeaderZone";
import { TabsZone } from "@/src/features/library/zones/02-tabs/TabsZone";
import { TrackListZone } from "@/src/features/library/zones/03-track-list/TrackListZone";
import { MiniPlayerZone } from "@/src/features/library/zones/05-mini-player/MiniPlayerZone";
import { SheetsZone } from "@/src/features/library/zones/06-sheets/SheetsZone";
import { FavoritesPlaylistScreen } from "@/src/features/library/components/FavoritesPlaylistScreen";
import { NewPlaylistDialog } from "@/src/features/library/components/NewPlaylistDialog";
import { migrateLegacyDownloadedTracksToLibrary } from "@/src/features/library/local-downloads";
import { LANGUAGES, LangCode } from "@/src/i18n/translations";
import { storage } from "@/src/utils/storage";

const LIBRARY_DOWNLOADS_UPDATED = "library:downloads-updated";
const LIBRARY_THEME_KEY = "@app/library-theme";
const LIBRARY_CUSTOM_PLAYLISTS_KEY = "@app/library-custom-playlists";
const LIBRARY_LAST_SCAN_AT_KEY = "@app/library-last-scan-at";
const AUTO_SCAN_MIN_INTERVAL_MS = 15 * 60 * 1000;
type LibraryTheme = "dark" | "gray" | "light";
type CustomPlaylist = { id: string; title: string; count: number };

function mergeLibraryTracks(downloadedTracks: Track[], hiddenIds: Set<string>, deletedIds: Set<string>) {
  const merged = [...downloadedTracks];
  return merged.filter(
    (track, index, list) =>
      list.findIndex((item) => item.id === track.id) === index &&
      !hiddenIds.has(track.id) &&
      !deletedIds.has(track.id),
  );
}

export default function LibraryPage() {
  const params = useLocalSearchParams<{ externalUri?: string | string[] }>();
  const router = useRouter();
  const { t, lang, setLang } = useLanguage();
  const { current, playTrack, playCounts, favoriteTrackIds, hiddenItems, deletedItems } = useAudio();
  const {
    sleepTimerOption, sleepTimerCustomMinutes, sleepTimerFinishTrack,
    setSleepTimerOption, setSleepTimerCustomMinutes, toggleSleepTimerFinishTrack,
  } = usePlaybackTools();

  const [tab, setTab] = useState<TabKey>("songs");
  const [videoFoldersVisible, setVideoFoldersVisible] = useState(false);
  const [audioFolderName, setAudioFolderName] = useState<string | null>(null);
  const [libraryFolderLabel, setLibraryFolderLabel] = useState("Stone");
  const [tracks, setTracks] = useState<Track[]>([]);
  const [scanned, setScanned] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);
  const [searchVisible, setSearchVisible] = useState(false);
  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("title");
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc");
  const [quickDownloadVisible, setQuickDownloadVisible] = useState(false);
  const [sortVisible, setSortVisible] = useState(false);
  const [settingsVisible, setSettingsVisible] = useState(false);
  const [sleepTimerVisible, setSleepTimerVisible] = useState(false);
  const [notificationsVisible, setNotificationsVisible] = useState(false);
  const [playbackSettingsVisible, setPlaybackSettingsVisible] = useState(false);
  const [feedbackVisible, setFeedbackVisible] = useState(false);
  const [themeVisible, setThemeVisible] = useState(false);
  const [themeMode, setThemeMode] = useState<LibraryTheme>("dark");
  const [customPlaylists, setCustomPlaylists] = useState<CustomPlaylist[]>([]);
  const [createPlaylistVisible, setCreatePlaylistVisible] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState("");
  const [activeCollection, setActiveCollection] = useState<"favorites" | "recent" | "most-played" | "lyrics" | null>(null);
  const [recentDeletedVisible, setRecentDeletedVisible] = useState(false);
  const [hiddenFilesVisible, setHiddenFilesVisible] = useState(false);
  const [hiddenSearchVisible, setHiddenSearchVisible] = useState(false);
  const [hiddenSortVisible, setHiddenSortVisible] = useState(false);
  const [hiddenConfigVisible, setHiddenConfigVisible] = useState(false);
  const [langModal, setLangModal] = useState(false);
  const [notifyNewFiles, setNotifyNewFiles] = useState(true);
  const [pushNotifications, setPushNotifications] = useState(true);
  const [playWithOthers, setPlayWithOthers] = useState(false);
  const [gaplessPlayback, setGaplessPlayback] = useState(false);
  const [crossfadeEnabled, setCrossfadeEnabled] = useState(false);
  const [crossfadeSeconds, setCrossfadeSeconds] = useState(12);
  const [hiddenVideoToAudio, setHiddenVideoToAudio] = useState(true);
  const [hiddenTab, setHiddenTab] = useState<DeletedTab>("audio");
  const [hiddenCategory, setHiddenCategory] = useState<HiddenCategory>("all");
  const [hiddenQuery, setHiddenQuery] = useState("");
  const [hiddenSortKey, setHiddenSortKey] = useState<SortKey>("recent");
  const [hiddenSortDirection, setHiddenSortDirection] = useState<SortDirection>("desc");
  const [hiddenActionItemId, setHiddenActionItemId] = useState<string | null>(null);
  const [deletedTab, setDeletedTab] = useState<DeletedTab>("audio");
  const [selectedDeletedIds, setSelectedDeletedIds] = useState<string[]>([]);
  const [actionDeletedItemId, setActionDeletedItemId] = useState<string | null>(null);

  const spinAnim = useRef(new Animated.Value(0)).current;
  const externalUriRef = useRef<Record<string, true>>({});
  const scanInFlightRef = useRef(false);

  const syncLibraryTracks = async (nextHiddenItems = hiddenItems, nextDeletedItems = deletedItems) => {
    const libraryTracks = await getLibraryTracks();
    const hiddenIds = new Set(nextHiddenItems.map((item) => item.id));
    const deletedIds = new Set(nextDeletedItems.map((item) => item.id));
    setTracks(mergeLibraryTracks(libraryTracks, hiddenIds, deletedIds));
    return libraryTracks;
  };

  useEffect(() => {
    (async () => {
      const saved = await storage.getItem<boolean>("@app/scanned", false);
      const savedTheme = await storage.getItem<LibraryTheme>(LIBRARY_THEME_KEY, "dark");
      const savedPlaylists = await storage.getItem<CustomPlaylist[]>(LIBRARY_CUSTOM_PLAYLISTS_KEY, []);
      const preference = await getLibraryDirectoryPreference();
      await migrateLegacyDownloadedTracksToLibrary();
      setThemeMode(savedTheme ?? "dark");
      setCustomPlaylists(savedPlaylists ?? []);
      setLibraryFolderLabel(preference.label || "Stone");
      const downloadedTracks = await syncLibraryTracks(hiddenItems, deletedItems);
      if (saved) {
        setScanned(true);
        return;
      }
      if (!downloadedTracks.length) setTracks([]);
    })();
  }, [deletedItems, hiddenItems]);

  useEffect(() => {
    const maybeScanOnResume = async () => {
      const lastScanAt = await storage.getItem<number | null>(LIBRARY_LAST_SCAN_AT_KEY, null);
      if (lastScanAt && Date.now() - lastScanAt < AUTO_SCAN_MIN_INTERVAL_MS) return;
      await runScan(false);
    };
    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") maybeScanOnResume().catch(() => {});
    });
    maybeScanOnResume().catch(() => {});
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    const handleDownloadsUpdated = () => {
      syncLibraryTracks(hiddenItems, deletedItems).catch(() => {});
    };
    if (
      typeof window === "undefined" ||
      typeof window.addEventListener !== "function" ||
      typeof window.removeEventListener !== "function"
    ) {
      return;
    }
    window.addEventListener(LIBRARY_DOWNLOADS_UPDATED, handleDownloadsUpdated);
    return () => window.removeEventListener(LIBRARY_DOWNLOADS_UPDATED, handleDownloadsUpdated);
  }, [deletedItems, hiddenItems]);

  useEffect(() => {
    syncLibraryTracks(hiddenItems, deletedItems).catch(() => {});
  }, [deletedItems, hiddenItems]);

  useEffect(() => {
    const handleExternalTrack = async (incomingUrl?: string | null) => {
      if (!incomingUrl || externalUriRef.current[incomingUrl]) return;
      if (!incomingUrl.startsWith("content://") && !incomingUrl.startsWith("file://")) return;
      externalUriRef.current[incomingUrl] = true;
      const importedTrack = await importTrackFromUri(incomingUrl);
      await syncLibraryTracks(hiddenItems, deletedItems);
      if (importedTrack) playTrack(importedTrack, [importedTrack]);
    };
    Linking.getInitialURL().then(handleExternalTrack).catch(() => {});
    const subscription = Linking.addEventListener("url", ({ url }) => {
      handleExternalTrack(url).catch(() => {});
    });
    return () => subscription.remove();
  }, [deletedItems, hiddenItems, playTrack]);

  useEffect(() => {
    const paramValue = Array.isArray(params.externalUri)
      ? params.externalUri[0]
      : params.externalUri;
    if (!paramValue) return;
    const externalUri = decodeURIComponent(paramValue);
    if (!externalUri.startsWith("content://") && !externalUri.startsWith("file://")) return;
    if (externalUriRef.current[externalUri]) return;
    externalUriRef.current[externalUri] = true;
    importTrackFromUri(externalUri)
      .then(async (importedTrack) => {
        await syncLibraryTracks(hiddenItems, deletedItems);
        if (importedTrack) playTrack(importedTrack, [importedTrack]);
      })
      .finally(() => router.replace("/library"));
  }, [deletedItems, hiddenItems, params.externalUri, playTrack, router]);

  useEffect(() => {
    if (!scanning) {
      spinAnim.stopAnimation();
      spinAnim.setValue(0);
      return;
    }
    Animated.loop(
      Animated.timing(spinAnim, {
        toValue: 1,
        duration: 1000,
        easing: Easing.linear,
        useNativeDriver: true,
      })
    ).start();
  }, [scanning, spinAnim]);

  useEffect(() => {
    if (tab !== "videos") setVideoFoldersVisible(false);
    if (tab !== "folders") setAudioFolderName(null);
  }, [tab]);

  const runScan = async (force = true) => {
    if (scanInFlightRef.current || scanning) return;
    if (!force) {
      const lastScanAt = await storage.getItem<number | null>(LIBRARY_LAST_SCAN_AT_KEY, null);
      if (lastScanAt && Date.now() - lastScanAt < AUTO_SCAN_MIN_INTERVAL_MS) return;
    }
    scanInFlightRef.current = true;
    setScanning(true);
    setScanProgress(15);
    try {
      const existingPreference = await getLibraryDirectoryPreference();
      const preference = existingPreference.directoryUri
        ? existingPreference
        : await pickLibraryDirectory();
      if (!preference.directoryUri) return;
      setLibraryFolderLabel(preference.label || "Stone");
      setScanProgress(40);
      await scanLibraryDirectory();
      setScanProgress(80);
      await syncLibraryTracks(hiddenItems, deletedItems);
      setScanned(true);
      setScanProgress(100);
      await storage.setItem("@app/scanned", true);
      await storage.setItem(LIBRARY_LAST_SCAN_AT_KEY, Date.now());
    } finally {
      scanInFlightRef.current = false;
      setScanning(false);
    }
  };

  const pickStoneFolder = async () => {
    const preference = await pickLibraryDirectory();
    if (!preference.directoryUri) return;
    setLibraryFolderLabel(preference.label || "Stone");
    await runScan(true);
  };

  const rescanStoneLibrary = async () => {
    await runScan();
    setSettingsVisible(false);
  };

  const filtered = useMemo(() => {
    let next = tracks.filter((track) =>
      tab === "videos" ? track.kind === "video" : track.kind !== "video"
    );
    if (query.trim()) {
      const value = query.trim().toLowerCase();
      next = next.filter(
        (track) =>
          track.title.toLowerCase().includes(value) ||
          track.artist.toLowerCase().includes(value) ||
          track.album.toLowerCase().includes(value)
      );
    }
    const sorted = [...next];
    if (sortKey === "title") sorted.sort((a, b) => a.title.localeCompare(b.title));
    if (sortKey === "artist") sorted.sort((a, b) => a.artist.localeCompare(b.artist));
    if (sortKey === "mostPlayed") sorted.sort((a, b) => (playCounts[b.id] ?? 0) - (playCounts[a.id] ?? 0));
    if (sortKey === "duration") sorted.sort((a, b) => a.durationMs - b.durationMs);
    if (sortKey === "recent") sorted.reverse();
    if (sortDirection === "desc") sorted.reverse();
    return sorted;
  }, [tracks, tab, query, sortKey, sortDirection, playCounts]);

  const groupsByAlbum = useMemo(() => groupBy(filtered, (track) => track.album), [filtered]);
  const groupsByFolder = useMemo(() => groupBy(filtered, (track) => track.folder), [filtered]);
  const selectedAudioFolder = useMemo(
    () => groupsByFolder.find((group) => group.name === audioFolderName) ?? null,
    [audioFolderName, groupsByFolder],
  );
  const groupsByArtist = useMemo(
    () => groupBy(filtered, (track) => track.artist.split(",")[0].trim()),
    [filtered]
  );

  const favoriteTracks = useMemo(
    () => tracks.filter((track) => favoriteTrackIds.includes(track.id) && track.kind !== "video"),
    [favoriteTrackIds, tracks]
  );
  const recentTracks = useMemo(
    () =>
      tracks
        .filter((track) => track.kind !== "video" && ((playCounts[track.id] ?? 0) > 0 || current?.id === track.id))
        .sort((a, b) => (playCounts[b.id] ?? 0) - (playCounts[a.id] ?? 0)),
    [current?.id, playCounts, tracks],
  );
  const mostPlayedTracks = useMemo(
    () =>
      tracks
        .filter((track) => track.kind !== "video" && (playCounts[track.id] ?? 0) > 0)
        .sort((a, b) => (playCounts[b.id] ?? 0) - (playCounts[a.id] ?? 0)),
    [playCounts, tracks],
  );
  const lyricTracks = useMemo(
    () => tracks.filter((track) => track.kind !== "video" && (track.lyrics?.length ?? 0) > 0),
    [tracks],
  );

  const playlistItems = useMemo(
    () => [
      ...customPlaylists.map((item) => ({
        id: item.id,
        title: item.title,
        count: item.count,
        icon: "musical-notes-outline" as const,
      })),
      {
        id: "favorites",
        title: "Canciones favoritas",
        count: favoriteTrackIds.length,
        icon: "heart" as const,
        iconColor: "#FF6B81",
        cover: favoriteTracks[0]?.cover,
      },
      { id: "recent", title: "Recientemente\nreproducidas", count: recentTracks.length, cover: recentTracks[0]?.cover, icon: "time-outline" as const },
      { id: "most-played", title: "Mas reproducido", count: mostPlayedTracks.length, cover: mostPlayedTracks[0]?.cover, icon: "flame-outline" as const },
      { id: "lyrics", title: "Con letras", count: lyricTracks.length, cover: lyricTracks[0]?.cover, icon: "chatbox-ellipses-outline" as const },
    ],
    [customPlaylists, favoriteTrackIds.length, favoriteTracks, lyricTracks, mostPlayedTracks, recentTracks]
  );

  const handleCreatePlaylist = async () => {
    const trimmedName = newPlaylistName.trim();
    if (!trimmedName) return;
    const nextPlaylists = [
      { id: `custom-${Date.now()}`, title: trimmedName, count: 0 },
      ...customPlaylists,
    ];
    setCustomPlaylists(nextPlaylists);
    await storage.setItem(LIBRARY_CUSTOM_PLAYLISTS_KEY, nextPlaylists);
    setNewPlaylistName("");
    setCreatePlaylistVisible(false);
  };

  const handleOpenPlaylistItem = (playlistId: string) => {
    if (playlistId === "favorites" || playlistId === "recent" || playlistId === "most-played" || playlistId === "lyrics") {
      setActiveCollection(playlistId);
    }
  };

  const handleShuffleFavorites = () => {
    if (!favoriteTracks.length) return;
    const shuffled = [...favoriteTracks].sort(() => Math.random() - 0.5);
    playTrack(shuffled[0], shuffled);
  };
  const activeCollectionTracks = useMemo(() => {
    if (activeCollection === "favorites") return favoriteTracks;
    if (activeCollection === "recent") return recentTracks;
    if (activeCollection === "most-played") return mostPlayedTracks;
    if (activeCollection === "lyrics") return lyricTracks;
    return [];
  }, [activeCollection, favoriteTracks, lyricTracks, mostPlayedTracks, recentTracks]);
  const activeCollectionTitle =
    activeCollection === "favorites"
      ? "Canciones favoritas"
      : activeCollection === "recent"
        ? "Recientemente reproducidas"
        : activeCollection === "most-played"
          ? "Más reproducido"
          : "Con letras";
  const activeCollectionHeaderMode =
    activeCollection === "favorites"
      ? "menu"
      : activeCollection === "lyrics"
        ? "sort-check"
        : "check";
  const handleShuffleCollection = () => {
    if (!activeCollectionTracks.length) return;
    const shuffled = [...activeCollectionTracks].sort(() => Math.random() - 0.5);
    playTrack(shuffled[0], shuffled);
  };

  const hiddenFilteredItems = useMemo(
    () =>
      hiddenItems
        .filter((item) => item.type === hiddenTab)
        .filter((item) => hiddenCategory === "all" || item.category === hiddenCategory)
        .filter((item) => {
          const value = hiddenQuery.trim().toLowerCase();
          return !value || item.title.toLowerCase().includes(value) || item.subtitle.toLowerCase().includes(value);
        })
        .sort((a, b) => {
          if (hiddenSortKey === "title") return a.title.localeCompare(b.title);
          if (hiddenSortKey === "artist") return a.subtitle.localeCompare(b.subtitle);
          if (hiddenSortKey === "recent") return hiddenSortDirection === "desc" ? -1 : 1;
          return 0;
        }),
    [hiddenCategory, hiddenQuery, hiddenSortDirection, hiddenSortKey, hiddenTab]
  );

  const filteredDeletedItems = useMemo(
    () => deletedItems.filter((item) => item.type === deletedTab),
    [deletedItems, deletedTab]
  );

  const actionDeletedItem = deletedItems.find((item) => item.id === actionDeletedItemId) ?? null;
  const hiddenActionItem = hiddenItems.find((item) => item.id === hiddenActionItemId) ?? null;
  const selectionMode = selectedDeletedIds.length > 0;

  const sleepTimerLabel =
    sleepTimerOption === "off"
      ? "Apagar"
      : sleepTimerOption === "custom"
        ? `${sleepTimerCustomMinutes} min`
        : `${sleepTimerOption} min`;

  const languageLabel = LANGUAGES.find((item) => item.code === lang)?.label ?? "Espanol";
  const themeLabel =
    themeMode === "light" ? "Claro" : themeMode === "gray" ? "Gris" : "Negro";
  const themeColors = {
    dark: "#0A0A0E",
    gray: "#1A1C20",
    light: "#F3F1EB",
  } as const;

  const onPlay = (track: Track, queue: Track[]) => {
    playTrack(track, queue);
  };

  const resetDeletedUi = () => {
    setSelectedDeletedIds([]);
    setActionDeletedItemId(null);
  };

  const resetHiddenUi = () => {
    setHiddenSearchVisible(false);
    setHiddenConfigVisible(false);
    setHiddenSortVisible(false);
    setHiddenActionItemId(null);
  };

  const openHiddenFiles = (nextTab: DeletedTab = "audio") => {
    setHiddenTab(nextTab);
    setHiddenFilesVisible(true);
    resetHiddenUi();
  };

  const closeHiddenFiles = () => {
    setHiddenFilesVisible(false);
    resetHiddenUi();
  };

  const toggleDeletedSelection = (itemId: string) => {
    setSelectedDeletedIds((currentIds) =>
      currentIds.includes(itemId)
        ? currentIds.filter((id) => id !== itemId)
        : [...currentIds, itemId]
    );
  };

  const changeHiddenSort = (nextKey: SortKey) => {
    if (hiddenSortKey === nextKey) {
      setHiddenSortDirection((value) => (value === "asc" ? "desc" : "asc"));
    } else {
      setHiddenSortKey(nextKey);
      setHiddenSortDirection(nextKey === "recent" || nextKey === "mostPlayed" ? "desc" : "asc");
    }
    setHiddenSortVisible(false);
  };

  const spin = spinAnim.interpolate({
    inputRange: [0, 1],
    outputRange: ["0deg", "360deg"],
  });

  const contentSwipeResponder = useMemo(
    () =>
      PanResponder.create({
        onMoveShouldSetPanResponder: (_event, gesture) =>
          Math.abs(gesture.dx) > 18 && Math.abs(gesture.dx) > Math.abs(gesture.dy),
        onPanResponderTerminationRequest: () => false,
        onPanResponderRelease: (_event, gesture) => {
          if (Math.abs(gesture.dx) < 42) return;
          setTab((currentTab) => {
            const currentIndex = HOME_TABS.indexOf(currentTab);
            if (currentIndex === -1) return currentTab;
            if (gesture.dx < 0) return HOME_TABS[Math.min(currentIndex + 1, HOME_TABS.length - 1)];
            return HOME_TABS[Math.max(currentIndex - 1, 0)];
          });
        },
      }),
    []
  );

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: themeColors[themeMode] }]} edges={["top"]}>
      <HeaderZone
        searchVisible={searchVisible}
        query={query}
        placeholder={t("search_placeholder")}
        sortDirection={sortDirection}
        onOpenLibraryFolder={pickStoneFolder}
        onScanLibrary={rescanStoneLibrary}
        onToggleSearch={() => setSearchVisible((value) => !value)}
        onChangeQuery={setQuery}
        onClearQuery={() => setQuery("")}
        onOpenQuickDownload={() => setQuickDownloadVisible(true)}
        onOpenSort={() => setSortVisible(true)}
        onOpenSettings={() => setSettingsVisible(true)}
      />

      <TabsZone tab={tab} onChange={setTab} t={t} />

      <TrackListZone
        tab={tab}
        filtered={filtered}
        groupsByArtist={groupsByArtist}
        groupsByAlbum={groupsByAlbum}
        groupsByFolder={groupsByFolder}
        selectedAudioFolder={selectedAudioFolder}
        playlistItems={playlistItems}
        videoFoldersVisible={videoFoldersVisible}
        currentTrackId={current?.id}
        scanning={scanning}
        scanProgress={scanProgress}
        scanned={scanned}
        spin={spin}
        t={t}
        contentPanHandlers={contentSwipeResponder.panHandlers}
        onPlay={onPlay}
        onScan={runScan}
        onChooseFolder={pickStoneFolder}
        onOpenHiddenFiles={openHiddenFiles}
        onOpenAudioFolder={setAudioFolderName}
        onCloseAudioFolder={() => setAudioFolderName(null)}
        onOpenVideoFolders={() => setVideoFoldersVisible(true)}
        onCloseVideoFolders={() => setVideoFoldersVisible(false)}
        onOpenCreatePlaylist={() => setCreatePlaylistVisible(true)}
        onOpenPlaylistItem={handleOpenPlaylistItem}
      />

      {current ? <MiniPlayerZone /> : null}

      <SheetsZone
        t={t}
        lang={lang}
        languageLabel={languageLabel}
        quickDownloadVisible={quickDownloadVisible}
        sortVisible={sortVisible}
        sortKey={sortKey}
        sortDirection={sortDirection}
        settingsVisible={settingsVisible}
        sleepTimerVisible={sleepTimerVisible}
        themeVisible={themeVisible}
        themeLabel={themeLabel}
        themeMode={themeMode}
        notificationsVisible={notificationsVisible}
        playbackSettingsVisible={playbackSettingsVisible}
        feedbackVisible={feedbackVisible}
        recentDeletedVisible={recentDeletedVisible}
        hiddenFilesVisible={hiddenFilesVisible}
        hiddenSearchVisible={hiddenSearchVisible}
        hiddenSortVisible={hiddenSortVisible}
        hiddenConfigVisible={hiddenConfigVisible}
        langModal={langModal}
        sleepTimerLabel={sleepTimerLabel}
        hiddenFilesCount={hiddenItems.length}
        recentDeletedCount={deletedItems.length}
        libraryFolderLabel={libraryFolderLabel}
        sleepTimerOption={sleepTimerOption}
        sleepTimerCustomMinutes={sleepTimerCustomMinutes}
        sleepTimerFinishTrack={sleepTimerFinishTrack}
        notifyNewFiles={notifyNewFiles}
        pushNotifications={pushNotifications}
        playWithOthers={playWithOthers}
        gaplessPlayback={gaplessPlayback}
        crossfadeEnabled={crossfadeEnabled}
        crossfadeSeconds={crossfadeSeconds}
        hiddenVideoToAudio={hiddenVideoToAudio}
        hiddenTab={hiddenTab}
        hiddenCategory={hiddenCategory}
        hiddenQuery={hiddenQuery}
        hiddenSortKey={hiddenSortKey}
        hiddenSortDirection={hiddenSortDirection}
        deletedTab={deletedTab}
        selectedDeletedIds={selectedDeletedIds}
        selectionMode={selectionMode}
        hiddenItems={hiddenFilteredItems}
        deletedItems={filteredDeletedItems}
        deletedActionItem={actionDeletedItem}
        hiddenActionItem={hiddenActionItem}
        onCloseQuickDownload={() => setQuickDownloadVisible(false)}
        onCloseSort={() => setSortVisible(false)}
        onChangeSort={(nextSortKey) => {
          if (sortKey === nextSortKey) {
            setSortDirection((value) => (value === "asc" ? "desc" : "asc"));
          } else {
            setSortKey(nextSortKey);
            setSortDirection(
              nextSortKey === "recent" || nextSortKey === "mostPlayed" || nextSortKey === "duration"
                ? "desc"
                : "asc"
            );
          }
          setSortVisible(false);
        }}
        onCloseSettings={() => setSettingsVisible(false)}
        onPickLibraryFolder={async () => {
          setSettingsVisible(false);
          await pickStoneFolder();
        }}
        onRescanLibrary={rescanStoneLibrary}
        onOpenSleepTimer={() => {
          setSettingsVisible(false);
          setSleepTimerVisible(true);
        }}
        onOpenTheme={() => {
          setSettingsVisible(false);
          setThemeVisible(true);
        }}
        onOpenHiddenFiles={() => {
          setSettingsVisible(false);
          openHiddenFiles("audio");
        }}
        onOpenRecentDeleted={() => {
          setSettingsVisible(false);
          setRecentDeletedVisible(true);
          resetDeletedUi();
        }}
        onOpenPlaybackSettings={() => {
          setSettingsVisible(false);
          setPlaybackSettingsVisible(true);
        }}
        onOpenNotifications={() => {
          setSettingsVisible(false);
          setNotificationsVisible(true);
        }}
        onOpenLanguage={() => {
          setSettingsVisible(false);
          setLangModal(true);
        }}
        onOpenFeedback={() => {
          setSettingsVisible(false);
          setFeedbackVisible(true);
        }}
        onCloseSleepTimer={() => setSleepTimerVisible(false)}
        onCloseTheme={() => setThemeVisible(false)}
        onSelectTheme={async (nextTheme) => {
          setThemeMode(nextTheme);
          await storage.setItem(LIBRARY_THEME_KEY, nextTheme);
          setThemeVisible(false);
        }}
        onChangeSleepTimerOption={setSleepTimerOption}
        onChangeSleepTimerCustomMinutes={setSleepTimerCustomMinutes}
        onToggleSleepTimerFinishTrack={toggleSleepTimerFinishTrack}
        onCloseLanguage={() => setLangModal(false)}
        onSelectLanguage={async (code) => {
          await setLang(code as LangCode);
          setLangModal(false);
        }}
        onCloseNotifications={() => setNotificationsVisible(false)}
        onToggleNewFiles={() => setNotifyNewFiles((value) => !value)}
        onTogglePush={() => setPushNotifications((value) => !value)}
        onClosePlaybackSettings={() => setPlaybackSettingsVisible(false)}
        onCloseFeedback={() => setFeedbackVisible(false)}
        onTogglePlayWithOthers={() => setPlayWithOthers((value) => !value)}
        onToggleGapless={() => setGaplessPlayback((value) => !value)}
        onToggleCrossfade={() => setCrossfadeEnabled((value) => !value)}
        onChangeCrossfadeSeconds={setCrossfadeSeconds}
        onCloseHiddenFiles={closeHiddenFiles}
        onBackHiddenFiles={() => {
          if (hiddenSearchVisible) {
            setHiddenSearchVisible(false);
            return;
          }
          closeHiddenFiles();
        }}
        onShowHiddenSearch={() => setHiddenSearchVisible(true)}
        onOpenHiddenSort={() => setHiddenSortVisible(true)}
        onOpenHiddenConfig={() => setHiddenConfigVisible(true)}
        onChangeHiddenQuery={setHiddenQuery}
        onSelectHiddenTab={(nextTab) => {
          setHiddenTab(nextTab);
          setHiddenActionItemId(null);
        }}
        onSelectHiddenCategory={setHiddenCategory}
        onOpenHiddenItem={setHiddenActionItemId}
        onCloseRecentDeleted={() => {
          setRecentDeletedVisible(false);
          resetDeletedUi();
        }}
        onSelectDeletedTab={(nextTab) => {
          setDeletedTab(nextTab);
          resetDeletedUi();
        }}
        onToggleDeletedSelection={toggleDeletedSelection}
        onOpenDeletedItemActions={setActionDeletedItemId}
        onSelectAllDeleted={() => {
          if (selectedDeletedIds.length === filteredDeletedItems.length) {
            setSelectedDeletedIds([]);
            return;
          }
          setSelectedDeletedIds(filteredDeletedItems.map((item) => item.id));
        }}
        onCloseDeletedActions={() => setActionDeletedItemId(null)}
        onCloseHiddenActions={() => setHiddenActionItemId(null)}
        onCloseHiddenConfig={() => setHiddenConfigVisible(false)}
        onToggleHiddenVideoToAudio={() => setHiddenVideoToAudio((value) => !value)}
        onCloseHiddenSort={() => setHiddenSortVisible(false)}
        onChangeHiddenSort={changeHiddenSort}
      />

      <NewPlaylistDialog
        visible={createPlaylistVisible}
        value={newPlaylistName}
        onChange={setNewPlaylistName}
        onClose={() => {
          setCreatePlaylistVisible(false);
          setNewPlaylistName("");
        }}
        onCreate={handleCreatePlaylist}
      />
      <FavoritesPlaylistScreen
        visible={activeCollection !== null}
        title={activeCollectionTitle}
        headerMode={activeCollectionHeaderMode}
        tracks={activeCollectionTracks}
        onClose={() => setActiveCollection(null)}
        onPlay={onPlay}
        onShuffle={activeCollection === "favorites" ? handleShuffleFavorites : handleShuffleCollection}
      />
    </SafeAreaView>
  );
}
