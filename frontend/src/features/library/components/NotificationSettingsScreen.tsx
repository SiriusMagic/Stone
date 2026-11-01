import { ScreenSheet, SettingToggleRow } from "./shared";

type Props = {
  visible: boolean;
  notifyNewFiles: boolean;
  pushNotifications: boolean;
  onClose: () => void;
  onToggleNewFiles: () => void;
  onTogglePush: () => void;
};

export function NotificationSettingsScreen({
  visible,
  notifyNewFiles,
  pushNotifications,
  onClose,
  onToggleNewFiles,
  onTogglePush,
}: Props) {
  return (
    <ScreenSheet
      visible={visible}
      title="Configuracion de notificaciones"
      onClose={onClose}
    >
      <SettingToggleRow
        title="Notificacion de nuevos archivos"
        value={notifyNewFiles}
        onToggle={onToggleNewFiles}
      />
      <SettingToggleRow
        title="Notificacion push"
        subtitle="Recomendar musica que podria gustarme"
        value={pushNotifications}
        onToggle={onTogglePush}
      />
    </ScreenSheet>
  );
}
