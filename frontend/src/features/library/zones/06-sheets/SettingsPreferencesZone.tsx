import { NotificationSettingsScreen } from "../../components/NotificationSettingsScreen";
import { PlaybackSettingsScreen } from "../../components/PlaybackSettingsScreen";
import { FeedbackFlow } from "../../components/FeedbackFlow";

export type SettingsPreferencesProps = {
  notificationsVisible: boolean;
  playbackSettingsVisible: boolean;
  feedbackVisible: boolean;
  notifyNewFiles: boolean;
  pushNotifications: boolean;
  playWithOthers: boolean;
  gaplessPlayback: boolean;
  crossfadeEnabled: boolean;
  crossfadeSeconds: number;
  onCloseNotifications: () => void;
  onToggleNewFiles: () => void;
  onTogglePush: () => void;
  onClosePlaybackSettings: () => void;
  onCloseFeedback: () => void;
  onTogglePlayWithOthers: () => void;
  onToggleGapless: () => void;
  onToggleCrossfade: () => void;
  onChangeCrossfadeSeconds: (value: number) => void;
};

export function SettingsPreferencesZone(props: SettingsPreferencesProps) {
  return (
    <>
      <NotificationSettingsScreen
        visible={props.notificationsVisible}
        notifyNewFiles={props.notifyNewFiles}
        pushNotifications={props.pushNotifications}
        onClose={props.onCloseNotifications}
        onToggleNewFiles={props.onToggleNewFiles}
        onTogglePush={props.onTogglePush}
      />
      <PlaybackSettingsScreen
        visible={props.playbackSettingsVisible}
        playWithOthers={props.playWithOthers}
        gaplessPlayback={props.gaplessPlayback}
        crossfadeEnabled={props.crossfadeEnabled}
        crossfadeSeconds={props.crossfadeSeconds}
        onClose={props.onClosePlaybackSettings}
        onTogglePlayWithOthers={props.onTogglePlayWithOthers}
        onToggleGapless={props.onToggleGapless}
        onToggleCrossfade={props.onToggleCrossfade}
        onChangeCrossfadeSeconds={props.onChangeCrossfadeSeconds}
      />
      <FeedbackFlow visible={props.feedbackVisible} onClose={props.onCloseFeedback} />
    </>
  );
}
