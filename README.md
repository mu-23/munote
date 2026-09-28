# MuNote

Android tablet handwriting + searchable scanned-PDF notebook.

MuNote is a clean-room, local-first Android project aimed at combining a Notein-like handwriting experience with the search/OCR workflow that makes scanned textbooks practical to study.

## Current features

- Native Android stylus ink with historical digitizer samples
- Pressure + velocity-aware variable-width pen
- Highlighter, stroke eraser, undo/redo
- Stylus lasso selection with move, copy, resize, and delete
- Finger-only pinch zoom, pan, and page swipe
- PDF import with a persistent local document library
- Resume at the last viewed page
- Page thumbnail rail, persistent bookmarks, bookmark-only filtering, and direct page jump
- On-device Simplified Chinese scanned-PDF OCR
- Search scanned/image-only PDF text, jump to matches, and highlight the OCR region
- On-device Chinese digital-ink recognition for the user's own handwriting
- Unified search across PDF OCR and handwritten ink
- Flattened PDF export with handwritten annotations, progress, and cancellation
- Rename/delete local library documents
- OCR prioritization around the saved reading position for large scanned textbooks
- No account required and no paid OCR API

The original imported PDF is kept separate from editable MuNote ink. Editing does not destructively rewrite the source PDF.

## Status

The core workflow now builds successfully in GitHub Actions and is ready for repeated real-device testing.

The biggest remaining quality gate is handwriting feel on the target tablet: smoothing, pressure curve, velocity thinning, pen-down/pen-up behavior, palm rejection, and model-specific stylus button handling all need real hardware feedback before they should be considered final.

See:
- `docs/RESEARCH.md`
- `docs/ARCHITECTURE.md`
