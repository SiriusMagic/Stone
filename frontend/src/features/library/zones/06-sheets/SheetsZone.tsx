import { DeletedFilesProps, DeletedFilesZone } from "./DeletedFilesZone";
import { HiddenFilesProps, HiddenFilesZone } from "./HiddenFilesZone";
import { SettingsFlowProps, SettingsFlowZone } from "./SettingsFlowZone";

type Props = SettingsFlowProps & HiddenFilesProps & DeletedFilesProps;

export function SheetsZone(props: Props) {
  return (
    <>
      <SettingsFlowZone {...props} />
      <HiddenFilesZone {...props} />
      <DeletedFilesZone {...props} />
    </>
  );
}
