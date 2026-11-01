import { Animated, Image, Text, TouchableOpacity, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Artwork } from "@/src/components/Artwork";

import { styles } from "../styles";
import { DeletedTab, TabKey } from "../types";
import { EmptyHint, EmptyState } from "./shared";
import { formatMs, Track } from "@/src/data/tracks";

type Group = { name: string; items: Track[] };

type PlaylistItem = {
  id: string;
  title: string;
  count: number;
  icon: keyof typeof Ionicons.glyphMap;
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
  t: (key: string) => string;
  scanning: boolean;
  scanProgress: number;
  scanned: boolean;
  spin: Animated.AnimatedInterpolation<string>;
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

export function LibraryTabContent({
  tab,
  filtered,
  groupsByArtist,
  groupsByAlbum,
  groupsByFolder,
  selectedAudioFolder,
  playlistItems,
  videoFoldersVisible,
  currentTrackId,
  t,
  scanning,
  scanProgress,
  scanned,
  spin,
  onPlay,
  onScan,
  onChooseFolder,
  onOpenHiddenFiles,
  onOpenAudioFolder,
  onCloseAudioFolder,
  onOpenVideoFolders,
  onCloseVideoFolders,
  onOpenCreatePlaylist,
  onOpenPlaylistItem,
}: Props) {
  const renderLibraryActions = (showHidden = false) => (
    <View style={styles.scanWrap}>
      <Text style={styles.scanQuestion}>{t("songs_not_found")}</Text>
      <TouchableOpacity
        style={[styles.hiddenFilesBtn, { marginBottom: 12 }]}
        activeOpacity={0.9}
        onPress={onChooseFolder}
      >
        <Ionicons name="folder-open-outline" size={18} color="#F5C518" />
        <Text style={styles.hiddenFilesBtnText}>Elegir carpeta Stone</Text>
      </TouchableOpacity>
      <TouchableOpacity
        activeOpacity={0.9}
        onPress={onScan}
        style={styles.scanBtn}
        testID="library-scan-button"
        disabled={scanning}
      >
        <Animated.View style={{ transform: [{ rotate: scanning ? spin : "0deg" }] }}>
          <Ionicons name={scanning ? "sync" : "scan"} size={20} color="#0A0A0E" />
        </Animated.View>
        <Text style={styles.scanBtnText}>
          {scanning ? `${t("scanning")} ${scanProgress}%` : t("scan_folders")}
        </Text>
      </TouchableOpacity>
      {showHidden ? (
        <TouchableOpacity
          style={styles.hiddenFilesBtn}
          activeOpacity={0.85}
          onPress={() => onOpenHiddenFiles("audio")}
        >
          <Text style={styles.hiddenFilesBtnText}>Ver archivos ocultos</Text>
        </TouchableOpacity>
      ) : null}
      {!scanned && !scanning ? <Text style={styles.scanHint}>{t("scan_hint")}</Text> : null}
    </View>
  );

  if (tab === "videos") {
    if (videoFoldersVisible) {
      return (
        <View>
          <View style={styles.videoFoldersTopRow}>
            <TouchableOpacity style={styles.videoFoldersBackBtn} onPress={onCloseVideoFolders}>
              <Ionicons name="chevron-back" size={28} color="#FFFFFF" />
            </TouchableOpacity>
            <Text style={styles.videoFoldersTitle}>Carpetas de video</Text>
            <TouchableOpacity style={styles.videoFoldersSortBtn} activeOpacity={0.85}>
              <Ionicons name="swap-vertical-outline" size={28} color="#FFFFFF" />
            </TouchableOpacity>
          </View>

          <Text style={styles.sectionTitle}>Almacenamiento interno</Text>
          <TouchableOpacity style={styles.videoFolderCard} activeOpacity={0.85}>
            <View style={styles.videoFolderCardHeader}>
              <View style={styles.videoFolderCardBadge}>
                <Ionicons name="camera-outline" size={20} color="#178BFF" />
              </View>
              <Text style={styles.videoFolderCardTitle}>Camera</Text>
              <Text style={styles.mediaCount}>{filtered.length}</Text>
              <Ionicons name="chevron-forward" size={22} color="#8A8298" />
            </View>
            <View style={styles.videoFolderPreviewRow}>
              {filtered.slice(0, 2).map((track) => (
                <Image key={track.id} source={{ uri: track.cover }} style={styles.videoFolderPreview} />
              ))}
            </View>
          </TouchableOpacity>

          <View style={styles.folderCtaWrap}>
            <Text style={styles.scanQuestion}>¿Videos no encontrados?</Text>
            <TouchableOpacity
              style={styles.hiddenFilesBtn}
              activeOpacity={0.85}
              onPress={() => onOpenHiddenFiles("video")}
            >
              <Text style={styles.hiddenFilesBtnText}>Ver archivos ocultos</Text>
            </TouchableOpacity>
          </View>
        </View>
      );
    }

    if (filtered.length === 0) {
      return <EmptyState icon="videocam-outline" text={t("no_videos")} />;
    }

    return (
      <View>
        <View style={styles.videoListHeader}>
          <Text style={styles.videoCountLabel}>{`${filtered.length} Videos`}</Text>
          <TouchableOpacity style={styles.videoFolderLauncher} onPress={onOpenVideoFolders}>
            <Ionicons name="folder-open-outline" size={24} color="#FFFFFF" />
          </TouchableOpacity>
        </View>

        {filtered.map((track) => (
          <TouchableOpacity
            key={`video-${track.id}`}
            style={styles.mediaRow}
            onPress={() => onPlay(track, filtered)}
            activeOpacity={0.85}
          >
            <Image source={{ uri: track.cover }} style={styles.videoCover} />
            <View style={styles.mediaTextWrap}>
              <Text style={styles.mediaTitle} numberOfLines={2}>
                {track.title}
              </Text>
              <Text style={styles.mediaSubtitle} numberOfLines={1}>
                {`576P | ${formatMs(track.durationMs)}`}
              </Text>
            </View>
          </TouchableOpacity>
        ))}
      </View>
    );
  }

  if (tab === "songs") {
    return (
      <View>
        {filtered.length === 0 ? (
          <>
            <EmptyHint t={t} />
            {renderLibraryActions()}
          </>
        ) : (
          <>
            {filtered.map((track) => (
              <TouchableOpacity
                key={track.id}
                style={styles.mediaRow}
                onPress={() => onPlay(track, filtered)}
                activeOpacity={0.85}
              >
                <View
                  style={[
                    styles.mediaCoverWrap,
                    currentTrackId === track.id && styles.mediaCoverWrapActive,
                  ]}
                >
                  <Artwork uri={track.cover} style={styles.mediaCover} iconSize={24} />
                </View>
                <View style={styles.mediaTextWrap}>
                  <Text style={styles.mediaTitle} numberOfLines={1}>
                    {track.title}
                  </Text>
                  <View style={styles.songMetaRow}>
                    {track.id === filtered[0]?.id ? (
                      <View style={styles.lyricsBadge}>
                        <Text style={styles.lyricsBadgeText}>LETRAS</Text>
                      </View>
                    ) : null}
                    <Text style={styles.mediaSubtitle} numberOfLines={1}>
                      {`${track.artist} - ${track.album}`}
                    </Text>
                  </View>
                </View>
              </TouchableOpacity>
            ))}
            {renderLibraryActions()}
          </>
        )}
      </View>
    );
  }

  if (tab === "playlists") {
    return (
      <View>
        <TouchableOpacity style={styles.addPlaylistRow} activeOpacity={0.85} onPress={onOpenCreatePlaylist}>
          <Ionicons name="add" size={34} color="#C9C3D7" />
          <Text style={styles.addPlaylistText}>Agregar nuevo</Text>
        </TouchableOpacity>

        {playlistItems.map((item) => (
          <TouchableOpacity
            key={item.id}
            style={styles.mediaRow}
            activeOpacity={0.85}
            onPress={() => onOpenPlaylistItem(item.id)}
          >
            {item.cover ? (
              <View style={styles.playlistCoverWrap}>
                <Artwork uri={item.cover} style={styles.mediaCover} iconSize={24} />
              </View>
            ) : (
              <View style={styles.playlistIconCover}>
                <Ionicons name="list-outline" size={36} color="#7E7591" />
                <Ionicons
                  name={item.icon}
                  size={18}
                  color={item.iconColor ?? "#FFFFFF"}
                  style={styles.playlistOverlayIcon}
                />
              </View>
            )}
            <View style={styles.mediaTextWrap}>
              <View style={styles.songMetaRow}>
                {item.icon !== "heart" && item.cover ? (
                  <Ionicons name={item.icon} size={20} color="#FFFFFF" />
                ) : item.icon === "heart" ? (
                  <Ionicons name="heart" size={18} color="#FF6B81" />
                ) : null}
                <Text style={styles.playlistTitle} numberOfLines={2}>
                  {item.title}
                </Text>
              </View>
            </View>
            <Text style={styles.mediaCount}>{item.count}</Text>
          </TouchableOpacity>
        ))}
      </View>
    );
  }

  if (tab === "folders") {
    if (selectedAudioFolder) {
      return (
        <View>
          <View style={styles.videoFoldersTopRow}>
            <TouchableOpacity style={styles.videoFoldersBackBtn} onPress={onCloseAudioFolder}>
              <Ionicons name="chevron-back" size={28} color="#FFFFFF" />
            </TouchableOpacity>
            <Text style={styles.videoFoldersTitle} numberOfLines={1}>
              {selectedAudioFolder.name}
            </Text>
          </View>

          <Text style={styles.sectionTitle}>
            {`${selectedAudioFolder.items.length} cancion${selectedAudioFolder.items.length === 1 ? "" : "es"}`}
          </Text>

          {selectedAudioFolder.items.map((track) => (
            <TouchableOpacity
              key={track.id}
              style={styles.mediaRow}
              onPress={() => onPlay(track, selectedAudioFolder.items)}
              activeOpacity={0.85}
            >
              <View
                style={[
                  styles.mediaCoverWrap,
                  currentTrackId === track.id && styles.mediaCoverWrapActive,
                ]}
              >
                <Artwork uri={track.cover} style={styles.mediaCover} iconSize={24} />
              </View>
              <View style={styles.mediaTextWrap}>
                <Text style={styles.mediaTitle} numberOfLines={1}>
                  {track.title}
                </Text>
                <Text style={styles.mediaSubtitle} numberOfLines={1}>
                  {track.artist}
                </Text>
              </View>
              <Text style={styles.mediaCount}>{formatMs(track.durationMs)}</Text>
            </TouchableOpacity>
          ))}
        </View>
      );
    }

    return (
      <View>
        <Text style={styles.sectionTitle}>Almacenamiento interno</Text>
        {groupsByFolder.length ? (
          groupsByFolder.map((group) => (
            <TouchableOpacity
              key={group.name}
              style={styles.mediaRow}
              activeOpacity={0.85}
              onPress={() => onOpenAudioFolder(group.name)}
            >
              <View style={styles.folderCover}>
                <Ionicons
                  name={group.items[0]?.kind === "video" ? "folder-open" : "folder"}
                  size={42}
                  color="#B5ABC9"
                />
              </View>
              <View style={styles.mediaTextWrap}>
                <Text style={styles.mediaTitle}>{group.name}</Text>
                <Text style={styles.folderSubtitle}>
                  {`${group.items.length} archivo${group.items.length === 1 ? "" : "s"}`}
                </Text>
              </View>
              <Text style={styles.mediaCount}>{group.items.length}</Text>
            </TouchableOpacity>
          ))
        ) : (
          <EmptyState icon="folder-open-outline" text="No hay carpetas todavía" />
        )}

        <View style={styles.folderCtaWrap}>{renderLibraryActions(true)}</View>
      </View>
    );
  }

  if (tab === "artists") {
    return (
      <View>
        {groupsByArtist.length === 0 ? (
          <>
            <EmptyHint t={t} />
            {renderLibraryActions()}
          </>
        ) : (
          groupsByArtist.map((group) => (
            <TouchableOpacity
              key={group.name}
              style={styles.mediaRow}
              onPress={() => onPlay(group.items[0], group.items)}
              activeOpacity={0.85}
            >
              <Image source={{ uri: group.items[0].cover }} style={styles.mediaRoundCover} />
              <View style={styles.mediaTextWrap}>
                <Text style={styles.mediaTitle} numberOfLines={1}>
                  {group.name}
                </Text>
              </View>
              <Text style={styles.mediaCount}>{group.items.length}</Text>
            </TouchableOpacity>
          ))
        )}
      </View>
    );
  }

  return (
    <View>
      {groupsByAlbum.length === 0 ? (
        <>
          <EmptyHint t={t} />
          {renderLibraryActions()}
        </>
      ) : (
        groupsByAlbum.map((group) => (
          <TouchableOpacity
            key={group.name}
            style={styles.mediaRow}
            onPress={() => onPlay(group.items[0], group.items)}
            activeOpacity={0.85}
          >
            <View style={styles.mediaCoverWrapActive}>
              <Image source={{ uri: group.items[0].cover }} style={styles.mediaCover} />
            </View>
            <View style={styles.mediaTextWrap}>
              <Text style={styles.mediaTitle} numberOfLines={1}>
                {group.name}
              </Text>
            </View>
            <Text style={styles.mediaCount}>{group.items.length}</Text>
          </TouchableOpacity>
        ))
      )}
    </View>
  );
}
