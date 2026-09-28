# MuNote architecture

Updated: 2026-09-28

## Product boundary

MuNote is a tablet-first, local-first handwriting notebook and PDF study app.

The core targets are:

1. responsive stylus/finger handwriting suitable for long study sessions;
2. scanned/image-only PDFs that can be searched and navigated like text documents;
3. editable notes layered over source documents without destructively rewriting imported PDFs;
4. a self-contained local library with page organization, split view, backup/restore, images, text, links, and outline navigation.

Obsidian is not used as a technical base.

## Storage boundary

All MuNote-managed data lives under Android `Context.filesDir`.

Important subdirectories:

- `documents/` — imported PDFs and generated native-notebook background PDFs
- `ink/` — editable vector handwriting and vector shapes
- `indexes/` — scanned-PDF OCR index
- `handwriting-indexes/` — recognized handwriting text/bounds
- `text-notes/` — typed text boxes
- `images/<fingerprint>/` — images inserted into a document
- `image-notes/` — image placement/crop/rotation metadata
- `stickers/` — reusable global sticker library
- `navigation/` — page links and user-created outline entries
- `covers/` — custom library covers
- `backups/` — local backup archives

MuNote does not create a top-level shared-storage folder for automatic backups.

## Document identity and PDF flow

Imported PDF:

PDF picker
-> copy source bytes into app-private `documents/`
-> SHA-256 fingerprint becomes the document identity
-> library metadata stores title, last-open time, resume page, bookmarks, organization state, and logical page order
-> `PdfRenderer` renders pages on demand

The source PDF is never destructively rewritten by ordinary editing.

Imported-PDF page management uses a logical page map:

logical page index
-> source PDF page index
-> `PdfSession`
-> `PdfRenderer`

Delete, duplicate, and reorder therefore change MuNote metadata and annotation indexes rather than source PDF bytes. Export follows the current logical page order.

Native notebook:

create notebook
-> stable UUID-backed document identity
-> generate a lightweight local template PDF
-> Blank / Ruled / Grid / Dot page templates
-> per-page template list
-> rebuild only the lightweight background PDF when page structure/templates change
-> keep all editable sidecars keyed to the stable notebook identity

Native notebooks support add/delete/duplicate/reorder and single-page template changes.

## Page mutation model

Page structure changes must keep every page-keyed sidecar aligned.

For native notebooks and imported PDFs, delete/duplicate/reorder operations remap as applicable:

- vector ink and shapes
- typed text boxes
- inserted images
- PDF OCR index
- handwriting recognition index
- bookmarks and resume page
- internal page links
- custom outline entries

Page-structure mutations are disabled while in split view so two live sessions cannot rebuild/remap the same document concurrently.

## Rendering and split view

`PdfSession` owns:

- `PdfRenderer`
- serialized page access
- bounded bitmap cache
- optional logical-to-source page mapping
- source page count vs logical page count

The editor can open a second `PdfSession` in-app.

Split view supports:

- two independent positions in the same document;
- two different local documents;
- side-by-side layout on wide tablet windows;
- stacked layout on narrow windows.

Same-document split reuses annotation stores while using independent render/navigation UI state. Page-structure editing is disabled until split view is closed.

## Handwriting flow

Stylus/finger input
-> native `InkCanvasView`
-> historical digitizer samples
-> coordinate smoothing
-> pressure + velocity processing
-> normalized page coordinates
-> vector stroke persistence

Input modes:

- Pen mode: stylus writes; fingers navigate.
- Touch mode: one finger writes; two fingers pan/zoom.
- Three-finger inward pinch opens the page overview.

Brushes:

- fountain pen
- ballpoint
- pencil
- highlighter

Pen color/width choices and favorites are persisted locally.

Erasers:

- whole-stroke eraser
- partial/pixel-like vector eraser
- multiple sizes
- clear current page

Undo/redo uses page snapshots so ordinary writing, erasing, lasso transforms, and clear-page edits share the same history path.

## Shapes and ruler

Shapes are stored in the same vector ink model rather than rasterized overlays.

Supported shapes:

- line
- rectangle
- circle / ellipse
- arrow
- triangle

Ruler mode produces a vector straight line and snaps its angle to 15° increments.

Shapes participate in:

- persistence
- undo/redo
- lasso transforms
- erasing
- page remapping
- PDF export

## Lasso

The lasso remains inside `InkCanvasView` so it operates directly on vector strokes.

Stylus lasso
-> polygon hit test
-> selected stroke indices
-> selection bounds
-> move / duplicate / scale / delete
-> persisted page mutation
-> undo/redo history

## Images and stickers

Inserted images use two layers of storage:

- image bytes under `images/<fingerprint>/`
- per-page placement metadata under `image-notes/<fingerprint>.json`

Image metadata includes:

- normalized position
- width/height
- rotation
- crop rectangle
- lock state

The editor supports move, resize, rotate, crop, lock/unlock, and delete.

A selected/cropped image can be copied into the global `stickers/` library. Inserting a sticker creates an independent document image copy, so deleting the library sticker does not remove copies already placed in notes.

## Typed text boxes

Typed notes are stored under `text-notes/<fingerprint>.json`.

Each box keeps:

- normalized position
- width/height
- font size
- text

Text boxes can be moved, resized, deleted, and edited with the Android software keyboard. Their content participates in unified search and flattened PDF export.

## OCR flow

PDF logical page
-> local render at OCR resolution
-> bundled ML Kit Chinese text recognizer
-> line text + normalized bounding rectangles
-> `indexes/<fingerprint>.json`
-> search hit with page/snippet/bounds
-> jump + highlight

