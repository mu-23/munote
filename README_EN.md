# MuNote

[中文](README.md)

A local-first Android tablet handwriting notebook and searchable scanned-PDF study app.

MuNote is a clean-room Android project that combines a Notein-like handwriting/PDF workflow with OCR, search, navigation, page organization, and local-first storage. Chinese is the default UI language. No account, cloud notebook service, or paid OCR API is required.

## Current features

### Writing and editing

- Native Android stylus input with historical digitizer samples
- Pressure- and velocity-aware variable-width ink
- Fountain pen, ballpoint, pencil, and highlighter
- Favorite pen colors and widths, with persistent tool settings
- Stroke eraser, partial eraser, multiple eraser sizes, and clear-page action
- Undo / redo
- Lasso move, duplicate, resize, and delete
- Vector shapes: line, rectangle, circle/ellipse, arrow, and triangle
- Ruler mode with snapping to common 15° angles
- Pen / Touch modes: one-finger writing without a stylus, two-finger pan/zoom
- Three-finger inward pinch opens the full-page overview
- Multi-step jump history for returning to previous reading positions

### Notebooks, PDFs, and page management

- Native notebooks with Blank / Ruled / Grid / Dot templates
- Add, delete, duplicate, and drag-reorder native notebook pages
- Change the template of an individual native notebook page
- Imported PDFs keep their original file untouched
- Imported PDFs also support logical delete, duplicate, and drag-reorder operations without rewriting source PDF bytes
- Page thumbnails, bookmarks, bookmark-only filtering, and direct page jump
- Resume at the last viewed page
- In-app split view: two independent views of the same document or two different documents; side-by-side on wide tablets and stacked on narrow layouts

### Images, text, and navigation

- Insert images
- Move, resize, rotate, crop, lock, and delete images
- Save an image as a reusable global sticker and insert it into other documents
- Keyboard text boxes with move, resize, and font-size controls
- User-created outline / chapter entries
- Draggable page links that jump to another page
- Page mutations remap ink, text, images, search indexes, bookmarks, links, and outline entries

### Search and export

- On-device Simplified Chinese OCR for scanned PDFs
- Search image-only/scanned PDF text, jump to matches, and highlight OCR regions
- On-device Chinese handwriting recognition
- Unified search across PDF OCR, handwriting, and typed text
- Large scanned textbooks prioritize OCR near the current reading position
- Flattened PDF export containing images, ink, vector shapes, and typed text
- Export progress and cancellation

### Library and local data

- Responsive cover-thumbnail grid
- First page as the default cover, plus custom covers
- Marquee filenames below covers
- Folders, favorites, and trash
- Rename, restore, and permanent delete
- One-tap complete local backup and restore
- Automatic local backup at most once per day on launch, retaining the latest 7 automatic snapshots
- Backups, documents, images, stickers, and all note sidecars stay inside MuNote's private Android app directory
- **MuNote does not create a backup folder at the Android storage root and does not automatically upload notes to the cloud**
- Chinese / English UI, with **Chinese as the default on first install**

## Data model

Imported source PDFs are stored separately from editable MuNote data. PDF page delete/duplicate/reorder is implemented through a logical page map, so source PDF bytes are not rewritten.

Ink, text, images, OCR, handwriting indexes, internal links, and outline data are stored as document-keyed sidecars. Native notebooks use stable UUID-backed document IDs, so regenerating their lightweight template PDF does not invalidate existing annotations.

## Status

The core feature set now covers the main “textbook PDF + handwritten notes” workflow and continues to build through GitHub Actions.

The next priority is real-device quality rather than adding more surface area: handwriting feel, palm rejection, long-PDF performance, split-view layout, image manipulation, and backup/restore need repeated testing on the target tablet and stylus until the app is reliable for daily use.

Technical details:

- `docs/RESEARCH.md`
- `docs/ARCHITECTURE.md`
