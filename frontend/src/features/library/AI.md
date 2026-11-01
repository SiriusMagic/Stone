# AI RULES

- Read MAP.md first.
- Open max 2 files per task.
- Never scan full project.
- Edit only the target zone.
- Never edit LibraryPage.tsx unless layout order changes.
- For download changes, prefer `QuickDownloadSheet.tsx` and `QuickDownloadPlaylist*`.
- Library sync must be event-driven or manual scan driven, never periodic polling.
- Any callback used by `useEffect` must be declared before that effect.
- Buttons stay inside their zone unless reused 3+ times.
- Icons come from shared/icon library, not separate files.
- Max zone file: 60 lines.
- Max style file: 50 lines.
- Max shared component: 30 lines.
- If a zone exceeds limit, split inside same zone folder.
- Do not run npm run dev.
- Validate with npm run check.
