import React, { createContext, useContext, useMemo, useRef, useState, ReactNode, useEffect, useCallback } from "react";
import { useAudioPlayer, useAudioPlayerStatus, setAudioModeAsync } from "expo-audio";
import { PermissionsAndroid, Platform } from "react-native";
import { listenStonePlayback, listenStonePlaybackCommand, stonePlayback, usesStonePlayback } from "@/src/native/StonePlayback";
import { Track } from "@/src/data/tracks";
import { DeletedItem, HiddenItem } from "@/src/features/library/types";
import { storage } from "@/src/utils/storage";
import { consumeStopAfterCurrentTrack } from "@/src/features/player/playback-tools-state";

type RepeatMode = "off" | "all" | "one";

type AudioCtx = {
  current: Track | null;
  currentIndex: number;
  queue: Track[];
  playCounts: Record<string, number>;
  favoriteTrackIds: string[];
  shuffle: boolean;
  repeat: RepeatMode;
  volumePercent: number;
  playbackRate: number;
  hiddenItems: HiddenItem[];
  deletedItems: DeletedItem[];
  playTrack: (track: Track, queue?: Track[]) => Promise<void>;
  pause: () => void;
  toggle: () => void;
  next: () => void;
  prev: () => void;
  seekMs: (ms: number) => Promise<void>;
  moveQueueItem: (fromIndex: number, toIndex: number) => void;
  reorderQueue: (tracks: Track[]) => void;
  updateTrackLyrics: (trackId: string, lyrics: string[], syncedLyrics: Track["syncedLyrics"]) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  setShuffleEnabled: (value: boolean) => void;
  setRepeatMode: (mode: RepeatMode) => void;
  setVolumePercent: (value: number) => void;
  changeVolumeBy: (delta: number) => void;
  setPlaybackRate: (value: number) => void;
  toggleFavorite: (trackId: string) => void;
  hideTrack: (track: Track) => void;
  deleteTrack: (track: Track) => void;
};

type AudioProgressCtx = {
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
};

const Ctx = createContext<AudioCtx | null>(null);
const ProgressCtx = createContext<AudioProgressCtx | null>(null);
const AUDIO_CURRENT_TRACK_KEY = "@app/audio-current-track-id";
const AUDIO_QUEUE_KEY = "@app/audio-queue";
const AUDIO_PLAY_COUNTS_KEY = "@app/audio-play-counts";
const AUDIO_VOLUME_KEY = "@app/audio-volume-percent";
const AUDIO_PLAYBACK_RATE_KEY = "@app/audio-playback-rate";

async function ensureNotificationPermission() {
  if (Platform.OS !== "android" || Number(Platform.Version) < 33) return;
  const permission = PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS;
  if (!(await PermissionsAndroid.check(permission))) await PermissionsAndroid.request(permission);
}
const AUDIO_FAVORITES_KEY = "@app/audio-favorite-track-ids";
const AUDIO_HIDDEN_ITEMS_KEY = "@app/audio-hidden-items";
const AUDIO_DELETED_ITEMS_KEY = "@app/audio-deleted-items";

function trackToHiddenItem(track: Track): HiddenItem {
  return {
    id: track.id,
    type: track.kind === "video" ? "video" : "audio",
    title: track.title,
    subtitle: track.artist || track.album || "Descargas",
    category: "other",
    cover: track.cover,
  };
}

function trackToDeletedItem(track: Track): DeletedItem {
  return {
    id: track.id,
    type: track.kind === "video" ? "video" : "audio",
    title: track.title,
    subtitle: track.artist || track.album || "Descargas",
    daysAgo: 0,
    cover: track.cover,
  };
}

