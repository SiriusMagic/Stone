# Project Rules

Use this project with minimal reading and zone-based edits.

## Read Order

1. Read `C:\Users\ZIRAKUSA\BotCaja\musica\MAP.md`.
2. If the task is in the frontend, read `C:\Users\ZIRAKUSA\BotCaja\musica\frontend\MAP.md`.
3. Open the target file.
4. Open at most one direct dependency.

## Fast Rules

- Do not scan the full repo unless the task is truly unclear.
- Do not run `npm run dev`.
- Validate with `npm run build` or `npm run check` in `frontend`.
- If localhost is already up, do not restart it.
- Prefer visual files near 100 lines and split before passing 150.
- Reusable buttons and repeated action chips go to a nearby `shared` component.
- When splitting, keep one owner file for state and pass props/handlers to child files.
- Split a visual file before adding more UI if it is already large.

## Key Zones

- full library screen -> `frontend/app/library.tsx`
- full player screen -> `frontend/app/player.tsx`
- download sheet -> `frontend/src/features/library/components/QuickDownloadSheet.tsx`
- playlist download toolbar -> `frontend/src/features/library/components/QuickDownloadPlaylistSummary.tsx`
- playlist download rows -> `frontend/src/features/library/components/QuickDownloadPlaylistRow.tsx`
- library tabs -> `frontend/src/features/library/components/LibraryTabs.tsx`
- library content -> `frontend/src/features/library/components/LibraryTabContent.tsx`
