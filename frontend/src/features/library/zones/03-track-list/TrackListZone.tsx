import { useRef } from "react";
import {
  Animated,
  ScrollView,
  View,
} from "react-native";

import { LibraryTabContent } from "../../components/LibraryTabContent";
import { styles } from "../../styles";
import { DeletedTab, TabKey } from "../../types";
import { Track } from "@/src/data/tracks";

type Group = { name: string; items: Track[] };

type PlaylistItem = {
  id: string;
  title: string;
  count: number;
  icon: string;
  iconColor?: string;
  cover?: string;
};

type Props = {
  tab: TabKey;
  filtered: Track[];
  groupsByArtist: Group[];
  groupsByAlbum: Group[];
  groupsByFolder: Group[];
  selectedAudioFolder: Group | null;
  playlistItems: PlaylistItem[];
  videoFoldersVisible: boolean;
  currentTrackId?: string;
  scanning: boolean;
  scanProgress: number;
  scanned: boolean;
  spin: Animated.AnimatedInterpolation<string>;
  t: (key: string) => string;
  contentPanHandlers: any;
  onPlay: (track: Track, queue: Track[]) => void;
  onScan: () => void;
  onChooseFolder: () => void;
  onOpenHiddenFiles: (tab?: DeletedTab) => void;
  onOpenAudioFolder: (folderName: string) => void;
  onCloseAudioFolder: () => void;
  onOpenVideoFolders: () => void;
  onCloseVideoFolders: () => void;
  onOpenCreatePlaylist: () => void;
  onOpenPlaylistItem: (playlistId: string) => void;
};

export function TrackListZone(props: Props) {
  const scrollRef = useRef<ScrollView>(null);

  return (
    <View style={{ flex: 1 }} {...props.contentPanHandlers}>
      <ScrollView
        ref={scrollRef}
        style={{ flex: 1 }}
        contentContainerStyle={[styles.listContent, { paddingBottom: props.currentTrackId ? 130 : 32 }]}
        showsVerticalScrollIndicator={false}
      >
        <LibraryTabContent
          tab={props.tab}
          filtered={props.filtered}
          groupsByArtist={props.groupsByArtist}
          groupsByAlbum={props.groupsByAlbum}
          groupsByFolder={props.groupsByFolder}
          selectedAudioFolder={props.selectedAudioFolder}
          playlistItems={props.playlistItems as any}
          videoFoldersVisible={props.videoFoldersVisible}
          currentTrackId={props.currentTrackId}
          t={props.t}
          scanning={props.scanning}
          scanProgress={props.scanProgress}
          scanned={props.scanned}
          spin={props.spin}
          onPlay={props.onPlay}
          onScan={props.onScan}
          onChooseFolder={props.onChooseFolder}
          onOpenHiddenFiles={props.onOpenHiddenFiles}
          onOpenAudioFolder={props.onOpenAudioFolder}
          onCloseAudioFolder={props.onCloseAudioFolder}
          onOpenVideoFolders={props.onOpenVideoFolders}
          onCloseVideoFolders={props.onCloseVideoFolders}
          onOpenCreatePlaylist={props.onOpenCreatePlaylist}
          onOpenPlaylistItem={props.onOpenPlaylistItem}
        />
      </ScrollView>
    </View>
  );
}
