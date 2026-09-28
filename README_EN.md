# MuNote

[中文](README.md)

Android tablet handwriting + searchable scanned-PDF notebook.

MuNote is a clean-room, local-first Android project aimed at combining a Notein-like handwriting experience with the OCR/search workflow needed for scanned textbooks.

## Current features

- Native Android stylus ink with historical digitizer samples
- Pressure + velocity-aware variable-width pen
- Pen, highlighter, eraser, undo/redo
- Stylus lasso selection with move, copy, resize, and delete
- Pen/Touch modes: write with one finger when no stylus is available; use two fingers to pan/zoom
- Three-finger inward pinch opens a full-page overview, with multi-step jump history for returning to earlier locations
- Native notebooks with Blank/Ruled/Grid/Dot pages and page append
- PDF import with a persistent local document library and first-page cover grid
- Custom cover images and marquee filenames below each cover
- Resume at the last viewed page
- Page thumbnails, persistent bookmarks, bookmark-only filtering, and direct page jump
- On-device Simplified Chinese scanned-PDF OCR
- Search scanned/image-only PDF text, jump to matches, and highlight the OCR region
- On-device Chinese digital-ink recognition for the user’s own handwriting
- Typed text boxes for keyboard notes, with move/resize/font-size controls and unified PDF/handwriting/text search
- Flattened PDF export with handwriting and typed text, progress, and cancellation
- Rename/delete local library documents
- OCR prioritization around the saved reading position for large scanned textbooks
- No account required and no paid OCR API
- Chinese / English UI, with **Chinese as the default on first install**

The original imported PDF is kept separate from editable MuNote ink/text annotations. Editing does not destructively rewrite the source PDF. Native notebooks use stable document IDs so appending pages does not invalidate existing notes.

## Status

The core workflow builds successfully in GitHub Actions and is ready for repeated real-device testing.

The biggest remaining quality gate is handwriting feel on the target tablet. Coordinate smoothing, pressure response, velocity thinning, pen-down/pen-up behavior, palm rejection, and model-specific stylus button handling all need real hardware feedback before they should be considered final.

Technical details:

- `docs/RESEARCH.md`
- `docs/ARCHITECTURE.md`
