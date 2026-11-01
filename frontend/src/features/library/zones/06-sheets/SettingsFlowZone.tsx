import { SettingsMenuProps, SettingsMenuZone } from "./SettingsMenuZone";
import { SettingsPreferencesProps, SettingsPreferencesZone } from "./SettingsPreferencesZone";

export type SettingsFlowProps = SettingsMenuProps & SettingsPreferencesProps;

export function SettingsFlowZone(props: SettingsFlowProps) {
  return (
    <>
      <SettingsMenuZone {...props} />
      <SettingsPreferencesZone {...props} />
    </>
  );
}
