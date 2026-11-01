from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from threading import Event, RLock, Thread
from typing import Any, Literal
import hashlib
import re
import shutil
import time
import uuid

from yt_dlp import YoutubeDL

from downloads import (
    AUDIO_PRESETS,
    VIDEO_PRESETS,
    DownloadAnalyzeError,
    ensure_formats,
    extract_single_entry_info,
    extract_source,
    extract_thumbnail,
    format_duration,
    format_size,
    normalize_url,
    pick_audio_format,
    pick_video_format,
    resolve_size,
)

try:
    import imageio_ffmpeg  # type: ignore
except ImportError:  # pragma: no cover - optional dependency
    imageio_ffmpeg = None


QueueStatus = Literal["queued", "analyzing", "downloading", "done", "error", "cancelled"]
Mode = Literal["audio", "video"]
MEDIA_SUFFIXES = {".mp3", ".m4a", ".mp4", ".webm", ".aac", ".opus", ".ogg", ".wav", ".mkv"}


class DownloadStop(Exception):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


@dataclass
class QueueItem:
    id: str
    entry_id: str
    title: str
    source: str
    thumbnail: str | None
    original_url: str
    duration_seconds: int | None
    duration_label: str
    mode: Mode
    format_id: str
    format_label: str
    status: QueueStatus = "queued"
    progress_percent: int = 0
    downloaded_bytes: int = 0
    total_bytes: int | None = None
    expected_size_bytes: int | None = None
    eta_seconds: int | None = None
    speed_bytes_per_second: float | None = None
    download_url: str | None = None
    local_path: str | None = None
    error_message: str | None = None
    remove_on_stop: bool = False
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)


