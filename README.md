# Instagram Photo Optimizer (Android)

Offline-first Android app to optimize photos for Instagram uploads.
Built with Kotlin + Jetpack Compose.

## Features

- Select one or multiple images from gallery
- Preserve original aspect ratio
- Optimize for Instagram Post or Story formats
- Smart JPEG compression with quality-first strategy
- Export as single optimized image or ZIP batch
- Share directly to Instagram (single image)
- Share ZIP for batch workflows
- Save optimized outputs locally
- Fully offline (no cloud processing)

## Tech Stack

- Kotlin
- Jetpack Compose
- Android SDK (minSdk 26)

## Getting Started

1. Open in Android Studio.
2. Sync Gradle.
3. Run on device/emulator.

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Image Quality Guidelines (Instagram)

This app follows practical upload recommendations commonly used by photographers:

- Use `sRGB` output
- Keep valid Instagram aspect ratios
- Avoid oversized uploads that trigger heavy recompression
- Use JPEG with controlled compression
- Resize before upload (instead of letting platform do aggressive scaling)

Reference discussion:

- [Reddit: Bad picture quality when uploaded to Instagram](https://www.reddit.com/r/AskPhotography/comments/m66yx0/bad_picture_quality_when_uploaded_to_instagram/?solution=6a591cf262ab644a6a591cf262ab644a&js_challenge=1&token=bbbe4bf1c9a2b5160829c4be34da58617db68bb3f4268f1d25af009064a75354&jsc_orig_r=)

## Batch Workflow

- Select multiple images
- Optimize in one run
- Get a ZIP package
- Share ZIP directly

## License

MIT License. See `LICENSE`.
