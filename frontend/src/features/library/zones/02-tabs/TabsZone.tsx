import { LibraryTabs } from "../../components/LibraryTabs";
import { TabKey } from "../../types";

type Props = {
  tab: TabKey;
  onChange: (tab: TabKey) => void;
  t: (key: string) => string;
};

export function TabsZone(props: Props) {
  return <LibraryTabs tab={props.tab} onChange={props.onChange} t={props.t} />;
}
