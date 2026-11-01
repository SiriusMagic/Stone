from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

from yt_dlp import YoutubeDL


@dataclass(frozen=True)
class AudioPreset:
    id: str
    label: str
    sublabel: str
    target_abr: int
    preferred_exts: tuple[str, ...] = ()
    tag: str | None = None


@dataclass(frozen=True)
class VideoPreset:
    id: str
    label: str
    sublabel: str
    target_height: int
    tag: str | None = None


AUDIO_PRESETS = (
    AudioPreset(
        id="audio-quick",
        label="Rapido",
        sublabel="M4A (128 Kbps), ideal para reproduccion en dispositivos moviles",
        target_abr=128,
        preferred_exts=("m4a", "mp4", "webm"),
    ),
    AudioPreset(
        id="audio-mp3-70",
        label="MP3 clasico (70K)",
        sublabel="Compatible con bocina Bluetooth, telefono movil, coche, reloj, etc.",
        target_abr=70,
        tag="Bajo",
    ),
    AudioPreset(
        id="audio-mp3-128",
        label="MP3 clasico (128K)",
        sublabel="Compatible con bocina Bluetooth, telefono movil, coche, reloj, etc.",
        target_abr=128,
    ),
    AudioPreset(
        id="audio-mp3-160",
        label="MP3 clasico",
        sublabel="MP3 (160 Kbps), compatible con altavoces Bluetooth, telefonos moviles, automoviles, relojes, etc.",
        target_abr=160,
    ),
    AudioPreset(
        id="audio-mp3-320",
        label="MP3 clasico (320K)",
        sublabel="Compatible con bocina Bluetooth, telefono movil, coche, reloj, etc.",
        target_abr=320,
        tag="Lento",
    ),
)

VIDEO_PRESETS = (
    VideoPreset("video-144", "Rapido (144p)", "Video de calidad baja", 144, "Baja"),
    VideoPreset("video-240", "Rapido (240p)", "Calidad baja para reproduccion rapida", 240),
    VideoPreset("video-360", "Rapido (360p)", "Calidad normal para reproduccion rapida", 360),
    VideoPreset("video-480", "Rapido (480p)", "Calidad normal para reproduccion rapida", 480),
    VideoPreset("video-720", "Calidad alta (720p)", "Calidad clara y reproduccion rapida", 720),
    VideoPreset("video-1080", "Calidad alta (1080p)", "Alta definicion para pantalla completa", 1080),
    VideoPreset("video-1440", "Calidad alta (2K)", "Alta definicion para pantalla grande", 1440),
    VideoPreset("video-2160", "Calidad alta (4K)", "Maxima definicion disponible", 2160),
)


class DownloadAnalyzeError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def analyze_url(url: str) -> dict[str, Any]:
    normalized_url = normalize_url(url)
    info = extract_preview_info(normalized_url)
    if info.get("_type") == "playlist" or info.get("entries"):
        return build_playlist_preview(info, normalized_url)
    return build_single_preview(info, normalized_url)


def normalize_entry_url(url: str | None) -> str | None:
    if not url:
        return None

    parsed = urlparse(str(url))
    if not parsed.scheme or not parsed.netloc:
        return str(url)

    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    video_id = query.get("v")

    if parsed.netloc.endswith("youtu.be"):
        video_id = parsed.path.lstrip("/") or video_id

    if video_id:
        clean_query = urlencode({"v": video_id})
        return urlunparse(("https", "www.youtube.com", "/watch", "", clean_query, ""))

    cleaned_query = urlencode(
        [(key, value) for key, value in parse_qsl(parsed.query, keep_blank_values=True) if key not in {"list", "index", "start_radio", "pp"}]
    )
    return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, cleaned_query, parsed.fragment))


