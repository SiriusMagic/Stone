import { DeletedItemActionSheet } from "../../components/DeletedItemActionSheet";
import { RecentDeletedModal } from "../../components/RecentDeletedModal";
import { DeletedTab } from "../../types";

type Item = {
  id: string;
  title: string;
  subtitle?: string;
  type?: string;
  category?: string;
  cover?: string;
};

export type DeletedFilesProps = {
  recentDeletedVisible: boolean;
  deletedTab: DeletedTab;
  selectedDeletedIds: string[];
  selectionMode: boolean;
  deletedItems: Item[];
  deletedActionItem: Item | null;
  onCloseRecentDeleted: () => void;
  onSelectDeletedTab: (next: DeletedTab) => void;
  onToggleDeletedSelection: (itemId: string) => void;
  onOpenDeletedItemActions: (itemId: string) => void;
  onSelectAllDeleted: () => void;
  onCloseDeletedActions: () => void;
};

export function DeletedFilesZone(props: DeletedFilesProps) {
  return (
    <>
      <RecentDeletedModal
        visible={props.recentDeletedVisible}
        selectionMode={props.selectionMode}
        deletedTab={props.deletedTab}
        selectedDeletedIds={props.selectedDeletedIds}
        items={props.deletedItems as never}
        onClose={props.onCloseRecentDeleted}
        onSelectTab={props.onSelectDeletedTab}
        onToggleSelection={props.onToggleDeletedSelection}
        onOpenItemActions={props.onOpenDeletedItemActions}
        onSelectAll={props.onSelectAllDeleted}
      />
      <DeletedItemActionSheet item={props.deletedActionItem as never} onClose={props.onCloseDeletedActions} />
    </>
  );
}
