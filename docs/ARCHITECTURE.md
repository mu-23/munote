# MuNote architecture

Updated: 2026-09-28

## Product boundary

MuNote is a tablet-first handwriting notebook, not an office suite.

The core product target is:
1. handwriting that is responsive enough to compete with dedicated Android note apps;
2. imported scanned/image-only PDFs that can be searched like text documents;
3. local-first storage with editable vector ink and no mandatory account/cloud OCR.

Obsidian is not used as a technical base.

## Document flow

PDF import
-> copy original PDF into app-local `documents/`
-> SHA-256 fingerprint becomes the document identity
-> `PdfRenderer` renders pages on demand
-> the library index stores title, recent-open time, last viewed page and bookmarks

The original PDF is never destructively rewritten by the editor.

## PDF OCR flow

PDF page
-> render at OCR resolution
-> bundled ML Kit Chinese text recognizer
-> retain line text + normalized bounding rectangles
-> persist under `indexes/<fingerprint>.json`
-> search returns page, snippet and match rectangle
-> tapping a result moves the pager and highlights the matching region

Existing early indexes that contained only plain page text are migrated and refreshed with positioned OCR data.

## Handwriting flow

Stylus MotionEvent
-> native `InkCanvasView`
-> reject finger input from the ink path
-> consume historical digitizer samples
-> causal coordinate filtering
-> filtered pressure + measured velocity
-> variable-width vector stroke
-> normalized page coordinates
-> persist under `ink/<fingerprint>.json`

Finger input is handled separately for:
- page swipe at fit-to-page scale;
- pinch zoom;
- panning while zoomed.

Undo/redo is page-snapshot based so pen strokes, erasing and lasso moves share one history model.

## Handwriting recognition/search

Editable vector strokes
-> ignore highlighter strokes
-> group strokes into approximate writing lines
-> ML Kit Digital Ink Recognition (`zh-Hani-CN`)
-> model downloaded on demand once, then recognition runs locally
-> persist recognized line text + normalized bounds under
   `handwriting-indexes/<fingerprint>.json`

Recognition is debounced after writing pauses so it does not run in the latency-critical live-ink path.

Search combines:
- scanned-PDF OCR hits;
- recognized handwriting hits.

The UI labels the source of each hit and uses different highlight colors for PDF text vs handwritten content.

## Lasso

The lasso lives in `InkCanvasView` so it operates directly on vector strokes.

Stylus lasso
-> polygon hit test
-> selected stroke indices
-> dashed selection bounds
-> stylus drag inside bounds
-> normalized translation of selected vector strokes
-> one persisted page mutation
-> one undo/redo history step

Finger navigation remains separate.

## PDF export

`PdfExporter` currently creates a flattened compatibility PDF:

original page rendered locally
-> draw stored vector ink over the page
-> write through Android `PdfDocument`
-> user chooses the destination with the system document picker

The source document is untouched.

A future exporter can preserve/searchable OCR text as an actual PDF text layer instead of flattening the page image.

## Main modules

- `pdf/PdfSession.kt`
  - PDF import/copy
  - hash identity
  - serialized access to `PdfRenderer`
  - bounded bitmap cache
  - reopen local documents

- `pdf/PdfLibrary.kt`
  - persistent local library metadata
  - title
  - last-open time
  - last-page resume
  - bookmarks
  - rename/delete

- `pdf/PdfExporter.kt`
  - flattened annotated-PDF export

- `ocr/OcrIndex.kt`
  - bundled Chinese page OCR
  - positioned OCR blocks
  - persistent per-page index
  - keyword search + snippets

- `handwriting/HandwritingIndex.kt`
  - ML Kit Digital Ink Recognition
  - Simplified Chinese model download
  - line grouping
  - persistent handwritten-text index

- `ink/InkModels.kt`
  - vector stroke model
  - page-level persistent ink
  - undo/redo history

- `ink/InkCanvasView.kt`
  - low-latency stylus input
  - pressure/speed response
  - pen/highlighter/eraser
  - lasso selection + move

- `ui/MuNoteApp.kt`
  - local library
  - compact tablet editor
  - search navigation
  - PDF pager + handwriting overlay
  - thumbnails/bookmarks
  - finger zoom/pan
  - pen presets
  - export action

## Next milestones

### P1 — target-tablet ink tuning
This is now the highest priority quality gate.

Test the same Chinese handwriting sample in MuNote and Notein, then tune:
- coordinate smoothing;
- pressure response exponent;
- min/max stroke width;
- velocity thinning;
- pen-down/pen-up appearance;
- eraser radius;
- palm cancellation behavior;
- Xiaomi/Redmi stylus buttons if needed.

### P2 — notebook-native documents
MuNote is currently strongest as a PDF notebook. The next structural feature should add native blank notebooks and page templates without faking them as imported PDFs.

Desired capabilities:
- blank/grid/dot/ruled pages;
- add/delete/reorder pages;
- stable document ID independent of a source PDF;
- keep the same ink/search/export stack.

### P3 — export/search quality
- preserve original PDF vector/text quality when exporting;
- optional searchable OCR text layer in exported PDFs;
- page-by-page export progress and cancellation;
- benchmark output size on large textbooks.

### P4 — editor polish
- lasso delete/copy/resize in addition to move;
- more pen nib types;
- favorites/recent colors;
- direct page-number jump;
- optional bookmark-only page list;
- more compact landscape/tablet layouts.

### P5 — large-document performance
- prioritize OCR around the current page;
- pause/resume background OCR;
- battery/thermal-aware scheduling;
- benchmark 300-800 page scanned textbooks;
- cap and tune render/OCR caches.

## Privacy / cost

Scanned-PDF OCR uses an on-device bundled ML Kit model.

Handwriting recognition uses ML Kit Digital Ink Recognition. Its language model is downloaded on demand (roughly 20 MB for a language according to ML Kit documentation), after which recognition runs locally.

MuNote does not require a paid OCR API, account, or note upload service.
