function toFileUri(path: string) {
  if (!path.startsWith("/")) return path;
  return `file://${path}`;
}

export function normalizeExternalUri(path?: string | null) {
  if (!path) return null;
  if (path.startsWith("content://") || path.startsWith("file://")) return path;
  if (path.startsWith("/storage/") || path.startsWith("/sdcard/")) {
    return toFileUri(path);
  }
  return null;
}

export function isExternalMediaUri(path?: string | null) {
  return normalizeExternalUri(path) !== null;
}