class DownloadQueueManager:
    def __init__(self, download_root: Path):
        self.download_root = download_root
        self.download_root.mkdir(parents=True, exist_ok=True)
        self._lock = RLock()
        self._items: dict[str, QueueItem] = {}
        self._order: list[str] = []
        self._active_id: str | None = None
        self._wake = Event()
        self._worker = Thread(target=self._worker_loop, daemon=True, name="download-queue-worker")
        self._worker.start()

    def enqueue(
        self,
        mode: Mode,
        format_id: str,
        format_label: str,
        items: list[dict[str, Any]],
    ) -> dict[str, Any]:
        with self._lock:
            for payload in items:
                entry_id = str(payload.get("entry_id") or payload.get("id") or uuid.uuid4())
                existing = self._items.get(entry_id)

                if existing and existing.status in {"queued", "analyzing", "downloading", "done"}:
                    existing.title = payload.get("title") or existing.title
                    existing.source = payload.get("source") or existing.source
                    existing.thumbnail = payload.get("thumbnail") or existing.thumbnail
                    existing.duration_seconds = payload.get("duration_seconds") or existing.duration_seconds
                    existing.duration_label = payload.get("duration_label") or existing.duration_label
                    existing.mode = mode
                    existing.format_id = format_id
                    existing.format_label = format_label
                    existing.updated_at = time.time()
                    continue

                item = QueueItem(
                    id=str(uuid.uuid4()),
                    entry_id=entry_id,
                    title=payload.get("title") or "Sin titulo",
                    source=payload.get("source") or "origen",
                    thumbnail=payload.get("thumbnail"),
                    original_url=normalize_url(payload.get("original_url") or payload.get("url") or ""),
                    duration_seconds=payload.get("duration_seconds"),
                    duration_label=payload.get("duration_label") or "0:00",
                    mode=mode,
                    format_id=format_id,
                    format_label=format_label,
                )
                self._items[entry_id] = item
                self._order.append(entry_id)

        self._wake.set()
        return self.snapshot()

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            items = [self._serialize_item(self._items[item_id]) for item_id in self._order if item_id in self._items]
            raw_items = [self._items[item_id] for item_id in self._order if item_id in self._items]

            total_count = len(raw_items)
            done_count = sum(1 for item in raw_items if item.status == "done")
            active_count = sum(1 for item in raw_items if item.status in {"queued", "analyzing", "downloading"})
            error_count = sum(1 for item in raw_items if item.status == "error")
            downloaded_bytes = sum(item.downloaded_bytes for item in raw_items)
            total_bytes = sum(item.total_bytes or item.expected_size_bytes or 0 for item in raw_items) or None
            progress_percent = self._compute_progress_percent(raw_items, downloaded_bytes, total_bytes)
            eta_seconds = self._compute_eta_seconds(raw_items, downloaded_bytes, total_bytes)

            return {
                "items": items,
                "totalCount": total_count,
                "doneCount": done_count,
                "downloadedCount": done_count,
                "activeCount": active_count,
                "errorCount": error_count,
                "remainingCount": max(total_count - done_count, 0),
                "progressPercent": progress_percent,
                "downloadedBytes": downloaded_bytes,
                "totalBytes": total_bytes,
                "etaSeconds": eta_seconds,
                "etaLabel": self._format_eta(eta_seconds),
            }

    def remove(self, entry_ids: list[str]) -> dict[str, Any]:
        targets = {str(item_id) for item_id in entry_ids if item_id}
        if not targets:
            return self.snapshot()

        cleanup_paths: list[Path] = []

        with self._lock:
            for entry_id in list(targets):
                item = self._items.get(entry_id)
                if not item:
                    continue
                if self._active_id == entry_id and item.status in {"analyzing", "downloading"}:
                    item.remove_on_stop = True
                    item.status = "cancelled"
                    item.updated_at = time.time()
                    continue

                cleanup_paths.append(self._item_dir(entry_id))
                self._items.pop(entry_id, None)
                self._order = [item_id for item_id in self._order if item_id != entry_id]

        for path in cleanup_paths:
            shutil.rmtree(path, ignore_errors=True)

        return self.snapshot()

    def cancel_all(self) -> dict[str, Any]:
        cleanup_paths: list[Path] = []

        with self._lock:
            for entry_id in list(self._order):
                item = self._items.get(entry_id)
                if not item or item.status == "done":
                    continue

                if self._active_id == entry_id and item.status in {"analyzing", "downloading"}:
                    item.remove_on_stop = True
                    item.status = "cancelled"
                    item.updated_at = time.time()
                    continue

                cleanup_paths.append(self._item_dir(entry_id))
                self._items.pop(entry_id, None)

            self._order = [
                entry_id
                for entry_id in self._order
                if entry_id in self._items and self._items[entry_id].status == "done"
            ]

        for path in cleanup_paths:
            shutil.rmtree(path, ignore_errors=True)

        return self.snapshot()

    def reorder(self, entry_ids: list[str]) -> dict[str, Any]:
        requested = [str(item_id) for item_id in entry_ids if item_id]
        if not requested:
            return self.snapshot()

        with self._lock:
            current = [item_id for item_id in self._order if item_id in self._items]
            anchored = [item_id for item_id in current if item_id == self._active_id]
            movable = [item_id for item_id in current if item_id != self._active_id]
            requested_known = [item_id for item_id in requested if item_id in movable]
            leftover = [item_id for item_id in movable if item_id not in requested_known]
            self._order = anchored + requested_known + leftover

        self._wake.set()
        return self.snapshot()

    def _worker_loop(self) -> None:
        while True:
            self._wake.wait()
            next_entry_id = self._next_entry_id()
            if not next_entry_id:
                self._wake.clear()
                continue
            self._download_one(next_entry_id)

    def _next_entry_id(self) -> str | None:
        with self._lock:
            if self._active_id:
                return self._active_id
            for entry_id in self._order:
                item = self._items.get(entry_id)
                if item and item.status == "queued":
                    self._active_id = entry_id
                    return entry_id
        return None

    def _download_one(self, entry_id: str) -> None:
        with self._lock:
            item = self._items.get(entry_id)
            if not item:
                self._active_id = None
                return
            item.status = "analyzing"
            item.error_message = None
            item.updated_at = time.time()

        try:
            info = extract_single_entry_info(item.original_url)
            self._prepare_item(item, info)
            self._run_download(item, info)
            self._finalize_item(item)
        except DownloadStop:
            self._cancel_item(entry_id)
        except DownloadAnalyzeError as error:
            self._mark_error(entry_id, error.message)
        except Exception as error:  # pragma: no cover - depends on remote sites
            self._mark_error(entry_id, f"No se pudo descargar: {error}")
        finally:
            with self._lock:
                if self._active_id == entry_id:
                    self._active_id = None
            self._wake.set()

    def _prepare_item(self, item: QueueItem, info: dict[str, Any]) -> None:
        formats = ensure_formats(info)
        if item.mode == "audio":
            match = pick_audio_format(
                [fmt for fmt in formats if fmt.get("acodec") not in (None, "none")],
                self._audio_preset(item.format_id),
            )
        else:
            match = pick_video_format(
                [fmt for fmt in formats if fmt.get("vcodec") not in (None, "none")],
                self._video_preset(item.format_id),
            )

        item.expected_size_bytes = resolve_size(match)
        item.total_bytes = item.expected_size_bytes
        item.duration_seconds = item.duration_seconds or info.get("duration")
        item.duration_label = format_duration(item.duration_seconds)
        item.thumbnail = item.thumbnail or extract_thumbnail(info)
        item.source = item.source or extract_source(info)
        item.updated_at = time.time()

    def _run_download(self, item: QueueItem, info: dict[str, Any]) -> None:
        item_dir = self._item_dir(item.entry_id)
        item_dir.mkdir(parents=True, exist_ok=True)

        def progress_hook(payload: dict[str, Any]) -> None:
            self._ensure_not_cancelled(item.entry_id)
            status = payload.get("status")
            downloaded = int(payload.get("downloaded_bytes") or 0)
            total = payload.get("total_bytes") or payload.get("total_bytes_estimate") or item.total_bytes or item.expected_size_bytes
            total_int = int(total) if total else None
            speed = payload.get("speed")
            eta = payload.get("eta")

            with self._lock:
                live = self._items.get(item.entry_id)
                if not live:
                    raise DownloadStop("removed")
                live.status = "downloading"
                live.downloaded_bytes = downloaded
                live.total_bytes = total_int
                live.speed_bytes_per_second = float(speed) if speed else None
                live.eta_seconds = int(eta) if eta is not None else live.eta_seconds
                live.progress_percent = self._percent(downloaded, total_int)
                live.updated_at = time.time()

                if status == "finished":
                    live.progress_percent = 100
                    live.eta_seconds = 0

        opts = self._build_ydl_options(item, item_dir, progress_hook, info)

        with YoutubeDL(opts) as ydl:
            ydl.download([item.original_url])

    def _build_ydl_options(
        self,
        item: QueueItem,
        item_dir: Path,
        progress_hook: Any,
        info: dict[str, Any],
    ) -> dict[str, Any]:
        formats = ensure_formats(info)
        opts: dict[str, Any] = {
            "quiet": True,
            "no_warnings": True,
            "continuedl": True,
            "ignoreerrors": False,
            "noplaylist": True,
            "outtmpl": str(item_dir / "%(title).140B.%(ext)s"),
            "progress_hooks": [progress_hook],
            "writethumbnail": False,
            "skip_download": False,
        }

        if item.mode == "audio":
            preset = self._audio_preset(item.format_id)
            audio_candidates = [fmt for fmt in formats if fmt.get("acodec") not in (None, "none")]
            match = pick_audio_format(audio_candidates, preset)
            format_id = str(match.get("format_id")) if match and match.get("format_id") else None

            if format_id:
                opts["format"] = format_id
            elif item.format_id == "audio-quick":
                opts["format"] = "bestaudio[ext=m4a]/bestaudio[ext=mp4]/bestaudio"
            else:
                opts["format"] = "bestaudio/best"

            if preset.id != "audio-quick":
                ffmpeg_path = self._ffmpeg_location()
                if not ffmpeg_path:
                    raise DownloadAnalyzeError(
                        "ffmpeg_missing",
                        "No pude convertir ese audio a MP3 porque falta FFmpeg.",
                    )
                opts["ffmpeg_location"] = ffmpeg_path
                opts["postprocessors"] = [
                    {
                        "key": "FFmpegExtractAudio",
                        "preferredcodec": "mp3",
                        "preferredquality": str(preset.target_abr),
                    }
                ]
        else:
            preset = self._video_preset(item.format_id)
            video_candidates = [fmt for fmt in formats if fmt.get("vcodec") not in (None, "none")]
            match = pick_video_format(video_candidates, preset)
            format_id = str(match.get("format_id")) if match and match.get("format_id") else None

            if format_id:
                opts["format"] = format_id
            else:
                opts["format"] = f"best[height<={preset.target_height}][ext=mp4]/best[height<={preset.target_height}]"

        return opts

    def _finalize_item(self, item: QueueItem) -> None:
        item_dir = self._item_dir(item.entry_id)
        media_file = self._pick_media_file(item_dir)
        if not media_file:
            raise DownloadAnalyzeError("download_missing", "La descarga termino pero no encontre el archivo final.")

        with self._lock:
            live = self._items.get(item.entry_id)
            if not live:
                return
            live.local_path = str(media_file)
            live.download_url = f"/media/downloads/{media_file.relative_to(self.download_root).as_posix()}"
            live.total_bytes = live.total_bytes or media_file.stat().st_size
            live.downloaded_bytes = live.total_bytes or live.downloaded_bytes
            live.progress_percent = 100
            live.status = "done"
            live.eta_seconds = 0
            live.updated_at = time.time()

    def _cancel_item(self, entry_id: str) -> None:
        cleanup_path = self._item_dir(entry_id)
        remove = False
        with self._lock:
            item = self._items.get(entry_id)
            if not item:
                return
            remove = item.remove_on_stop or item.status == "cancelled"
            if remove:
                self._items.pop(entry_id, None)
                self._order = [item_id for item_id in self._order if item_id != entry_id]
            else:
                item.status = "cancelled"
                item.updated_at = time.time()

        if remove:
            shutil.rmtree(cleanup_path, ignore_errors=True)

    def _mark_error(self, entry_id: str, message: str) -> None:
        with self._lock:
            item = self._items.get(entry_id)
            if not item:
                return
            item.status = "error"
            item.error_message = message
            item.updated_at = time.time()

    def _ensure_not_cancelled(self, entry_id: str) -> None:
        with self._lock:
            item = self._items.get(entry_id)
            if not item:
                raise DownloadStop("removed")
            if item.remove_on_stop or item.status == "cancelled":
                raise DownloadStop("cancelled")

    def _serialize_item(self, item: QueueItem) -> dict[str, Any]:
        total_bytes = item.total_bytes or item.expected_size_bytes
        return {
            "id": item.id,
            "entryId": item.entry_id,
            "title": item.title,
            "source": item.source,
            "thumbnail": item.thumbnail,
            "originalUrl": item.original_url,
            "durationSeconds": item.duration_seconds,
            "durationLabel": item.duration_label,
            "mode": item.mode,
            "formatId": item.format_id,
            "formatLabel": item.format_label,
            "status": item.status,
            "progressPercent": item.progress_percent,
            "downloadedBytes": item.downloaded_bytes,
            "totalBytes": total_bytes,
            "downloadedSizeLabel": format_size(item.downloaded_bytes),
            "totalSizeLabel": format_size(total_bytes, approximate=item.expected_size_bytes is not None and item.total_bytes is None),
            "etaSeconds": item.eta_seconds,
            "etaLabel": self._format_eta(item.eta_seconds),
            "speedBytesPerSecond": item.speed_bytes_per_second,
            "speedLabel": self._format_speed(item.speed_bytes_per_second),
            "downloadUrl": item.download_url,
            "errorMessage": item.error_message,
        }

    def _compute_progress_percent(
        self,
        items: list[QueueItem],
        downloaded_bytes: int,
        total_bytes: int | None,
    ) -> int:
        if total_bytes:
            return self._percent(downloaded_bytes, total_bytes)
        if not items:
            return 0
        done_count = sum(1 for item in items if item.status == "done")
        return round((done_count / len(items)) * 100)

    def _compute_eta_seconds(
        self,
        items: list[QueueItem],
        downloaded_bytes: int,
        total_bytes: int | None,
    ) -> int | None:
        active = next((item for item in items if item.status == "downloading"), None)
        if not active:
            return None
        if total_bytes and active.speed_bytes_per_second and active.speed_bytes_per_second > 0:
            remaining_bytes = max(total_bytes - downloaded_bytes, 0)
            return int(remaining_bytes / active.speed_bytes_per_second)
        return active.eta_seconds

    def _audio_preset(self, preset_id: str):
        for preset in AUDIO_PRESETS:
            if preset.id == preset_id:
                return preset
        return AUDIO_PRESETS[0]

    def _video_preset(self, preset_id: str):
        for preset in VIDEO_PRESETS:
            if preset.id == preset_id:
                return preset
        return VIDEO_PRESETS[0]

    def _ffmpeg_location(self) -> str | None:
        if imageio_ffmpeg is None:
            return None
        try:
            return imageio_ffmpeg.get_ffmpeg_exe()
        except Exception:  # pragma: no cover - external binary lookup
            return None

    def _pick_media_file(self, item_dir: Path) -> Path | None:
        files = [
            path
            for path in item_dir.rglob("*")
            if path.is_file()
            and path.suffix.lower() in MEDIA_SUFFIXES
            and not path.name.endswith(".part")
        ]
        if not files:
            return None
        return sorted(files, key=lambda path: path.stat().st_size, reverse=True)[0]

    def _item_dir(self, entry_id: str) -> Path:
        safe = re.sub(r"[^A-Za-z0-9._-]+", "_", entry_id).strip("._")
        digest = hashlib.sha1(entry_id.encode("utf-8")).hexdigest()[:10]
        prefix = (safe or "item")[:60]
        return self.download_root / f"{prefix}_{digest}"

    def _percent(self, current: int, total: int | None) -> int:
        if not total or total <= 0:
            return 0
        return max(0, min(100, round((current / total) * 100)))

    def _format_eta(self, seconds: int | None) -> str | None:
        if seconds is None:
            return None
        return format_duration(max(int(seconds), 0))

    def _format_speed(self, speed: float | None) -> str | None:
        if not speed:
            return None
        return f"{format_size(int(speed))}/s"


DOWNLOAD_ROOT = Path(__file__).parent / "download_storage"
download_queue = DownloadQueueManager(DOWNLOAD_ROOT)
