export type TabKey = "videos" | "songs" | "playlists" | "folders" | "artists" | "albums";

export type SortKey = "recent" | "mostPlayed" | "title" | "artist" | "duration";

export type SortDirection = "asc" | "desc";

export type SleepTimerOption = "off" | "10" | "20" | "30" | "60" | "custom";

export type DeletedTab = "audio" | "video";

export type DeletedItem = {
  id: string;
  type: DeletedTab;
  title: string;
  subtitle: string;
  daysAgo: number;
  cover?: string;
};

export type HiddenCategory = "all" | "voice" | "recordings" | "other";

export type HiddenItem = {
  id: string;
  type: DeletedTab;
  title: string;
  subtitle: string;
  category: HiddenCategory;
  cover?: string;
};

export type DownloadContentType = "single" | "playlist";
export type DownloadMode = "audio" | "video";

export type DownloadFormatOption = {
  id: string;
  kind: DownloadMode;
  label: string;
  sublabel: string;
  sizeBytes?: number | null;
  sizeLabel: string;
  tag?: string | null;
  ext?: string | null;
  formatId?: string | null;
};

export type DownloadPlaylistEntry = {
  id: string;
  index: number;
  title: string;
  source: string;
  durationSeconds?: number | null;
  durationLabel: string;
  thumbnail?: string | null;
  webpageUrl?: string | null;
};

export type DownloadAnalyzeResult = {
  type: DownloadContentType;
  source: string;
  title: string;
  thumbnail?: string | null;
  uploader: string;
  originalUrl: string;
  durationSeconds?: number | null;
  durationLabel: string;
  audioFormats: DownloadFormatOption[];
  videoFormats: DownloadFormatOption[];
  entries: DownloadPlaylistEntry[];
  entryCount: number;
  estimatedTotalSizeLabel?: string | null;
};

export type PlaylistFormatsResult = {
  type: "playlist-formats";
  title: string;
  thumbnail?: string | null;
  source: string;
  selectedCount: number;
  entryCount: number;
  audioFormats: DownloadFormatOption[];
  videoFormats: DownloadFormatOption[];
};

export type DownloadQueueRequestItem = {
  entry_id: string;
  title: string;
  source: string;
  thumbnail?: string | null;
  original_url: string;
  duration_seconds?: number | null;
  duration_label: string;
};

export type DownloadQueueItem = {
  id: string;
  entryId: string;
  title: string;
  source: string;
  thumbnail?: string | null;
  originalUrl: string;
  durationSeconds?: number | null;
  durationLabel: string;
  mode: DownloadMode;
  formatId: string;
  formatLabel: string;
  status: "queued" | "analyzing" | "downloading" | "done" | "error" | "cancelled";
  progressPercent: number;
  downloadedBytes: number;
  totalBytes?: number | null;
  downloadedSizeLabel: string;
  totalSizeLabel: string;
  etaSeconds?: number | null;
  etaLabel?: string | null;
  speedBytesPerSecond?: number | null;
  speedLabel?: string | null;
  downloadUrl?: string | null;
  errorMessage?: string | null;
};

export type DownloadQueueSnapshot = {
  items: DownloadQueueItem[];
  totalCount: number;
  doneCount: number;
  downloadedCount: number;
  activeCount: number;
  errorCount: number;
  remainingCount: number;
  progressPercent: number;
  downloadedBytes: number;
  totalBytes?: number | null;
  etaSeconds?: number | null;
  etaLabel?: string | null;
};
