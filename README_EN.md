# MuNote

[中文](README.md)

Android tablet handwriting + searchable scanned-PDF notebook.

MuNote is a clean-room, local-first Android project aimed at combining a Notein-like handwriting experience with the OCR/search workflow needed for scanned textbooks.

## Current features

- Native Android stylus ink with historical digitizer samples
- Pressure + velocity-aware variable-width pen
- Pen, highlighter, eraser, undo/redo
- Stylus lasso selection with move, copy, resize, and delete
- Finger-only pinch zoom, pan, and page swipe, separated from stylus input
- PDF import with a persistent local document library
- Resume at the last viewed page
- Page thumbnails, persistent bookmarks, bookmark-only filtering, and direct page jump
- On-device Simplified Chinese scanned-PDF OCR
- Search scanned/image-only PDF text, jump to matches, and highlight the OCR region
- On-device Chinese digital-ink recognition for the user’s own handwriting
- Unified search across PDF OCR and handwritten ink
- Flattened PDF export with handwritten annotations, progress, and cancellation
- Rename/delete local library documents
- OCR prioritization around the saved reading position for large scanned textbooks
- No account required and no paid OCR API
- Chinese / English UI, with **Chinese as the default on first install**

The original imported PDF is kept separate from editable MuNote ink. Editing does not destructively rewrite the source PDF.

## Status

The core workflow builds successfully in GitHub Actions and is ready for repeated real-device testing.

The biggest remaining quality gate is handwriting feel on the target tablet. Coordinate smoothing, pressure response, velocity thinning, pen-down/pen-up behavior, palm rejection, and model-specific stylus button handling all need real hardware feedback before they should be considered final.

Technical details:

- `docs/RESEARCH.md`
- `docs/ARCHITECTURE.md`