def analyze_playlist_formats(url: str, selected_entry_ids: list[str]) -> dict[str, Any]:
    normalized_url = normalize_url(url)
    playlist_info = extract_playlist_entries(normalized_url)
    selected_ids = {item for item in selected_entry_ids if item}

    if not selected_ids:
        raise DownloadAnalyzeError("invalid_selection", "No seleccionaste canciones para calcular formatos.")

    entry_map = {
        build_entry_id(entry, index): entry
        for index, entry in enumerate(iter_valid_entries(playlist_info.get("entries", [])), start=1)
    }
    selected_entries = [entry_map[entry_id] for entry_id in selected_ids if entry_id in entry_map]

    if not selected_entries:
        raise DownloadAnalyzeError("invalid_selection", "No se encontraron canciones validas en la seleccion.")

    playlist_title = playlist_info.get("title") or "Playlist"
    thumbnail = extract_thumbnail(playlist_info) or extract_thumbnail(selected_entries[0])
    source = extract_source(playlist_info)

    totals = {
        preset.id: 0 for preset in (*AUDIO_PRESETS, *VIDEO_PRESETS)
    }
    available = {preset.id: False for preset in (*AUDIO_PRESETS, *VIDEO_PRESETS)}

    for entry in selected_entries:
        entry_url = normalize_entry_url(entry.get("webpage_url") or entry.get("url"))
        if not entry_url:
            continue
        try:
            detailed = extract_single_entry_info(entry_url)
        except DownloadAnalyzeError:
            continue

        formats = ensure_formats(detailed)
        audio_sizes = summarize_audio_sizes(formats)
        video_sizes = summarize_video_sizes(formats)

        for option in audio_sizes:
            if option["sizeBytes"] is None:
                continue
            totals[option["id"]] += option["sizeBytes"]
            available[option["id"]] = True

        for option in video_sizes:
            if option["sizeBytes"] is None:
                continue
            totals[option["id"]] += option["sizeBytes"]
            available[option["id"]] = True

    return {
        "type": "playlist-formats",
        "title": playlist_title,
        "thumbnail": thumbnail,
        "source": source,
        "selectedCount": len(selected_entries),
        "entryCount": len(list(iter_valid_entries(playlist_info.get("entries", [])))),
        "audioFormats": [
            build_format_payload_from_total(preset, totals[preset.id], available[preset.id], "audio")
            for preset in AUDIO_PRESETS
        ],
        "videoFormats": [
            build_format_payload_from_total(preset, totals[preset.id], available[preset.id], "video")
            for preset in VIDEO_PRESETS
        ],
    }


def normalize_url(url: str) -> str:
    value = (url or "").strip()
    if not value:
        raise DownloadAnalyzeError("invalid_url", "Pega una URL antes de analizar.")
    if not value.startswith(("http://", "https://")):
        value = f"https://{value}"
    return value


def extract_preview_info(url: str) -> dict[str, Any]:
    try:
        with YoutubeDL(
            {
                "quiet": True,
                "no_warnings": True,
                "skip_download": True,
                "ignoreerrors": True,
                "extract_flat": "in_playlist",
                "noplaylist": False,
            }
        ) as ydl:
            info = ydl.extract_info(url, download=False)
    except Exception as error:  # pragma: no cover - depends on remote sites
        raise DownloadAnalyzeError("analyze_failed", f"No se pudo analizar esa URL: {error}") from error

    if not info:
        raise DownloadAnalyzeError("unsupported", "No pude obtener informacion de esa URL.")

    return info


def extract_playlist_entries(url: str) -> dict[str, Any]:
    info = extract_preview_info(url)
    if info.get("_type") != "playlist" and not info.get("entries"):
        raise DownloadAnalyzeError("unsupported", "La URL no corresponde a una playlist.")
    return info


def extract_single_entry_info(url: str) -> dict[str, Any]:
    candidates = []
    normalized = normalize_entry_url(url)
    if normalized:
        candidates.append(normalized)
    if url and url not in candidates:
        candidates.append(url)

    last_error: Exception | None = None

    for candidate in candidates:
        try:
            with YoutubeDL(
                {
                    "quiet": True,
                    "no_warnings": True,
                    "skip_download": True,
                    "ignoreerrors": True,
                    "noplaylist": True,
                }
            ) as ydl:
                info = ydl.extract_info(candidate, download=False)
        except Exception as error:  # pragma: no cover - depends on remote sites
            last_error = error
            continue

        if info:
            return info

    if last_error:
        error_text = str(last_error)
        if "Sign in to confirm you’re not a bot" in error_text or "Sign in to confirm you're not a bot" in error_text:
            raise DownloadAnalyzeError(
                "youtube_auth_required",
                "YouTube bloqueo esa cancion en este momento. Prueba con otra o vuelve a intentarlo mas tarde.",
            ) from last_error
        raise DownloadAnalyzeError("analyze_failed", f"No se pudo revisar una cancion de la playlist: {last_error}") from last_error

    parsed = urlparse(normalized or url)
    if "youtube.com" in parsed.netloc or "youtu.be" in parsed.netloc:
        raise DownloadAnalyzeError(
            "youtube_auth_required",
            "YouTube bloqueo esa cancion en este momento. Prueba con otra o vuelve a intentarlo mas tarde.",
        )

    raise DownloadAnalyzeError("unsupported", "No pude leer una de las canciones seleccionadas.")


