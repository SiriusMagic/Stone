import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useAudio } from "./AudioContext";
import { stonePlayback, usesStonePlayback } from "@/src/native/StonePlayback";
import {
  armStopAfterCurrentTrack,
  cancelStopAfterCurrentTrack,
  listenStopAfterCurrentConsumed,
} from "@/src/features/player/playback-tools-state";

export type SleepTimerOption = "off" | "10" | "20" | "30" | "60" | "custom";
type ToolsContext = {
  equalizerEnabled: boolean;
  equalizerValues: number[];
  selectedPreset: string;
  sleepTimerOption: SleepTimerOption;
  sleepTimerCustomMinutes: number;
  sleepTimerFinishTrack: boolean;
  applyEqualizerPreset: (label: string, values: number[]) => void;
  setEqualizerBand: (index: number, value: number) => void;
  setEqualizerEnabled: (value: boolean) => void;
  setSleepTimerCustomMinutes: (value: number) => void;
  setSleepTimerOption: (value: SleepTimerOption) => void;
  toggleSleepTimerFinishTrack: () => void;
};

const Ctx = createContext<ToolsContext | null>(null);

export function PlaybackToolsProvider({ children }: { children: ReactNode }) {
  const { pause } = useAudio();
  const [equalizerEnabled, setEqualizerEnabled] = useState(false);
  const [equalizerValues, setEqualizerValues] = useState<number[]>(Array(10).fill(0));
  const [selectedPreset, setSelectedPreset] = useState("Personalizado");
  const [sleepTimerOption, setSleepTimerOption] = useState<SleepTimerOption>("off");
  const [sleepTimerCustomMinutes, setSleepTimerCustomMinutesState] = useState(90);
  const [sleepTimerFinishTrack, setSleepTimerFinishTrack] = useState(false);

  useEffect(() => {
    if (usesStonePlayback) stonePlayback.setEqualizer(equalizerEnabled, equalizerValues);
  }, [equalizerEnabled, equalizerValues]);
  useEffect(() => listenStopAfterCurrentConsumed(() => setSleepTimerOption("off")), []);
  useEffect(() => {
    cancelStopAfterCurrentTrack();
    if (sleepTimerOption === "off") return;
    const minutes = sleepTimerOption === "custom" ? sleepTimerCustomMinutes : Number(sleepTimerOption);
    const timer = setTimeout(() => {
      if (sleepTimerFinishTrack) armStopAfterCurrentTrack();
      else { pause(); setSleepTimerOption("off"); }
    }, Math.max(1, minutes) * 60_000);
    return () => clearTimeout(timer);
  }, [pause, sleepTimerCustomMinutes, sleepTimerFinishTrack, sleepTimerOption]);

  const applyEqualizerPreset = useCallback((label: string, values: number[]) => {
    setSelectedPreset(label);
    setEqualizerValues(values.map((value) => Math.max(-10, Math.min(10, Math.round(value)))));
    setEqualizerEnabled(true);
  }, []);
  const setEqualizerBand = useCallback((index: number, value: number) => {
    setSelectedPreset("Personalizado");
    setEqualizerEnabled(true);
    setEqualizerValues((current) => current.map((item, itemIndex) => itemIndex === index ? Math.max(-10, Math.min(10, Math.round(value))) : item));
  }, []);
  const setSleepTimerCustomMinutes = useCallback((value: number) => {
    setSleepTimerCustomMinutesState(Math.max(1, Math.min(1439, Math.round(value))));
  }, []);
  const toggleSleepTimerFinishTrack = useCallback(() => {
    setSleepTimerFinishTrack((value) => !value);
  }, []);
  const value = useMemo<ToolsContext>(() => ({
    equalizerEnabled, equalizerValues, selectedPreset, sleepTimerOption, sleepTimerCustomMinutes,
    sleepTimerFinishTrack, applyEqualizerPreset, setEqualizerBand, setEqualizerEnabled,
    setSleepTimerCustomMinutes, setSleepTimerOption, toggleSleepTimerFinishTrack,
  }), [
    applyEqualizerPreset, equalizerEnabled, equalizerValues, selectedPreset, setEqualizerBand,
    setSleepTimerCustomMinutes, sleepTimerCustomMinutes, sleepTimerFinishTrack, sleepTimerOption,
    toggleSleepTimerFinishTrack,
  ]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function usePlaybackTools() {
  const value = useContext(Ctx);
  if (!value) throw new Error("usePlaybackTools must be used inside PlaybackToolsProvider");
  return value;
}
