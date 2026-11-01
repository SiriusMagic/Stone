function toLibraryPath(path: string) {
  const encodedPath = encodeURIComponent(path);
  return `/library?externalUri=${encodedPath}`;
}

export function redirectSystemPath({ path }: { path: string }) {
  if (!path) return "/library";
  if (path.startsWith("content://") || path.startsWith("file://")) {
    return toLibraryPath(path);
  }
  return path;
}