def build_single_preview(info: dict[str, Any], original_url: str) -> dict[str, Any]:
    formats = ensure_formats(info)
    return {
        "type": "single",
        "source": extract_source(info),
        "title": info.get("title") or "Sin titulo",
        "thumbnail": extract_thumbnail(info),
        "uploader": info.get("uploader") or info.get("channel") or "Origen desconocido",
        "originalUrl": original_url,
        "durationSeconds": info.get("duration"),
        "durationLabel": format_duration(info.get("duration")),
        "audioFormats": summarize_audio_sizes(formats),
        "videoFormats": summarize_video_sizes(formats),
        "entries": [],
        "entryCount": 0,
        "estimatedTotalSizeLabel": None,
    }


def build_playlist_preview(info: dict[str, Any], original_url: str) -> dict[str, Any]:
    entries = []
    for index, entry in enumerate(iter_valid_entries(info.get("entries", [])), start=1):
        entries.append(
            {
                "id": build_entry_id(entry, index),
                "index": index,
                "title": entry.get("title") or f"Cancion {index}",
                "source": extract_source(entry),
                "durationSeconds": entry.get("duration"),
                "durationLabel": format_duration(entry.get("duration")),
                "thumbnail": extract_thumbnail(entry),
                "webpageUrl": normalize_entry_url(entry.get("webpage_url") or entry.get("url")) or "",
            }
        )

    return {
        "type": "playlist",
        "source": extract_source(info),
        "title": info.get("title") or "Playlist",
        "thumbnail": extract_thumbnail(info) or (entries[0]["thumbnail"] if entries else None),
        "uploader": info.get("uploader") or info.get("channel") or "Origen desconocido",
        "originalUrl": original_url,
        "durationSeconds": None,
        "durationLabel": f"{len(entries)} canciones",
        "audioFormats": [],
        "videoFormats": [],
        "entries": entries,
        "entryCount": len(entries),
        "estimatedTotalSizeLabel": None,
    }


def summarize_audio_sizes(formats: list[dict[str, Any]]) -> list[dict[str, Any]]:
    items = []
    audio_candidates = [fmt for fmt in formats if fmt.get("acodec") not in (None, "none")]

    for preset in AUDIO_PRESETS:
        match = pick_audio_format(audio_candidates, preset)
        size_bytes = resolve_size(match) if match else None
        items.append(
            {
                "id": preset.id,
                "kind": "audio",
                "label": preset.label,
                "sublabel": preset.sublabel,
                "sizeBytes": size_bytes,
                "sizeLabel": format_size(size_bytes),
                "tag": preset.tag,
                "ext": match.get("ext") if match else None,
                "formatId": str(match.get("format_id")) if match and match.get("format_id") else None,
            }
        )

    return items


def summarize_video_sizes(formats: list[dict[str, Any]]) -> list[dict[str, Any]]:
    items = []
    video_candidates = [fmt for fmt in formats if fmt.get("vcodec") not in (None, "none")]
    audio_fallback = pick_audio_format([fmt for fmt in formats if fmt.get("acodec") not in (None, "none")], AUDIO_PRESETS[0])
    audio_size = resolve_size(audio_fallback) or 0

    for preset in VIDEO_PRESETS:
        match = pick_video_format(video_candidates, preset)
        if match:
            size_bytes = resolve_size(match)
            if size_bytes is not None and match.get("acodec") in (None, "none"):
                size_bytes += audio_size
        else:
            size_bytes = None
        items.append(
            {
                "id": preset.id,
                "kind": "video",
                "label": preset.label,
                "sublabel": preset.sublabel,
                "sizeBytes": size_bytes,
                "sizeLabel": format_size(size_bytes),
                "tag": preset.tag,
                "ext": match.get("ext") if match else None,
                "formatId": str(match.get("format_id")) if match and match.get("format_id") else None,
            }
        )

    return items


