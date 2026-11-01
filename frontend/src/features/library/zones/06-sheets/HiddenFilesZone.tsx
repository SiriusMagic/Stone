import { HiddenFilesModal } from "../../components/HiddenFilesModal";
import { DeletedTab, HiddenCategory } from "../../types";
import { HiddenFilesToolsProps, HiddenFilesToolsZone } from "./HiddenFilesToolsZone";

type Item = {
  id: string;
  title: string;
  subtitle?: string;
  type?: string;
  category?: string;
  cover?: string;
};

export type HiddenFilesProps = {
  hiddenFilesVisible: boolean;
  hiddenSearchVisible: boolean;
  hiddenTab: DeletedTab;
  hiddenCategory: HiddenCategory;
  hiddenQuery: string;
  hiddenItems: Item[];
  onCloseHiddenFiles: () => void;
  onBackHiddenFiles: () => void;
  onShowHiddenSearch: () => void;
  onOpenHiddenSort: () => void;
  onOpenHiddenConfig: () => void;
  onChangeHiddenQuery: (value: string) => void;
  onSelectHiddenTab: (next: DeletedTab) => void;
  onSelectHiddenCategory: (next: HiddenCategory) => void;
  onOpenHiddenItem: (itemId: string) => void;
} & HiddenFilesToolsProps;

export function HiddenFilesZone(props: HiddenFilesProps) {
  return (
    <>
      <HiddenFilesModal
        visible={props.hiddenFilesVisible}
        searchVisible={props.hiddenSearchVisible}
        hiddenTab={props.hiddenTab}
        hiddenCategory={props.hiddenCategory}
        hiddenQuery={props.hiddenQuery}
        items={props.hiddenItems as never}
        onClose={props.onCloseHiddenFiles}
        onBack={props.onBackHiddenFiles}
        onShowSearch={props.onShowHiddenSearch}
        onOpenSort={props.onOpenHiddenSort}
        onOpenConfig={props.onOpenHiddenConfig}
        onChangeQuery={props.onChangeHiddenQuery}
        onSelectTab={props.onSelectHiddenTab}
        onSelectCategory={props.onSelectHiddenCategory}
        onOpenItem={props.onOpenHiddenItem}
      />
      <HiddenFilesToolsZone {...props} />
    </>
  );
}
