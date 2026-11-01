export function groupBy<T>(arr: T[], key: (item: T) => string) {
  const groups = new Map<string, T[]>();

  for (const item of arr) {
    const groupKey = key(item);

    if (!groups.has(groupKey)) {
      groups.set(groupKey, []);
    }

    groups.get(groupKey)!.push(item);
  }

  return Array.from(groups.entries()).map(([name, items]) => ({ name, items }));
}
