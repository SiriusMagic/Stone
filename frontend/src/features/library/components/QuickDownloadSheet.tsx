import { useCallback, useEffect, useMemo, useState } from "react";
import { Text, TextInput, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";

import {
  analyzeDownloadUrl,
  cancelAllQueuedDownloads,
  enqueueDownloads,
  fetchDownloadQueue,
  removeQueuedDownloads,
  reorderQueuedDownloads,
  resolveDownloadMediaUrl,
} from "../download-api";
import {
  getDownloadDirectoryPreference,
  persistDownloadedTrack,
  pickDownloadDirectory,
} from "../local-downloads";
import { styles } from "../styles";
import {
  DownloadAnalyzeResult,
  DownloadFormatOption,
  DownloadMode,
  DownloadPlaylistEntry,
  DownloadQueueItem,
  DownloadQueueRequestItem,
  DownloadQueueSnapshot,
  PlaylistFormatsResult,
} from "../types";
import { ScreenSheet } from "./shared";
import { QuickDownloadPlaylistScreen } from "./QuickDownloadPlaylistScreen";
import { useAudio } from "@/src/contexts/AudioContext";
import { DEMO_TRACKS, Track } from "@/src/data/tracks";

const EMPTY_SNAPSHOT: DownloadQueueSnapshot = {
  items: [],
  totalCount: 0,
  doneCount: 0,
  downloadedCount: 0,
  activeCount: 0,
  errorCount: 0,
  remainingCount: 0,
  progressPercent: 0,
  downloadedBytes: 0,
  totalBytes: null,
  etaSeconds: null,
  etaLabel: null,
};

function isBlockedQueueItem(item: DownloadQueueItem | null | undefined) {
  if (!item || item.status !== "error" || !item.errorMessage) return false;
  return item.errorMessage
    .toLowerCase()
    .includes("youtube bloqueo esa cancion");
}

const AUDIO_OPTIONS: DownloadFormatOption[] = [
  {
    id: "audio-mp3-320",
    kind: "audio",
    label: "MP3 320 kbps (Maxima calidad)",
    sublabel: "Audio con mejor calidad disponible",
    sizeLabel: "8.4 MB",
    ext: "mp3",
  },
  {
    id: "audio-mp3-192",
    kind: "audio",
    label: "MP3 192 kbps (Alta calidad)",
    sublabel: "Buen equilibrio entre tamano y calidad",
    sizeLabel: "4.0 MB",
    ext: "mp3",
  },
  {
    id: "audio-mp3-128",
    kind: "audio",
    label: "MP3 128 kbps (Buena calidad)",
    sublabel: "Ideal para uso diario",
    sizeLabel: "2.5 MB",
    ext: "mp3",
  },
  {
    id: "audio-mp3-70",
    kind: "audio",
    label: "MP3 70 kbps (Baja calidad)",
    sublabel: "Descarga mas ligera",
    sizeLabel: "1.3 MB",
    ext: "mp3",
  },
];

const VIDEO_OPTIONS: DownloadFormatOption[] = [
  {
    id: "video-144",
    kind: "video",
    label: "Rapido (144p)",
    sublabel: "Video de calidad baja",
    sizeLabel: "4.8 MB",
    ext: "mp4",
  },
  {
    id: "video-240",
    kind: "video",
    label: "Rapido (240p)",
    sublabel: "Calidad baja para reproduccion rapida",
    sizeLabel: "5.5 MB",
    ext: "mp4",
  },
  {
    id: "video-360",
    kind: "video",
    label: "Rapido (360p)",
    sublabel: "Calidad normal para reproduccion rapida",
    sizeLabel: "6.6 MB",
    ext: "mp4",
  },
  {
    id: "video-480",
    kind: "video",
    label: "Rapido (480p)",
    sublabel: "Calidad normal para reproduccion rapida",
    sizeLabel: "7.9 MB",
    ext: "mp4",
  },
  {
    id: "video-720",
    kind: "video",
    label: "Calidad alta (720p)",
    sublabel: "Calidad clara y reproduccion rapida",
    sizeLabel: "10.0 MB",
    ext: "mp4",
  },
  {
    id: "video-1080",
    kind: "video",
    label: "Calidad alta (1080p)",
    sublabel: "Alta definicion para pantalla completa",
    sizeLabel: "26.2 MB",
    ext: "mp4",
  },
  {
    id: "video-1440",
    kind: "video",
    label: "Calidad alta (2K)",
    sublabel: "Alta definicion para pantalla grande",
    sizeLabel: "40.7 MB",
    ext: "mp4",
  },
  {
    id: "video-2160",
    kind: "video",
    label: "Calidad alta (4K)",
    sublabel: "Maxima definicion disponible",
    sizeLabel: "112.4 MB",
    ext: "mp4",
  },
];

const LIBRARY_DOWNLOADS_UPDATED = "library:downloads-updated";
const FALLBACK_COVER = DEMO_TRACKS[0]?.cover ?? "https://placehold.co/400x400/png";

function buildSingleEntryId(url: string) {
  return `single:${url}`;
}

function isBusyStatus(status?: DownloadQueueItem["status"]) {
  return status === "queued" || status === "analyzing" || status === "downloading";
}

function buildQueueTrack(entry: DownloadPlaylistEntry, item: DownloadQueueItem): Track {
  return {
    id: item.entryId,
    title: item.title,
    artist: item.source || entry.source || "Descargas",
    album: "Descargas",
    folder: item.mode === "video" ? "Videos" : "Descargas",
    kind: item.mode,
    cover: item.thumbnail || entry.thumbnail || FALLBACK_COVER,
    url: resolveDownloadMediaUrl(item.downloadUrl),
    localUri: undefined,
    durationMs: (item.durationSeconds ?? entry.durationSeconds ?? 0) * 1000,
    lyrics: [],
  };
}

function buildQueueRequestItem(entry: DownloadPlaylistEntry): DownloadQueueRequestItem {
  return {
    entry_id: entry.id,
    title: entry.title,
    source: entry.source,
    thumbnail: entry.thumbnail,
    original_url: entry.webpageUrl || "",
    duration_seconds: entry.durationSeconds ?? null,
    duration_label: entry.durationLabel,
  };
}

function buildSingleQueueRequestItem(preview: DownloadAnalyzeResult): DownloadQueueRequestItem {
  return {
    entry_id: buildSingleEntryId(preview.originalUrl),
    title: preview.title,
    source: preview.source,
    thumbnail: preview.thumbnail,
    original_url: preview.originalUrl,
    duration_seconds: preview.durationSeconds ?? null,
    duration_label: preview.durationLabel,
  };
}

export function QuickDownloadSheet({
  visible,
  onClose,
}: {
  visible: boolean;
  onClose: () => void;
}) {
  const router = useRouter();
  const { playTrack } = useAudio();

  const [mode, setMode] = useState<DownloadMode>("audio");
  const [urlValue, setUrlValue] = useState("");
  const [formatPickerVisible, setFormatPickerVisible] = useState(false);
  const [selectedAudioId, setSelectedAudioId] = useState("audio-mp3-320");
  const [selectedVideoId, setSelectedVideoId] = useState("video-1080");
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [preview, setPreview] = useState<DownloadAnalyzeResult | null>(null);
  const [playlistFormats, setPlaylistFormats] = useState<PlaylistFormatsResult | null>(null);
  const [selectedPlaylistIds, setSelectedPlaylistIds] = useState<string[]>([]);
  const [hiddenPlaylistIds, setHiddenPlaylistIds] = useState<string[]>([]);
  const [playlistOrder, setPlaylistOrder] = useState<string[]>([]);
  const [queueSnapshot, setQueueSnapshot] = useState<DownloadQueueSnapshot>(EMPTY_SNAPSHOT);
  const [lastDoneCount, setLastDoneCount] = useState(0);
  const [isAutoClearing, setIsAutoClearing] = useState(false);
  const [downloadDirectoryLabel, setDownloadDirectoryLabel] = useState("Descargas internas");

  const activeAudioOptions =
    playlistFormats?.audioFormats.length
      ? playlistFormats.audioFormats
      : preview?.audioFormats.length
        ? preview.audioFormats
        : AUDIO_OPTIONS;
  const activeVideoOptions =
    playlistFormats?.videoFormats.length
      ? playlistFormats.videoFormats
      : preview?.videoFormats.length
        ? preview.videoFormats
        : VIDEO_OPTIONS;
  const currentOptions = mode === "audio" ? activeAudioOptions : activeVideoOptions;
  const selectedAudio =
    activeAudioOptions.find((item) => item.id === selectedAudioId) ?? activeAudioOptions[0];
  const selectedVideo =
    activeVideoOptions.find((item) => item.id === selectedVideoId) ?? activeVideoOptions[0];
  const currentSelection = mode === "audio" ? selectedAudio : selectedVideo;

  const entryMap = useMemo(() => {
    if (!preview || preview.type !== "playlist") return new Map<string, DownloadPlaylistEntry>();
    return new Map(preview.entries.map((entry) => [entry.id, entry]));
  }, [preview]);

  const visiblePlaylistEntries = useMemo(() => {
    if (!preview || preview.type !== "playlist") return [];

    const orderedIds = playlistOrder.length
      ? playlistOrder
      : preview.entries.map((entry) => entry.id);

    return orderedIds
      .map((entryId) => entryMap.get(entryId))
      .filter(
        (entry): entry is DownloadPlaylistEntry =>
          !!entry && !hiddenPlaylistIds.includes(entry.id),
      );
  }, [entryMap, hiddenPlaylistIds, playlistOrder, preview]);

  const queueByEntryId = useMemo(
    () => new Map(queueSnapshot.items.map((item) => [item.entryId, item])),
    [queueSnapshot.items],
  );
  const blockedEntryIds = useMemo(
    () =>
      new Set(
        queueSnapshot.items
          .filter((item) => isBlockedQueueItem(item))
          .map((item) => item.entryId),
      ),
    [queueSnapshot.items],
  );
  const selectablePlaylistEntries = useMemo(
    () =>
      visiblePlaylistEntries.filter((entry) => !blockedEntryIds.has(entry.id)),
    [blockedEntryIds, visiblePlaylistEntries],
  );

  const allVisibleSelected =
    selectablePlaylistEntries.length > 0 &&
    selectablePlaylistEntries.every((entry) =>
      selectedPlaylistIds.includes(entry.id),
    );

  const singleEntryId =
    preview && preview.type !== "playlist" ? buildSingleEntryId(preview.originalUrl) : null;
  const singleQueueItem = singleEntryId ? queueByEntryId.get(singleEntryId) ?? null : null;

  const finishedPlaylistTracks = useMemo(() => {
    return visiblePlaylistEntries
      .map((entry) => {
        const item = queueByEntryId.get(entry.id);
        if (!item || item.status !== "done" || !item.downloadUrl) return null;
        return buildQueueTrack(entry, item);
      })
      .filter(Boolean) as Track[];
  }, [queueByEntryId, visiblePlaylistEntries]);

  useEffect(() => {
    if (activeAudioOptions.length && !activeAudioOptions.some((item) => item.id === selectedAudioId)) {
      setSelectedAudioId(activeAudioOptions[0].id);
    }
  }, [activeAudioOptions, selectedAudioId]);

  useEffect(() => {
    if (activeVideoOptions.length && !activeVideoOptions.some((item) => item.id === selectedVideoId)) {
      setSelectedVideoId(activeVideoOptions[0].id);
    }
  }, [activeVideoOptions, selectedVideoId]);

  const loadQueue = useCallback(async () => {
    const snapshot = await fetchDownloadQueue();
    setQueueSnapshot(snapshot);
  }, []);

  useEffect(() => {
    if (!visible) return;

    loadQueue().catch(() => {});
    const timer = setInterval(() => {
      loadQueue().catch(() => {});
    }, 1500);

    return () => clearInterval(timer);
  }, [loadQueue, visible]);

  useEffect(() => {
    if (!visible) return;
    getDownloadDirectoryPreference()
      .then((preference) => setDownloadDirectoryLabel(preference.label))
      .catch(() => setDownloadDirectoryLabel("Descargas internas"));
  }, [visible]);

  useEffect(() => {
    if (
      typeof window === "undefined" ||
      typeof window.dispatchEvent !== "function" ||
      typeof CustomEvent === "undefined"
    ) {
      return;
    }
    if (queueSnapshot.doneCount === lastDoneCount) return;
    setLastDoneCount(queueSnapshot.doneCount);
    window.dispatchEvent(new CustomEvent(LIBRARY_DOWNLOADS_UPDATED));
  }, [lastDoneCount, queueSnapshot.doneCount]);

  useEffect(() => {
    const persistQueueTracks = async () => {
      const completedPlaylistTracks = visiblePlaylistEntries
        .map((entry) => {
          const item = queueByEntryId.get(entry.id);
          if (!item || item.status !== "done" || !item.downloadUrl) return null;
          return buildQueueTrack(entry, item);
        })
        .filter(Boolean) as Track[];

      if (preview && preview.type !== "playlist" && singleQueueItem?.status === "done" && singleQueueItem.downloadUrl) {
        const singleEntry: DownloadPlaylistEntry = {
          id: singleQueueItem.entryId,
          index: 1,
          title: preview.title,
          source: preview.source,
          durationSeconds: preview.durationSeconds ?? null,
          durationLabel: preview.durationLabel,
          thumbnail: preview.thumbnail,
          webpageUrl: preview.originalUrl,
        };
        completedPlaylistTracks.push(buildQueueTrack(singleEntry, singleQueueItem));
      }

      if (!completedPlaylistTracks.length) return;
      await Promise.all(completedPlaylistTracks.map((track) => persistDownloadedTrack(track).catch(() => track)));
    };

    persistQueueTracks().catch(() => {});
  }, [preview, queueByEntryId, singleQueueItem, visiblePlaylistEntries]);

  const resetAnalyzeFlow = useCallback(() => {
    setPreview(null);
    setPlaylistFormats(null);
    setSelectedPlaylistIds([]);
    setHiddenPlaylistIds([]);
    setPlaylistOrder([]);
    setErrorMessage("");
  }, []);

  useEffect(() => {
    if (!visible || isAutoClearing) return;
    if (!queueSnapshot.totalCount) return;
    if (queueSnapshot.activeCount > 0 || queueSnapshot.errorCount > 0) return;
    if (queueSnapshot.doneCount !== queueSnapshot.totalCount) return;

    const doneIds = queueSnapshot.items.map((item) => item.entryId);
    if (!doneIds.length) return;

    setIsAutoClearing(true);
    removeQueuedDownloads(doneIds)
      .then(() => {
        setQueueSnapshot(EMPTY_SNAPSHOT);
        setUrlValue("");
        resetAnalyzeFlow();
      })
      .catch(() => {})
      .finally(() => setIsAutoClearing(false));
  }, [isAutoClearing, queueSnapshot, resetAnalyzeFlow, visible]);

  useEffect(() => {
    if (!blockedEntryIds.size) return;
    setSelectedPlaylistIds((current) =>
      current.filter((entryId) => !blockedEntryIds.has(entryId)),
    );
  }, [blockedEntryIds]);

  const handleAnalyze = async () => {
    const normalizedUrl = urlValue.trim();
    if (!normalizedUrl) {
      setErrorMessage("Pega un enlace antes de analizar.");
      return;
    }

    setIsAnalyzing(true);
    setErrorMessage("");

    try {
      const result = await analyzeDownloadUrl(normalizedUrl);
      setPreview(result);
      if (result.audioFormats.length) setSelectedAudioId(result.audioFormats[0].id);
      if (result.videoFormats.length) setSelectedVideoId(result.videoFormats[0].id);
      setMode(result.audioFormats.length || result.type === "playlist" ? "audio" : "video");

      if (result.type === "playlist") {
        const allIds = result.entries.map((entry) => entry.id);
        setSelectedPlaylistIds(
          allIds.filter((entryId) => !blockedEntryIds.has(entryId)),
        );
        setHiddenPlaylistIds([]);
        setPlaylistOrder(allIds);
        setPlaylistFormats(null);
      } else {
        setPlaylistFormats(null);
        setSelectedPlaylistIds([]);
        setHiddenPlaylistIds([]);
        setPlaylistOrder([]);
      }
    } catch (error) {
      setPreview(null);
      setSelectedPlaylistIds([]);
      setPlaylistOrder([]);
      setErrorMessage(error instanceof Error ? error.message : "No se pudo analizar el enlace.");
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handlePickDirectory = async () => {
    try {
      const preference = await pickDownloadDirectory();
      setDownloadDirectoryLabel(preference.label);
    } catch {
      setErrorMessage("No se pudo elegir la carpeta de guardado.");
    }
  };

  const handleToggleAll = () => {
    if (!preview || preview.type !== "playlist") return;
    const visibleIds = selectablePlaylistEntries.map((entry) => entry.id);

    setSelectedPlaylistIds((current) =>
      allVisibleSelected
        ? current.filter((entryId) => !visibleIds.includes(entryId))
        : Array.from(new Set([...current, ...visibleIds])),
    );
  };

  const handleToggleEntry = (entryId: string) => {
    if (blockedEntryIds.has(entryId)) return;
    setSelectedPlaylistIds((current) =>
      current.includes(entryId)
        ? current.filter((itemId) => itemId !== entryId)
        : [...current, entryId],
    );
  };

  const hidePlaylistEntries = useCallback((entryIds: string[]) => {
    setHiddenPlaylistIds((current) => Array.from(new Set([...current, ...entryIds])));
    setSelectedPlaylistIds((current) => current.filter((entryId) => !entryIds.includes(entryId)));
  }, []);

  const handleTopCancel = () => {
    setUrlValue("");
    setFormatPickerVisible(false);
    resetAnalyzeFlow();
  };

  const enqueueEntryList = useCallback(
    async (entries: DownloadPlaylistEntry[]) => {
      if (!entries.length || !currentSelection) return;

      const payload = entries.map(buildQueueRequestItem);
      const snapshot = await enqueueDownloads(
        mode,
        currentSelection.id,
        currentSelection.label,
        payload,
      );
      setQueueSnapshot(snapshot);
    },
    [currentSelection, mode],
  );

  const handleDownloadSingle = async () => {
    if (!preview || preview.type === "playlist" || !currentSelection) return;

    try {
      setErrorMessage("");
      const snapshot = await enqueueDownloads(
        mode,
        currentSelection.id,
        currentSelection.label,
        [buildSingleQueueRequestItem(preview)],
      );
      setQueueSnapshot(snapshot);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "No se pudo iniciar la descarga.");
    }
  };

  const handleDownloadSelected = async () => {
    if (!preview) return;

    if (preview.type !== "playlist") {
      await handleDownloadSingle();
      return;
    }

    const entries = visiblePlaylistEntries.filter(
      (entry) =>
        selectedPlaylistIds.includes(entry.id) &&
        !blockedEntryIds.has(entry.id),
    );
    if (!entries.length) {
      setErrorMessage("Selecciona al menos una cancion.");
      return;
    }

    try {
      setErrorMessage("");
      await enqueueEntryList(entries);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "No se pudo iniciar la descarga.");
    }
  };

  const handleCancelQueued = async () => {
    try {
      setErrorMessage("");
      const snapshot = await cancelAllQueuedDownloads();
      setQueueSnapshot(snapshot);
      setSelectedPlaylistIds([]);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "No se pudieron cancelar las descargas.");
    }
  };

  const handleDeleteSelected = async () => {
    const selectedIds = [...selectedPlaylistIds];
    if (!selectedIds.length) return;

    try {
      setErrorMessage("");
      const snapshot = await removeQueuedDownloads(selectedIds);
      setQueueSnapshot(snapshot);
      hidePlaylistEntries(selectedIds);
      setPlaylistOrder((current) => current.filter((entryId) => !selectedIds.includes(entryId)));
      if (selectedIds.length >= visiblePlaylistEntries.length) {
        setUrlValue("");
        resetAnalyzeFlow();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "No se pudieron borrar las canciones seleccionadas.");
    }
  };

  const handleDeleteEntry = async (entryId: string) => {
    try {
      setErrorMessage("");
      const snapshot = await removeQueuedDownloads([entryId]);
      setQueueSnapshot(snapshot);
      hidePlaylistEntries([entryId]);
      setPlaylistOrder((current) => current.filter((itemId) => itemId !== entryId));
      if (visiblePlaylistEntries.length <= 1) {
        setUrlValue("");
        resetAnalyzeFlow();
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "No se pudo borrar esta cancion.");
    }
  };

  const handlePrioritizeEntry = async (entryId: string) => {
    const baseOrder =
      playlistOrder.length || !preview || preview.type !== "playlist"
        ? [...playlistOrder]
        : preview.entries.map((entry) => entry.id);

    const currentIndex = baseOrder.indexOf(entryId);
    if (currentIndex <= 0) return;

    const nextOrder = [
      entryId,
      ...baseOrder.filter((itemId) => itemId !== entryId),
    ];
    setPlaylistOrder(nextOrder);

    const queueOrder = nextOrder.filter((itemId) => queueByEntryId.has(itemId));
    if (!queueOrder.length) return;

    try {
      const snapshot = await reorderQueuedDownloads(queueOrder);
      setQueueSnapshot(snapshot);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "No se pudo cambiar la prioridad.");
    }
  };

  const handlePlayEntry = async (entry: DownloadPlaylistEntry) => {
    const item = queueByEntryId.get(entry.id);
    if (!item || item.status !== "done" || !item.downloadUrl) {
      setErrorMessage("Esa cancion aun no termina de descargarse.");
      return;
    }

    const currentTrack = buildQueueTrack(entry, item);
    const queueTracks = finishedPlaylistTracks.length ? finishedPlaylistTracks : [currentTrack];
    await playTrack(currentTrack, queueTracks);
    router.push("/player");
  };

  const handlePlaySingle = async () => {
    if (!preview || preview.type === "playlist" || !singleQueueItem || !singleQueueItem.downloadUrl) {
      setErrorMessage("Todavia no hay una cancion lista para reproducir.");
      return;
    }

    if (singleQueueItem.status !== "done") {
      setErrorMessage("La descarga aun no termina.");
      return;
    }

    const entry: DownloadPlaylistEntry = {
      id: singleQueueItem.entryId,
      index: 1,
      title: preview.title,
      source: preview.source,
      durationSeconds: preview.durationSeconds ?? null,
      durationLabel: preview.durationLabel,
      thumbnail: preview.thumbnail,
      webpageUrl: preview.originalUrl,
    };
    const track = buildQueueTrack(entry, singleQueueItem);
    await playTrack(track, [track]);
    router.push("/player");
  };

  const renderOption = (
    option: DownloadFormatOption,
    selected: boolean,
    onPress: () => void,
  ) => (
    <TouchableOpacity
      key={option.id}
      style={styles.quickDownloadFormatRow}
      activeOpacity={0.85}
      onPress={onPress}
    >
      <View style={styles.quickDownloadFormatIcon}>
        <Ionicons
          name={option.kind === "audio" ? "musical-notes-outline" : "play-outline"}
          size={18}
          color="#8B8B92"
        />
      </View>
      <View style={styles.quickDownloadFormatTextWrap}>
        <Text style={styles.quickDownloadFormatTitle}>{option.label}</Text>
        <Text style={styles.quickDownloadFormatSubtitle}>{option.sublabel}</Text>
      </View>
      <Text style={styles.quickDownloadFormatSize}>{option.sizeLabel}</Text>
      <View style={[styles.quickDownloadRadio, selected && styles.quickDownloadRadioActive]}>
        {selected ? <Ionicons name="checkmark" size={16} color="#0A0A0E" /> : null}
      </View>
    </TouchableOpacity>
  );

  const renderDownloadButton = (
    item: DownloadQueueItem | null | undefined,
    onPress: () => void,
  ) => {
    const status = item?.status;
    const blocked = isBlockedQueueItem(item);
    const busy = isBusyStatus(status);
    const done = status === "done";
    const failed = status === "error";
    const buttonStyle = [
      styles.quickDownloadTrackDownloadBtn,
      (busy || done || failed) && styles.quickDownloadTrackDownloadBtnActive,
      done && styles.quickDownloadTrackDownloadBtnDone,
    ];

    let iconName: keyof typeof Ionicons.glyphMap = "download-outline";
    let label = "Descargar";
    let hint: string | null = null;

    if (status === "queued") {
      iconName = "time-outline";
      label = "En cola";
      hint = "0%";
    } else if (status === "analyzing") {
      iconName = "search-outline";
      label = "Analizando";
      hint = item?.progressPercent ? `${item.progressPercent}%` : null;
    } else if (status === "downloading") {
      iconName = "download-outline";
      label = "Descargando";
      hint = `${item?.progressPercent ?? 0}%`;
    } else if (status === "done") {
      iconName = "checkmark-circle";
      label = "Descargado";
      hint = "100%";
    } else if (status === "error") {
      iconName = blocked ? "ban-outline" : "alert-circle-outline";
      label = blocked ? "Bloqueada" : "Reintentar";
      hint = item?.errorMessage ?? null;
    } else if (status === "cancelled") {
      iconName = "refresh-outline";
      label = "Reintentar";
      hint = null;
    }

    return (
      <TouchableOpacity
        style={buttonStyle}
        activeOpacity={0.88}
        onPress={!busy && !done && !blocked ? onPress : undefined}
      >
        <View style={styles.quickDownloadTrackDownloadMain}>
          <Ionicons name={iconName} size={16} color="#FFFFFF" />
          <Text style={styles.quickDownloadTrackDownloadText}>{label}</Text>
        </View>
        {hint ? <Text style={styles.quickDownloadTrackDownloadPercent}>{hint}</Text> : null}
        {item && (busy || done) ? (
          <View style={styles.quickDownloadTrackMiniBarTrack}>
            <View
              style={[
                styles.quickDownloadTrackMiniBarFill,
                { width: `${Math.max(0, Math.min(100, item.progressPercent))}%` },
              ]}
            />
          </View>
        ) : null}
      </TouchableOpacity>
    );
  };

  return (
    <>
      <ScreenSheet visible={visible} title="Descargar desde URL" onClose={onClose}>
        <View style={styles.quickDownloadScreenContent}>
          <View style={styles.quickDownloadBlock}>
            <View style={styles.quickDownloadInputWrap}>
              <Ionicons name="link-outline" size={18} color="#F59E0B" />
              <TextInput
                placeholder="Pegar texto"
                placeholderTextColor="#8B8B92"
                style={styles.quickDownloadInput}
                value={urlValue}
                onChangeText={setUrlValue}
                testID="quick-download-url-input"
              />
            </View>

            <View style={styles.quickDownloadAnalyzeRow}>
              <TouchableOpacity
                style={styles.quickDownloadGhostBtn}
                activeOpacity={0.85}
                onPress={handleTopCancel}
              >
                <Text style={styles.quickDownloadGhostText}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.quickDownloadAnalyzeBtn,
                  isAnalyzing && styles.quickDownloadAnalyzeBtnDisabled,
                ]}
                activeOpacity={0.85}
                onPress={handleAnalyze}
                disabled={isAnalyzing}
              >
                <Ionicons name="search-outline" size={18} color="#FFF4E6" />
                <Text style={styles.quickDownloadAnalyzeText}>
                  {isAnalyzing ? "Analizando..." : "Analizar"}
                </Text>
              </TouchableOpacity>
            </View>

            {errorMessage ? (
              <View style={styles.quickDownloadStatusBox}>
                <Ionicons name="alert-circle-outline" size={18} color="#F59E0B" />
                <Text style={styles.quickDownloadStatusText}>{errorMessage}</Text>
              </View>
            ) : null}
          </View>

          <View style={styles.quickDownloadBlock}>
            <Text style={styles.quickDownloadLabel}>Que deseas descargar</Text>
            <View style={styles.quickDownloadModeRow}>
              <TouchableOpacity
                style={[
                  styles.quickDownloadModeBtn,
                  mode === "audio" && styles.quickDownloadModeBtnActive,
                ]}
                onPress={() => setMode("audio")}
              >
                <Ionicons
                  name="musical-notes-outline"
                  size={18}
                  color={mode === "audio" ? "#F59E0B" : "#CFC8DA"}
                />
                <Text
                  style={[
                    styles.quickDownloadModeText,
                    mode === "audio" && styles.quickDownloadModeTextActive,
                  ]}
                >
                  Audio
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.quickDownloadModeBtn,
                  mode === "video" && styles.quickDownloadModeBtnActive,
                ]}
                onPress={() => setMode("video")}
              >
                <Ionicons
                  name="play-outline"
                  size={18}
                  color={mode === "video" ? "#F59E0B" : "#CFC8DA"}
                />
                <Text
                  style={[
                    styles.quickDownloadModeText,
                    mode === "video" && styles.quickDownloadModeTextActive,
                  ]}
                >
                  Video
                </Text>
              </TouchableOpacity>
            </View>
          </View>

          <View style={styles.quickDownloadBlock}>
            <Text style={styles.quickDownloadLabel}>
              {mode === "audio" ? "Calidad de audio" : "Calidad de video"}
            </Text>
            <TouchableOpacity
              style={styles.quickDownloadSelect}
              activeOpacity={0.85}
              onPress={() => setFormatPickerVisible(true)}
            >
              <Text style={styles.quickDownloadSelectText}>
                {currentSelection?.label ?? "Analiza un enlace primero"}
              </Text>
              <Ionicons name="chevron-down" size={18} color="#8B8B92" />
            </TouchableOpacity>

            <View style={styles.quickDownloadDualRow}>
              <View style={styles.quickDownloadHalf}>
                <Text style={styles.quickDownloadLabel}>Formato</Text>
                <View style={styles.quickDownloadSelect}>
                  <Text style={styles.quickDownloadSelectText}>
                    {currentSelection?.ext?.toUpperCase() ?? (mode === "audio" ? "MP3" : "MP4")}
                  </Text>
                  <Ionicons name="chevron-down" size={18} color="#8B8B92" />
                </View>
              </View>
              <View style={styles.quickDownloadHalf}>
                <Text style={styles.quickDownloadLabel}>Ruta</Text>
                <TouchableOpacity
                  style={styles.quickDownloadSelect}
                  activeOpacity={0.85}
                  onPress={handlePickDirectory}
                >
                  <Text style={styles.quickDownloadSelectText}>{downloadDirectoryLabel}</Text>
                  <Ionicons name="folder-open-outline" size={18} color="#8B8B92" />
                </TouchableOpacity>
              </View>
            </View>

            {preview && preview.type !== "playlist" ? (
              <View style={styles.quickDownloadInlineActionRow}>
                {singleQueueItem?.status === "done" ? (
                  <TouchableOpacity
                    style={styles.quickDownloadGhostBtn}
                    activeOpacity={0.88}
                    onPress={handlePlaySingle}
                  >
                    <Text style={styles.quickDownloadGhostText}>Reproducir</Text>
                  </TouchableOpacity>
                ) : (
                  <View style={styles.quickDownloadHalf} />
                )}
                <TouchableOpacity
                  style={[
                    styles.quickDownloadInlinePrimaryBtn,
                    isAnalyzing && styles.quickDownloadAnalyzeBtnDisabled,
                  ]}
                  activeOpacity={0.88}
                  onPress={handleDownloadSingle}
                  disabled={isAnalyzing || !preview}
                >
                  <Ionicons
                    name={singleQueueItem?.status === "done" ? "checkmark-circle" : "download-outline"}
                    size={18}
                    color="#0A0A0E"
                  />
                  <Text style={styles.quickDownloadInlinePrimaryText}>
                    {singleQueueItem?.status === "done"
                      ? "Descargado"
                      : singleQueueItem && isBusyStatus(singleQueueItem.status)
                        ? `${singleQueueItem.progressPercent}%`
                        : "Descargar"}
                  </Text>
                </TouchableOpacity>
              </View>
            ) : null}
          </View>

          <QuickDownloadPlaylistScreen
            visible={preview?.type === "playlist"}
            entries={visiblePlaylistEntries}
            selectedIds={selectedPlaylistIds}
            queueItems={queueByEntryId}
            blockedEntryIds={blockedEntryIds}
            queueSnapshot={queueSnapshot}
            allSelected={allVisibleSelected}
            renderDownloadButton={renderDownloadButton}
            onToggleAll={handleToggleAll}
            onToggleEntry={handleToggleEntry}
            onCancelAll={handleCancelQueued}
            onDeleteSelected={handleDeleteSelected}
            onDownloadSelected={handleDownloadSelected}
            onPlayEntry={handlePlayEntry}
            onDownloadEntry={(entry) => enqueueEntryList([entry])}
            onDeleteEntry={handleDeleteEntry}
            onPrioritizeEntry={handlePrioritizeEntry}
          />
        </View>
      </ScreenSheet>

      <ScreenSheet
        visible={formatPickerVisible}
        title={mode === "audio" ? "Calidad de audio" : "Calidad de video"}
        onClose={() => setFormatPickerVisible(false)}
      >
        <View style={styles.quickDownloadFormatsContent}>
          <Text style={styles.quickDownloadFormatsSection}>
            {mode === "audio" ? "Audio disponible" : "Video disponible"}
          </Text>
          {currentOptions.length ? (
            currentOptions.map((option) =>
              renderOption(
                option,
                mode === "audio" ? option.id === selectedAudioId : option.id === selectedVideoId,
                () => {
                  if (mode === "audio") setSelectedAudioId(option.id);
                  else setSelectedVideoId(option.id);
                  setFormatPickerVisible(false);
                },
              ),
            )
          ) : (
            <View style={styles.quickDownloadEmptyState}>
              <Text style={styles.quickDownloadEmptyText}>
                Primero analiza un enlace para ver formatos reales.
              </Text>
            </View>
          )}
        </View>
      </ScreenSheet>
    </>
  );
}
