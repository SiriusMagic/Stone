import { LangCode } from "@/src/i18n/translations";
import { LanguageSheet } from "../../components/LanguageSheet";
import { LibrarySortSheet } from "../../components/LibrarySortSheet";
import { QuickDownloadSheet } from "../../components/QuickDownloadSheet";
import { SettingsSheet } from "../../components/SettingsSheet";
import { SleepTimerSheet } from "../../components/SleepTimerSheet";
import { ThemeSheet } from "../../components/ThemeSheet";
import { SleepTimerOption, SortDirection, SortKey } from "../../types";

export type SettingsMenuProps = {
  t: (key: string) => string;
  lang: string;
  languageLabel: string;
  quickDownloadVisible: boolean;
  sortVisible: boolean;
  sortKey: SortKey;
  sortDirection: SortDirection;
  settingsVisible: boolean;
  themeVisible: boolean;
  sleepTimerVisible: boolean;
  langModal: boolean;
  sleepTimerLabel: string;
  themeLabel: string;
  libraryFolderLabel: string;
  hiddenFilesCount: number;
  recentDeletedCount: number;
  themeMode: "dark" | "gray" | "light";
  sleepTimerOption: SleepTimerOption;
  sleepTimerCustomMinutes: number;
  sleepTimerFinishTrack: boolean;
  onCloseQuickDownload: () => void;
  onCloseSort: () => void;
  onChangeSort: (nextSortKey: SortKey) => void;
  onCloseSettings: () => void;
  onPickLibraryFolder: () => void;
  onRescanLibrary: () => void;
  onOpenTheme: () => void;
  onOpenSleepTimer: () => void;
  onOpenHiddenFiles: () => void;
  onOpenRecentDeleted: () => void;
  onOpenPlaybackSettings: () => void;
  onOpenNotifications: () => void;
  onOpenLanguage: () => void;
  onOpenFeedback: () => void;
  onCloseTheme: () => void;
  onSelectTheme: (theme: "dark" | "gray" | "light") => void;
  onCloseSleepTimer: () => void;
  onChangeSleepTimerOption: (next: SleepTimerOption) => void;
  onChangeSleepTimerCustomMinutes: (minutes: number) => void;
  onToggleSleepTimerFinishTrack: () => void;
  onCloseLanguage: () => void;
  onSelectLanguage: (code: LangCode) => void;
};

export function SettingsMenuZone(props: SettingsMenuProps) {
  return (
    <>
      <QuickDownloadSheet visible={props.quickDownloadVisible} onClose={props.onCloseQuickDownload} />
      <LibrarySortSheet
        visible={props.sortVisible}
        title={props.t("sort_by")}
        sortKey={props.sortKey}
        sortDirection={props.sortDirection}
        onClose={props.onCloseSort}
        onChange={props.onChangeSort}
      />
      <SettingsSheet
        visible={props.settingsVisible}
        title={props.t("settings")}
        sleepTimerLabel={props.sleepTimerLabel}
        themeLabel={props.themeLabel}
        languageLabel={props.languageLabel}
        libraryFolderLabel={props.libraryFolderLabel}
        hiddenFilesCount={props.hiddenFilesCount}
        recentDeletedCount={props.recentDeletedCount}
        onClose={props.onCloseSettings}
        onPickLibraryFolder={props.onPickLibraryFolder}
        onRescanLibrary={props.onRescanLibrary}
        onOpenTheme={props.onOpenTheme}
        onOpenSleepTimer={props.onOpenSleepTimer}
        onOpenHiddenFiles={props.onOpenHiddenFiles}
        onOpenRecentDeleted={props.onOpenRecentDeleted}
        onOpenPlaybackSettings={props.onOpenPlaybackSettings}
        onOpenNotifications={props.onOpenNotifications}
        onOpenLanguage={props.onOpenLanguage}
        onOpenFeedback={props.onOpenFeedback}
      />
      <ThemeSheet
        visible={props.themeVisible}
        currentTheme={props.themeMode}
        onClose={props.onCloseTheme}
        onSelectTheme={props.onSelectTheme}
      />
      <SleepTimerSheet
        visible={props.sleepTimerVisible}
        onClose={props.onCloseSleepTimer}
        option={props.sleepTimerOption}
        customMinutes={props.sleepTimerCustomMinutes}
        finishTrack={props.sleepTimerFinishTrack}
        onChangeOption={props.onChangeSleepTimerOption}
        onChangeCustomMinutes={props.onChangeSleepTimerCustomMinutes}
        onToggleFinishTrack={props.onToggleSleepTimerFinishTrack}
      />
      <LanguageSheet
        visible={props.langModal}
        title={props.t("language")}
        currentLang={props.lang}
        onClose={props.onCloseLanguage}
        onSelectLanguage={props.onSelectLanguage}
      />
    </>
  );
}
