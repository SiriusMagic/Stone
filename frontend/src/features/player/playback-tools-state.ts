let stopAfterCurrentTrack = false;
const consumedListeners = new Set<() => void>();

export function armStopAfterCurrentTrack() {
  stopAfterCurrentTrack = true;
}

export function consumeStopAfterCurrentTrack() {
  if (!stopAfterCurrentTrack) return false;
  stopAfterCurrentTrack = false;
  consumedListeners.forEach((listener) => listener());
  return true;
}

export function cancelStopAfterCurrentTrack() {
  stopAfterCurrentTrack = false;
}

export function listenStopAfterCurrentConsumed(listener: () => void) {
  consumedListeners.add(listener);
  return () => { consumedListeners.delete(listener); };
}
