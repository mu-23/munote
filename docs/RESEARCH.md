# MuNote research notes

Updated: 2026-09-28

## Product target

MuNote is not trying to become another office suite. The target is a tablet-first handwritten notebook with two non-negotiable properties:

1. Ink should feel close to Notein: low latency, pressure-sensitive, stable at normal Chinese handwriting speed, with palm rejection and a minimal toolbar.
2. Imported scanned/image-only PDFs should become searchable in-place: OCR the pages, index the text, search a keyword, and jump directly to the page that contains it.

Obsidian is intentionally excluded from the reference set because its Android tablet interaction model is not a fit for this product.

## Commercial product research

### Notein / Orion Notes

What is worth copying at the interaction-design level (clean-room, not code):
- tablet-first editor instead of desktop UI squeezed onto Android;
- low-latency pressure-sensitive pen;
- compact/immersive toolbar and pen-case style controls;
- PDF annotation is treated as a first-class workflow.

Public product material describes ultra-low-latency handwriting and pressure-sensitive brushes. The main gap for our target is that current OCR/search messaging does not clearly promise the exact scanned-PDF -> full-document OCR -> keyword jump workflow we need.

### TouchNotes / 享做笔记

What is worth copying at the capability level:
- OCR can process PDF/image content;
- search spans PDF/image/handwriting content;
- search results can act as navigation into the note;
- a notebook can remain the primary UI instead of sending the user to a separate scanner application.

The weaknesses we are deliberately not copying are the denser UI and the handwriting feel reported during testing.

## Open-source codebases inspected

### shardulvs/xnotes-android — MIT

Why it is the strongest handwriting reference:
- native Android/Kotlin/Compose;
- stylus detection uses Android MotionEvent tool types;
- historical digitizer samples are consumed, rather than keeping only the final MOVE sample;
- StrokeEngine implements smooth variable-width ink;
- pressure is reshaped with a nonlinear response curve;
- speed pen support measures motion over time rather than a fixed sample count;
- PDF import, page rendering, annotations, page navigation and vector PDF export already exist;
- toolbar layout is explicitly tablet-oriented and customizable;
- native note bundles keep source PDF and editable vector strokes separately.

Important implementation files inspected:
- app/src/main/java/com/xnotes/ui/InfiniteInteraction.kt
- app/src/main/java/com/xnotes/core/stroke/StrokeEngine.kt
- app/src/main/java/com/xnotes/platform/PdfSource.kt
- app/src/main/java/com/xnotes/core/model/Document.kt
- app/src/main/java/com/xnotes/format/DocumentCodec.kt
- app/src/main/java/com/xnotes/ui/Toolbar.kt

Conclusion: best reference for the ink pipeline and tablet editor architecture.

### saber-notes/saber — GPL-3.0

Strengths:
- mature open-source handwriting application;
- large cross-platform feature set;
- PDF import and a full handwritten-note editor;
- substantial production history and community usage.

Reason not chosen as the main base:
- Flutter/Dart rather than native Android;
- larger architecture than we need for a focused Android tablet app;
- GPL-3.0 would impose a different distribution model on a direct code-derived fork.

Conclusion: useful UX/reference implementation, not our cleanest starting architecture.

### pipolarbear/picpocket — MIT

Why it is useful:
- Android/Kotlin;
- clear separation between PDF import, OCR, storage and searchable-PDF generation;
- background OCR pipeline stores per-page OCR text;
- PdfRenderer-based PDF page import;
- ML Kit based OCR;
- searchable PDF generation concept.

Important implementation files inspected:
- domain/ocr/OcrEngine.kt
- domain/ocr/MlKitOcrEngine.kt
- domain/ocr/OcrManager.kt
- domain/pdfimport/PdfPageImporter.kt
- domain/export/SearchablePdfGenerator.kt

Limitation:
- current recognizer in the inspected revision uses the Latin ML Kit recognizer;
- it is a scanner/document application rather than a handwriting notebook.

Conclusion: good reference for the OCR pipeline boundaries, not for the editor.

### meuse24/PDF_Scanner

The repository contains a significantly developed OCR/PDF stack, including:
- TextRecognizerRunner;
- positioned OCR extraction tests;
- PdfPageInputImageLoader;
- SearchablePdfBuilder;
- viewer/search related code.

However the repository currently exposes no root LICENSE file through GitHub. Therefore MuNote does not copy its implementation. It is used only as an architectural research reference.

## MuNote implementation decision

MuNote is a clean-room implementation. We are not copying Notein or TouchNotes code, and we are not importing source from repositories whose licensing is unclear.

The first implementation combines the ideas that survived the comparison:

- native Android + Jetpack Compose shell;
- custom Android View for the live ink surface;
- stylus-only ink capture so fingers remain navigation gestures;
- historical MotionEvent samples;
- causal coordinate smoothing;
- pressure + velocity width response;
- Android PdfRenderer for local PDF rendering;
- bundled/on-device ML Kit Chinese text recognizer for Chinese + Latin page OCR;
- one OCR text record per page;
- live keyword search against the page index;
- search result chips jump directly to the matching page;
- handwriting stored separately from the original PDF so the source stays intact;
- no account and no paid cloud OCR in the MVP.

## What still needs device tuning

A commercial pen feel cannot be recreated from source inspection alone. The final 10-20% depends on the digitizer and pen firmware of the actual tablet. The values that should be tuned from real writing samples are:

- coordinate smoothing factor;
- pressure response exponent;
- minimum/maximum pen width;
- velocity thinning cap;
- pen-down/pen-up cap behavior;
- eraser hit radius;
- whether Xiaomi/Redmi stylus side buttons need model-specific mapping.

The code intentionally keeps these parameters simple so they can be tuned quickly after the first APK is tested on the target tablet.