def build_format_payload_from_total(preset: AudioPreset | VideoPreset, total: int, available: bool, kind: str) -> dict[str, Any]:
    return {
        "id": preset.id,
        "kind": kind,
        "label": preset.label,
        "sublabel": preset.sublabel,
        "sizeBytes": total if available else None,
        "sizeLabel": format_size(total if available else None, approximate=available),
        "tag": preset.tag,
        "ext": None,
        "formatId": None,
    }


def ensure_formats(info: dict[str, Any]) -> list[dict[str, Any]]:
    formats = info.get("formats")
    if isinstance(formats, list) and formats:
        return [item for item in formats if isinstance(item, dict)]
    requested = info.get("requested_formats")
    if isinstance(requested, list) and requested:
        return [item for item in requested if isinstance(item, dict)]
    return []


def iter_valid_entries(entries: Iterable[Any]) -> Iterable[dict[str, Any]]:
    for entry in entries or []:
        if isinstance(entry, dict):
            yield entry


def build_entry_id(entry: dict[str, Any], index: int) -> str:
    return str(entry.get("id") or entry.get("url") or entry.get("webpage_url") or index)


def extract_thumbnail(info: dict[str, Any]) -> str | None:
    thumbnails = info.get("thumbnails")
    if isinstance(thumbnails, list) and thumbnails:
        for item in reversed(thumbnails):
            if isinstance(item, dict) and item.get("url"):
                return str(item["url"])
    thumbnail = info.get("thumbnail")
    return str(thumbnail) if thumbnail else None


def extract_source(info: dict[str, Any]) -> str:
    webpage = info.get("webpage_url") or info.get("url")
    if webpage:
        host = urlparse(str(webpage)).hostname or ""
        if host:
            return host.replace("www.", "")
    extractor = info.get("extractor") or info.get("extractor_key")
    return str(extractor or "origen")


def resolve_size(fmt: dict[str, Any] | None) -> int | None:
    if not fmt:
        return None
    value = fmt.get("filesize") or fmt.get("filesize_approx")
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def pick_audio_format(candidates: list[dict[str, Any]], preset: AudioPreset) -> dict[str, Any] | None:
    if not candidates:
        return None

    def score(item: dict[str, Any]) -> tuple[int, int, int]:
        abr = int(item.get("abr") or item.get("tbr") or 0)
        ext = str(item.get("ext") or "")
        ext_penalty = 0 if not preset.preferred_exts or ext in preset.preferred_exts else 1
        audio_only_penalty = 0 if item.get("vcodec") in (None, "none") else 1
        return (abs(abr - preset.target_abr), ext_penalty, audio_only_penalty)

    return sorted(candidates, key=score)[0]


def pick_video_format(candidates: list[dict[str, Any]], preset: VideoPreset) -> dict[str, Any] | None:
    if not candidates:
        return None

    def score(item: dict[str, Any]) -> tuple[int, int, int]:
        height = int(item.get("height") or 0)
        has_audio_penalty = 0 if item.get("acodec") not in (None, "none") else 1
        ext_penalty = 0 if str(item.get("ext") or "") == "mp4" else 1
        return (abs(height - preset.target_height), has_audio_penalty, ext_penalty)

    return sorted(candidates, key=score)[0]


def format_duration(seconds: Any) -> str:
    if not seconds:
        return "0:00"
    try:
        total = int(seconds)
    except (TypeError, ValueError):
        return "0:00"
    minutes, secs = divmod(total, 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours}:{minutes:02d}:{secs:02d}"
    return f"{minutes}:{secs:02d}"


def format_size(value: int | None, approximate: bool = False) -> str:
    if value is None:
        return "Tamano no disponible"
    units = ("B", "KB", "MB", "GB", "TB")
    size = float(value)
    unit = units[0]
    for unit in units:
        if size < 1024 or unit == units[-1]:
            break
        size /= 1024
    prefix = "≈" if approximate else ""
    if unit == "B":
        return f"{prefix}{int(size)} {unit}"
    return f"{prefix}{size:.1f} {unit}"