Large scanned PDFs start OCR around the saved/current reading position and expand outward.

OCR page indexes are remapped when imported-PDF logical pages are deleted, duplicated, or reordered.

## Handwriting recognition

Editable vector strokes
-> ignore highlighter strokes
-> group into approximate writing lines
-> ML Kit Digital Ink Recognition (`zh-Hani-CN`)
-> persist recognized text + normalized bounds under `handwriting-indexes/<fingerprint>.json`

Recognition is debounced after writing pauses so it does not run in the latency-critical live-ink path.

Unified search combines:

- scanned-PDF OCR
- recognized handwriting
- typed text boxes

## Links and outline

`NavigationStore` keeps two user-controlled navigation surfaces under `navigation/<fingerprint>.json`:

- outline/chapter entries pointing to pages;
- draggable labels on a page that jump to another page.

These targets are remapped during page mutations.

The existing page-history stack is separate: it records recent navigation positions so the user can return after a jump.

## Library organization

`PdfLibrary` owns document-level metadata and supports:

- responsive cover grid
- first logical page as default cover
- custom covers
- rename
- folders
- favorites
- recoverable trash
- permanent delete
- bookmarks
- resume page
- native notebook templates/page count
- imported-PDF logical page order

Permanent delete also removes the document's sidecar data.

## Local backup

`LocalBackupManager` creates ZIP snapshots under `files/backups/`.

Backup scope is the rest of `Context.filesDir`, excluding the backups directory itself. This includes documents, metadata, ink, text, images, stickers, OCR/search indexes, links/outline, covers, and other app-owned note data.

Current policy:

- manual backup on demand;
- automatic backup on launch when the newest automatic snapshot is at least 24 hours old;
- keep the newest 7 automatic snapshots;
- restore from an existing local archive;
- no automatic cloud upload;
- no automatic backup folder at the Android shared-storage root.

## Export

`PdfExporter` currently produces a flattened compatibility PDF.

For each logical page:

page rendered through `PdfSession`
-> inserted images
-> vector ink / shapes
-> typed text boxes
-> Android `PdfDocument`
-> progress callback / cancellation

This prioritizes broad reader compatibility. The source PDF remains untouched.

A future exporter can preserve more original PDF vector/text structure and optionally add a searchable OCR text layer.

## Main modules

- `pdf/PdfSession.kt`
  - app-private PDF opening
  - logical page mapping
  - source/logical page counts
  - serialized `PdfRenderer`
  - bitmap cache

- `pdf/PdfLibrary.kt`
  - persistent library metadata
  - folders/favorites/trash
  - covers/bookmarks/resume
  - native notebook page/template management
  - imported-PDF logical page management

- `pdf/PdfExporter.kt`
  - flattened annotated-PDF export
  - image/ink/shape/text rendering
  - progress + cancellation

- `ink/InkModels.kt`
  - brush, vector stroke, shape, page persistence
  - undo/redo history

- `ink/InkCanvasView.kt`
  - low-latency stylus/finger input
  - pressure/speed response
  - brushes/erasers
  - shapes/ruler
  - lasso transforms

- `ocr/OcrIndex.kt`
  - Chinese scanned-PDF OCR
  - positioned blocks
  - persistent search index
  - page-index remapping

- `handwriting/HandwritingIndex.kt`
  - ML Kit Digital Ink Recognition
  - Chinese model
  - handwritten-text search index

- `text/TextStore.kt`
  - typed text boxes
  - page remapping
  - text search

- `image/ImageStore.kt`
  - per-document inserted images
  - placement/crop/rotation/lock metadata
  - page remapping

- `image/StickerStore.kt`
  - reusable global sticker library

- `navigation/NavigationStore.kt`
  - page links
  - outline entries
  - page-target remapping

- `backup/LocalBackupManager.kt`
  - local ZIP snapshot / restore
  - automatic-backup retention

- `ui/MuNoteApp.kt`
  - library UI
  - editor and toolbars
  - page overview
  - split view
  - image/sticker/text interaction
  - search/navigation
  - backup controls

## Next milestones

### P1 — real-device writing quality

Highest priority.

Use the same handwriting samples in MuNote and a mature note app, then tune:

- coordinate smoothing;
- pressure curve;
- velocity thinning;
- min/max line widths;
- pen-down / pen-up appearance;
- partial eraser feel;
- palm rejection;
- Xiaomi/Redmi stylus button behavior where available.

### P2 — reliability and regression testing

Exercise the state-changing paths repeatedly on a real device:

- page delete/duplicate/reorder for both native notes and imported PDFs;
- same-document and cross-document split view;
- image crop/rotate/lock and sticker reuse;
- page links/outline after page mutations;
- trash/restore/permanent delete;
- full backup -> destructive test changes -> restore.

Add automated store/remapping tests where Android dependencies allow it.

### P3 — large-document performance

Benchmark 300–800 page scanned textbooks:

- render-cache sizing;
- OCR scheduling;
- memory use;
- battery/thermal behavior;
- page overview thumbnail loading;
- split-view memory pressure.

### P4 — export fidelity

Improve beyond flattened compatibility export:

- preserve source PDF vector/text quality where possible;
- optional searchable OCR text layer;
- benchmark output size and memory usage;
- validate page order/duplicates after imported-PDF logical edits.

## Privacy / cost

Scanned-PDF OCR uses an on-device ML Kit Chinese recognizer.

Handwriting recognition uses ML Kit Digital Ink Recognition. The language model is downloaded on demand and recognition then runs locally.

MuNote does not require a paid OCR API, mandatory account, or note-upload service.
