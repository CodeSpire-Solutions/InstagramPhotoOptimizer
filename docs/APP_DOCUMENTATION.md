# Instagram Photo Optimizer: Full Documentation

## 1. Overview
This app optimizes images locally on-device for Instagram uploads. It supports single and batch workflows (ZIP), offers before/after canvas previews, and allows manual rotation of the optimized result directly in the output file.

## 2. End-to-End User Flow
1. User selects `Mode`: `Post` or `Story`.
2. User optionally configures `Advanced Settings`.
3. User picks one or more images via `Select Photos`.
4. App renders `Original Canvas` preview.
5. User starts optimization.
6. For a single image: app creates an output file and renders it in `Result Canvas`.
7. User can rotate the result left/right; rotation is persisted in output.
8. User can share or save result.
9. For multiple images: app creates a ZIP and offers share/save.

## 3. All Options Explained

### 3.1 Mode
- `Post (1080x1350)`
: Target profile for feed posts.
- `Story (1080x1920)`
: Target profile for stories.

### 3.2 Advanced Settings
- `Export Size Policy`
  - `Adaptive Best`: higher target size (about 3.6 MB target) with quality-first behavior.
  - `Strict 1.6MB`: tighter output size target (about 1.6 MB), potentially stronger compression.

- `Resolution Profile`
  - `Standard IG`: more conservative resolution and processing load.
  - `High Detail`: higher target resolution for better detail retention.

- `Depth Data for Social Media`
  - `Optimize (may lose)`: re-encode path for stronger control over size/quality; depth/XMP may be lost.
  - `Preserve (no re-encode)`: if depth-like metadata is detected, app can preserve original compatibility path.

- `Anti-Aliasing Strength`
  - `Off / Low / Medium / High`.
  - Influences smoothing behavior during downscaling.

- `HDR Handling`
  - Appears meaningfully only when HDR-like input is detected.
  - `Preserve HDR`: keeps HDR-compatible source path to avoid SDR flattening.
  - `Force SDR Optimize`: runs the standard SDR optimization path.
  - For SDR images, this setting is effectively irrelevant.

## 4. Canvas and Result Behavior

### 4.1 Original Canvas
- Shows selected source image preview.
- Tap opens enlarged preview.

### 4.2 Result Canvas
- Shows optimized output preview.
- Tap opens fullscreen + zoom.
- `↺ Left` / `Right ↻` rotates the result by 90°.
- Rotation is persisted to output so save/share use the same orientation.

## 5. Technical Image Pipeline

### 5.1 Decode
- Uses `ImageDecoder` with software allocator.
- Target color space set to `sRGB` for SDR optimization path.
- Decode sample size computed from source/target dimensions.

### 5.2 Resize
- Multi-step downscaling strategy to reduce visual artifacts.
- `Paint` uses filter/dither/antialias flags.
- Optional blur pass may be applied in high downscale ratios.

### 5.3 JPEG Compression
- Quality-search loop to fit size target while retaining quality.
- Iterative fallback may reduce dimensions if target cannot be reached.
- Modes:
  - strict size profile
  - adaptive quality profile

### 5.4 Metadata
- Copies key EXIF/XMP tags after re-encode.
- Preserve path may bypass re-encode for compatible scenarios.

### 5.5 HDR Retention
- HDR-like sources are detected via container/type and metadata hints.
- HDR-preserve path avoids flattening HDR into SDR when enabled.
- Save/share keeps correct MIME and extension.

## 6. File and Share Logic
- Single image output uses cache file naming.
- Batch output is packed into ZIP.
- Save paths:
  - images to `Pictures/InstagramOptimizer`
  - ZIP to `Downloads/InstagramOptimizer`
- Share:
  - single image to Instagram app package
  - ZIP via generic share sheet

## 7. Architecture (for Rebuild)
- UI: one Activity with Compose composables.
- State: `remember` + `mutableStateOf`.
- Background work: coroutines (`Dispatchers.IO` / `Dispatchers.Default`).
- Settings persistence: `SharedPreferences`.
- Imaging stack: `Bitmap`, `Canvas`, `ImageDecoder`, `ExifInterface`.

## 8. Rebuild Guide (Step-by-Step)
1. Create Android project with Kotlin + Compose.
2. Build base UI: mode switch, advanced panel, select/optimize/share/save actions.
3. Integrate image picking with `GetMultipleContents()`.
4. Implement original/result canvases.
5. Add optimization engine:
   - decode
   - sample size
   - resize
   - JPEG quality targeting
   - metadata copy
6. Add save/share via `MediaStore` + `FileProvider`.
7. Add result rotation and persistence.
8. Add HDR detection and preserve mode.
9. Add edge-to-edge and Material 3 theme settings.

## 9. Core Functions in Current Code
- `MainScreen(...)`
- `optimizeForInstagram(...)`
- `optimizeForInstagramInternal(...)`
- `highQualityResize(...)`
- `compressJpegSmart(...)`
- `copyMetadataForSocialMedia(...)`
- `rotateJpegFileInPlace(...)`
- `hasHdrLikeSource(...)`

## 10. Known Trade-offs
- Higher compression saves size but can reduce fine detail.
- Very large inputs increase memory pressure.
- Metadata behavior can vary across apps/platform viewers.
- HDR interoperability differs across devices and apps.
