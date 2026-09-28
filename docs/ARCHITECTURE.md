# MuNote architecture

## MVP data flow

PDF import
-> copy original PDF into app-local documents/
-> SHA-256 fingerprint
-> PdfRenderer renders pages on demand
-> background OCR renders each unindexed page at OCR resolution
-> Chinese ML Kit recognizer extracts text
-> per-page text saved under indexes/<fingerprint>.json

Search
-> query the per-page OCR index
-> return page + snippet
-> tap result
-> pager jumps directly to that PDF page

Handwriting
-> custom InkCanvasView receives stylus MotionEvent only
-> include historical digitizer samples
-> causal smoothing
-> pressure + velocity produce live stroke width
-> normalized coordinates are stored per page
-> ink/<fingerprint>.json persists editable strokes

The original PDF is never destructively rewritten by the editor.

## Modules

- pdf/PdfSession.kt
  - import/copy PDF
  - hash identity
  - serialize access to PdfRenderer
  - bounded bitmap cache

- ocr/OcrIndex.kt
  - bundled Chinese ML Kit OCR
  - persistent per-page index
  - keyword search + snippets

- ink/InkModels.kt
  - vector stroke model
  - page-level persistent ink store

- ink/InkCanvasView.kt
  - stylus input
  - historical samples
  - pressure/speed response
  - pen/highlighter/eraser

- ui/MuNoteApp.kt
  - compact tablet UI
  - PDF picker
  - background OCR status
  - search result navigation
  - PDF pager + handwriting overlay
  - floating-style bottom tool controls

## Next milestones

### P1 — first real-device tuning
- install debug APK on the target tablet;
- record 30-60 seconds of normal Chinese handwriting;
- compare MuNote to Notein using the same pen and page scale;
- tune smoothing, width and velocity constants.

### P2 — search precision
- retain OCR block bounding boxes instead of only page text;
- highlight the matching OCR region after jumping;
- next/previous match controls;
- optionally index the user's own handwriting with a separate recognizer.

### P3 — editor polish
- pinch zoom without breaking stylus capture;
- page thumbnail rail;
- pen preset popover similar in compactness to Notein without copying its artwork;
- custom colors and widths;
- stroke eraser + pixel eraser modes;
- redo stack;
- lasso selection.

### P4 — PDF output
- export original PDF plus vector ink;
- optional searchable PDF text layer;
- preserve source PDF page quality.

## Privacy / cost

The MVP OCR model runs on-device. It does not require an API key, subscription or cloud OCR account. The project does not upload note content anywhere.