export function AudioProvider({ children }: { children: ReactNode }) {
  const player = useAudioPlayer(null);
  const status = useAudioPlayerStatus(player);
  const [nativeStatus, setNativeStatus] = useState({ playing: false, positionSeconds: 0, durationSeconds: 0 });
  const activeStatus = usesStonePlayback
    ? { playing: nativeStatus.playing, currentTime: nativeStatus.positionSeconds, duration: nativeStatus.durationSeconds }
    : status;

  const [queue, setQueue] = useState<Track[]>([]);
  const [currentIndex, setCurrentIndex] = useState<number>(-1);
  const [playCounts, setPlayCounts] = useState<Record<string, number>>({});
  const [shuffle, setShuffle] = useState(false);
  const [repeat, setRepeat] = useState<RepeatMode>("off");
  const [volumePercent, setVolumePercentState] = useState(100);
  const [playbackRate, setPlaybackRateState] = useState(1);
  const [favoriteTrackIds, setFavoriteTrackIds] = useState<string[]>([]);
  const [hiddenItems, setHiddenItems] = useState<HiddenItem[]>([]);
  const [deletedItems, setDeletedItems] = useState<DeletedItem[]>([]);
  const suppressAutoAdvanceUntilRef = useRef(0);
  const statusRef = useRef(activeStatus);

  // refs for use inside handlers
  const queueRef = useRef(queue);
  const indexRef = useRef(currentIndex);
  const shuffleRef = useRef(shuffle);
  const repeatRef = useRef(repeat);
  const volumeRef = useRef(volumePercent);
  const syncWebMediaVolume = useCallback((value: number) => {
    if (Platform.OS !== "web" || typeof document === "undefined") return;
    const mediaVolume = Math.max(0, Math.min(1, value / 100));
    document.querySelectorAll("audio, video").forEach((node) => {
      const mediaNode = node as HTMLMediaElement;
      mediaNode.volume = mediaVolume;
      mediaNode.muted = mediaVolume <= 0;
    });
  }, []);
  const applyVolumeFromUser = useCallback((value: number) => {
    const nextValue = Math.max(0, Math.min(300, Math.round(value)));
    if (usesStonePlayback) {
      stonePlayback.setVolumeFromUser(nextValue)?.then((next) => {
        volumeRef.current = next.volumePercent;
        setVolumePercentState(next.volumePercent);
      });
    } else {
      player.volume = Math.max(0, Math.min(1, nextValue / 100));
      syncWebMediaVolume(nextValue);
      storage.setItem(AUDIO_VOLUME_KEY, nextValue);
    }
    return nextValue;
  }, [player, syncWebMediaVolume]);
  useEffect(() => { queueRef.current = queue; }, [queue]);
  useEffect(() => { indexRef.current = currentIndex; }, [currentIndex]);
  useEffect(() => { shuffleRef.current = shuffle; }, [shuffle]);
  useEffect(() => { repeatRef.current = repeat; }, [repeat]);
  useEffect(() => { volumeRef.current = volumePercent; }, [volumePercent]);
  useEffect(() => { statusRef.current = activeStatus; }, [activeStatus]);
  useEffect(() => listenStonePlayback((next) => {
    setNativeStatus({ playing: next.playing, positionSeconds: next.positionSeconds, durationSeconds: next.durationSeconds });
    volumeRef.current = next.volumePercent;
    setVolumePercentState(next.volumePercent);
  }).remove, []);
  useEffect(() => {
    if (usesStonePlayback) return;
    player.volume = Math.max(0, Math.min(1, volumePercent / 100));
    syncWebMediaVolume(volumePercent);
  }, [player, syncWebMediaVolume, volumePercent]);
  useEffect(() => {
    if (!usesStonePlayback) return;
    stonePlayback.status()?.then((next) => {
      setNativeStatus({ playing: next.playing, positionSeconds: next.positionSeconds, durationSeconds: next.durationSeconds });
      volumeRef.current = next.volumePercent;
      setVolumePercentState(next.volumePercent);
    });
  }, []);

  useEffect(() => {
    try {
      if (usesStonePlayback) stonePlayback.setRate(playbackRate);
      else {
        player.setPlaybackRate(playbackRate);
        player.shouldCorrectPitch = true;
      }
    } catch {}
    storage.setItem(AUDIO_PLAYBACK_RATE_KEY, playbackRate);
  }, [player, playbackRate]);

  useEffect(() => {
    if (usesStonePlayback) return;
    setAudioModeAsync({
      playsInSilentMode: true,
      shouldPlayInBackground: true,
      interruptionMode: "doNotMix",
    }).catch(() => {});
  }, []);

  useEffect(() => {
    (async () => {
      const savedTrackId = await storage.getItem<string | null>(AUDIO_CURRENT_TRACK_KEY, null);
      const savedQueue = await storage.getItem<Track[]>(AUDIO_QUEUE_KEY, []);
      const savedPlayCounts = await storage.getItem<Record<string, number>>(AUDIO_PLAY_COUNTS_KEY, {});
      const savedVolume = await storage.getItem<number>(AUDIO_VOLUME_KEY, 100);
      const savedPlaybackRate = await storage.getItem<number>(AUDIO_PLAYBACK_RATE_KEY, 1);
      const savedFavoriteTrackIds = await storage.getItem<string[]>(AUDIO_FAVORITES_KEY, []);
      const savedHiddenItems = await storage.getItem<HiddenItem[]>(AUDIO_HIDDEN_ITEMS_KEY, []);
      const savedDeletedItems = await storage.getItem<DeletedItem[]>(AUDIO_DELETED_ITEMS_KEY, []);
      setPlayCounts(savedPlayCounts ?? {});
      if (!usesStonePlayback) setVolumePercentState(applyVolumeFromUser(savedVolume ?? 100));
      setPlaybackRateState(savedPlaybackRate ?? 1);
      setFavoriteTrackIds(savedFavoriteTrackIds ?? []);
      setHiddenItems(savedHiddenItems ?? []);
      setDeletedItems(savedDeletedItems ?? []);
      const nextQueue = savedQueue?.length ? savedQueue : [];
      setQueue(nextQueue);
      if (!savedTrackId) return;
      const savedIndex = nextQueue.findIndex((track) => track.id === savedTrackId);
      if (savedIndex < 0) return;
      setCurrentIndex(savedIndex);

      try {
        if (usesStonePlayback) {
          const track = nextQueue[savedIndex];
          await stonePlayback.load(track.localUri || track.url, track.title, track.artist, track.cover);
        }
        else player.replace({ uri: nextQueue[savedIndex].localUri || nextQueue[savedIndex].url });
      } catch {}
    })();
  }, [applyVolumeFromUser, player]);

  const current = currentIndex >= 0 && currentIndex < queue.length ? queue[currentIndex] : null;

  const playAtIndex = useCallback(async (idx: number, q: Track[]) => {
    if (idx < 0 || idx >= q.length) return;
    const tr = q[idx];
    setCurrentIndex(idx);
    storage.setItem(AUDIO_CURRENT_TRACK_KEY, tr.id);
    setPlayCounts((currentCounts) => {
      const nextCounts = {
        ...currentCounts,
        [tr.id]: (currentCounts[tr.id] ?? 0) + 1,
      };
      storage.setItem(AUDIO_PLAY_COUNTS_KEY, nextCounts);
      return nextCounts;
    });
    try {
      if (usesStonePlayback) {
        await ensureNotificationPermission();
        stonePlayback.setQueue(q.map((item) => ({ id: item.id, title: item.title, artist: item.artist })), idx);
        await stonePlayback.load(tr.localUri || tr.url, tr.title, tr.artist, tr.cover);
        stonePlayback.play();
      } else {
        player.replace({ uri: tr.localUri || tr.url });
        player.play();
      }
    } catch (e) {
      console.log("audio replace error", e);
    }
  }, [player]);

  const playTrack = useCallback(async (track: Track, q?: Track[]) => {
    suppressAutoAdvanceUntilRef.current = Date.now() + 700;
    const newQueue = q && q.length ? q : queueRef.current;
    const idx = newQueue.findIndex((t) => t.id === track.id);
    if (q && q.length) setQueue(newQueue);
    await playAtIndex(idx >= 0 ? idx : 0, newQueue);
  }, [playAtIndex]);

  useEffect(() => {
    storage.setItem(AUDIO_QUEUE_KEY, queue);
  }, [queue]);

  useEffect(() => {
    if (!current) return;
    storage.setItem(AUDIO_CURRENT_TRACK_KEY, current.id);
    if (usesStonePlayback) return;
    try {
      player.setActiveForLockScreen(true, {
        title: current.title,
        artist: current.artist,
        albumTitle: current.album,
        artworkUrl: current.cover || undefined,
      }, {
        showSeekBackward: true,
        showSeekForward: true,
      });
    } catch {}
  }, [current]);

  const toggle = useCallback(async () => {
    if (indexRef.current < 0) {
      playAtIndex(0, queueRef.current);
      return;
    }
    if (statusRef.current.playing) {
      if (usesStonePlayback) stonePlayback.pause();
      else player.pause();
    } else {
      if (usesStonePlayback) {
        await ensureNotificationPermission();
        const q = queueRef.current;
        stonePlayback.setQueue(q.map((item) => ({ id: item.id, title: item.title, artist: item.artist })), indexRef.current);
        stonePlayback.play();
      }
      else player.play();
    }
  }, [playAtIndex, player]);

  const pause = useCallback(() => {
    try {
      if (usesStonePlayback) stonePlayback.pause();
      else player.pause();
    } catch {}
  }, [player]);

  const computeNext = useCallback((dir: 1 | -1) => {
    const q = queueRef.current;
    if (!q.length) return -1;
    const cur = indexRef.current;
    if (shuffleRef.current && dir === 1) {
      // random different index
      if (q.length === 1) return 0;
      let r = cur;
      while (r === cur) r = Math.floor(Math.random() * q.length);
      return r;
    }
    if (cur < 0) return 0;
    let n = cur + dir;
    if (n >= q.length) n = 0;
    if (n < 0) n = q.length - 1;
    return n;
  }, []);

  const next = useCallback(() => {
    suppressAutoAdvanceUntilRef.current = Date.now() + 700;
    const n = computeNext(1);
    if (n >= 0) playAtIndex(n, queueRef.current);
  }, [computeNext, playAtIndex]);

  const prev = useCallback(() => {
    suppressAutoAdvanceUntilRef.current = Date.now() + 700;
    // if more than 3s in, restart current
    if (statusRef.current.currentTime > 3) {
      try { if (usesStonePlayback) stonePlayback.seekTo(0); else player.seekTo(0); } catch {}
      return;
    }
    const n = computeNext(-1);
    if (n >= 0) playAtIndex(n, queueRef.current);
  }, [computeNext, playAtIndex, player]);

  useEffect(() => listenStonePlaybackCommand((command) => {
    if (command === "next") next();
    else if (command === "previous") prev();
    else if (consumeStopAfterCurrentTrack()) return;
    else if (repeatRef.current === "one") {
      stonePlayback.seekTo(0);
      stonePlayback.play();
    } else {
      const nextIndex = computeNext(1);
      if (nextIndex >= 0) playAtIndex(nextIndex, queueRef.current);
    }
  }).remove, [computeNext, next, playAtIndex, prev]);

  const seekMs = useCallback(async (ms: number) => {
    const durationMs = Math.floor((statusRef.current.duration || 0) * 1000);
    const nextMs = Math.max(0, Math.min(Math.round(ms), durationMs || Math.round(ms)));
    try {
      if (usesStonePlayback) await stonePlayback.seekTo(nextMs / 1000);
      else await player.seekTo(nextMs / 1000);
    } catch {}
  }, [player]);

  const moveQueueItem = useCallback((fromIndex: number, toIndex: number) => {
    setQueue((prevQueue) => {
      if (
        fromIndex < 0 ||
        toIndex < 0 ||
        fromIndex >= prevQueue.length ||
        toIndex >= prevQueue.length ||
        fromIndex === toIndex
      ) {
        return prevQueue;
      }

      const nextQueue = [...prevQueue];
      const [movedTrack] = nextQueue.splice(fromIndex, 1);
      nextQueue.splice(toIndex, 0, movedTrack);

      setCurrentIndex((prevIndex) => {
        if (prevIndex === fromIndex) return toIndex;
        if (fromIndex < prevIndex && toIndex >= prevIndex) return prevIndex - 1;
        if (fromIndex > prevIndex && toIndex <= prevIndex) return prevIndex + 1;
        return prevIndex;
      });

      return nextQueue;
    });
  }, []);

  const reorderQueue = useCallback((tracks: Track[]) => {
    setQueue(tracks);
    setCurrentIndex((prevIndex) => {
      const currentTrack = queueRef.current[prevIndex];
      if (!currentTrack) return -1;
      return tracks.findIndex((track) => track.id === currentTrack.id);
    });
  }, []);

  const updateTrackLyrics = useCallback((trackId: string, lyrics: string[], syncedLyrics: Track["syncedLyrics"]) => {
    setQueue((items) => items.map((track) => track.id === trackId ? { ...track, lyrics, syncedLyrics } : track));
  }, []);

  const toggleShuffle = useCallback(() => setShuffle((s) => !s), []);
  const cycleRepeat = useCallback(() => {
    setRepeat((r) => (r === "off" ? "all" : r === "all" ? "one" : "off"));
  }, []);
  const setShuffleEnabled = useCallback((value: boolean) => {
    setShuffle(value);
  }, []);
  const setRepeatMode = useCallback((mode: RepeatMode) => {
    setRepeat(mode);
  }, []);
  const setVolumePercent = useCallback((value: number) => {
    const nextValue = applyVolumeFromUser(value);
    volumeRef.current = nextValue;
    setVolumePercentState(nextValue);
  }, [applyVolumeFromUser]);
  const changeVolumeBy = useCallback((delta: number) => {
    const nextValue = applyVolumeFromUser(volumeRef.current + delta);
    volumeRef.current = nextValue;
    setVolumePercentState(nextValue);
  }, [applyVolumeFromUser]);
  const setPlaybackRate = useCallback((value: number) => {
    const nextValue = Math.max(0.5, Math.min(2, Number(value) || 1));
    setPlaybackRateState(nextValue);
  }, []);

  const toggleFavorite = useCallback((trackId: string) => {
    setFavoriteTrackIds((currentIds) => {
      const nextIds = currentIds.includes(trackId)
        ? currentIds.filter((id) => id !== trackId)
        : [trackId, ...currentIds];
      storage.setItem(AUDIO_FAVORITES_KEY, nextIds);
      return nextIds;
    });
  }, []);

  const removeTrackFromQueue = useCallback((trackId: string) => {
    setQueue((prevQueue) => {
      const nextQueue = prevQueue.filter((item) => item.id !== trackId);
      setCurrentIndex((prevIndex) => {
        if (prevIndex < 0) return -1;
        const currentTrack = prevQueue[prevIndex];
        if (!currentTrack) return -1;
        if (currentTrack.id === trackId) {
          try { if (usesStonePlayback) stonePlayback.pause(); else player.pause(); } catch {}
          return -1;
        }
        const nextIndex = nextQueue.findIndex((item) => item.id === currentTrack.id);
        return nextIndex;
      });
      return nextQueue;
    });
  }, [player]);

  const hideTrack = useCallback((track: Track) => {
    const nextItem = trackToHiddenItem(track);
    setHiddenItems((currentItems) => {
      const withoutCurrent = currentItems.filter((item) => item.id !== track.id);
      const nextItems = [nextItem, ...withoutCurrent];
      storage.setItem(AUDIO_HIDDEN_ITEMS_KEY, nextItems);
      return nextItems;
    });
    removeTrackFromQueue(track.id);
  }, [removeTrackFromQueue]);

  const deleteTrack = useCallback((track: Track) => {
    const nextItem = trackToDeletedItem(track);
    setDeletedItems((currentItems) => {
      const withoutCurrent = currentItems.filter((item) => item.id !== track.id);
      const nextItems = [nextItem, ...withoutCurrent];
      storage.setItem(AUDIO_DELETED_ITEMS_KEY, nextItems);
      return nextItems;
    });
    removeTrackFromQueue(track.id);
  }, [removeTrackFromQueue]);

  // Auto-advance on track end
  const didEndRef = useRef(false);
  useEffect(() => {
    if (usesStonePlayback) return;
    if (!activeStatus.duration || activeStatus.duration <= 0) return;
    if (suppressAutoAdvanceUntilRef.current > Date.now()) return;
    // didJustFinish style detection
    if (!activeStatus.playing && activeStatus.currentTime > 0 && activeStatus.duration > 0 && activeStatus.currentTime >= activeStatus.duration - 0.25) {
      if (didEndRef.current) return;
      didEndRef.current = true;
      if (consumeStopAfterCurrentTrack()) return;
      const mode = repeatRef.current;
      if (mode === "one") {
        try {
          if (usesStonePlayback) { stonePlayback.seekTo(0); stonePlayback.play(); }
          else { player.seekTo(0); player.play(); }
        } catch {}
      } else {
        const n = computeNext(1);
        if (n >= 0) playAtIndex(n, queueRef.current);
      }
      setTimeout(() => { didEndRef.current = false; }, 600);
    }
  }, [activeStatus.playing, activeStatus.currentTime, activeStatus.duration, computeNext, playAtIndex, player]);

  const value = useMemo<AudioCtx>(() => ({
    current,
    currentIndex,
    queue,
    playCounts,
    favoriteTrackIds,
    shuffle,
    repeat,
    volumePercent,
    playbackRate,
    hiddenItems,
    deletedItems,
    playTrack,
    pause,
    toggle,
    next,
    prev,
    seekMs,
    moveQueueItem,
    reorderQueue,
    updateTrackLyrics,
    toggleShuffle,
    cycleRepeat,
    setShuffleEnabled,
    setRepeatMode,
    setVolumePercent,
    changeVolumeBy,
    setPlaybackRate,
    toggleFavorite,
    hideTrack,
    deleteTrack,
  }), [current, currentIndex, queue, playCounts, favoriteTrackIds, shuffle, repeat, volumePercent, playbackRate, hiddenItems, deletedItems, playTrack, pause, toggle, next, prev, seekMs, moveQueueItem, reorderQueue, updateTrackLyrics, toggleShuffle, cycleRepeat, setShuffleEnabled, setRepeatMode, setVolumePercent, changeVolumeBy, setPlaybackRate, toggleFavorite, hideTrack, deleteTrack]);

  const progressValue = useMemo<AudioProgressCtx>(() => ({
    isPlaying: !!activeStatus.playing,
    positionMs: Math.floor((activeStatus.currentTime || 0) * 1000),
    durationMs: Math.floor((activeStatus.duration || (current?.durationMs ? current.durationMs / 1000 : 0)) * 1000),
  }), [activeStatus.currentTime, activeStatus.duration, activeStatus.playing, current?.durationMs]);

  return (
    <Ctx.Provider value={value}>
      <ProgressCtx.Provider value={progressValue}>{children}</ProgressCtx.Provider>
    </Ctx.Provider>
  );
}

export function useAudio() {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAudio must be used inside AudioProvider");
  return v;
}

export function useAudioProgress() {
  const value = useContext(ProgressCtx);
  if (!value) throw new Error("useAudioProgress must be used inside AudioProvider");
  return value;
}
