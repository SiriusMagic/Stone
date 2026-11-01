import { HiddenConfigSheet } from "../../components/HiddenConfigSheet";
import { HiddenItemActionSheet } from "../../components/HiddenItemActionSheet";
import { HiddenSortSheet } from "../../components/HiddenSortSheet";
import { SortDirection, SortKey } from "../../types";

type Item = {
  id: string;
  title: string;
  subtitle?: string;
  type?: string;
  category?: string;
  cover?: string;
};

export type HiddenFilesToolsProps = {
  hiddenSortVisible: boolean;
  hiddenConfigVisible: boolean;
  hiddenVideoToAudio: boolean;
  hiddenSortKey: SortKey;
  hiddenSortDirection: SortDirection;
  hiddenActionItem: Item | null;
  onCloseHiddenActions: () => void;
  onCloseHiddenConfig: () => void;
  onToggleHiddenVideoToAudio: () => void;
  onCloseHiddenSort: () => void;
  onChangeHiddenSort: (next: SortKey) => void;
};

export function HiddenFilesToolsZone(props: HiddenFilesToolsProps) {
  return (
    <>
      <HiddenItemActionSheet item={props.hiddenActionItem as never} onClose={props.onCloseHiddenActions} />
      <HiddenConfigSheet
        visible={props.hiddenConfigVisible}
        hiddenVideoToAudio={props.hiddenVideoToAudio}
        onClose={props.onCloseHiddenConfig}
        onToggleVideoToAudio={props.onToggleHiddenVideoToAudio}
      />
      <HiddenSortSheet
        visible={props.hiddenSortVisible}
        sortKey={props.hiddenSortKey}
        sortDirection={props.hiddenSortDirection}
        onClose={props.onCloseHiddenSort}
        onChange={props.onChangeHiddenSort}
      />
    </>
  );
}
