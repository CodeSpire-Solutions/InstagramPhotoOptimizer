# Instagram Photo Optimizer (Android)

Offline-first Android app to optimize photos for Instagram uploads.
You all know that the compression for photos is much more aggresive on Android, meanwhile on Apple Devices the compression isn't that aggresive. According to this [Reddit Post](https://www.reddit.com/r/AskPhotography/comments/m66yx0/bad_picture_quality_when_uploaded_to_instagram/) there are some techniques to get better image quality. This app automatically applies these techniques to selected images.
Built with Kotlin + Jetpack Compose + Material 3.

## Features

- Single and batch image optimization
- Optimized export for:
  - Post mode
  - Story mode
- Advanced controls:
  - Export size policy
  - Resolution profile
  - Depth/XMP handling
  - Anti-aliasing strength
- Before/after canvas preview
- Result canvas rotation (`↺` / `↻`) after optimization
  - Rotation is persisted into the optimized file
- Direct Instagram share for single-image output
- ZIP export/share for batch output
- Local gallery/downloads save
- Fully offline processing

## Technical Stack

- Kotlin
- Jetpack Compose
- Android SDK
- ExifInterface
- Coroutines

## SDK / Build

- `minSdk = 29`
- `targetSdk = 35`
- `compileSdk = 35`
- Java/Kotlin toolchain: `17`

## Getting Started

1. Open project in Android Studio.
2. Sync Gradle dependencies.
3. Run on emulator/device.

Debug APK path:

`app/build/outputs/apk/debug/app-debug.apk`

## Documentation

Detailed docs (including full option explanations and rebuild guide):

- [App Documentation](docs/APP_DOCUMENTATION.md)

This documentation includes:
- what each setting does,
- complete processing flow,
- technical pipeline details,
- and step-by-step instructions to rebuild the app architecture from scratch.

## Project Structure

- `app/src/main/java/org/css_apps_m3/instagramphotooptimizer/MainActivity.kt`
  - UI screens and state handling
  - image optimization pipeline
  - preview/result canvas logic
  - share/save/export logic
- `app/src/main/java/org/css_apps_m3/instagramphotooptimizer/ui/theme/`
  - theme, color, typography configuration

## License

MIT License. See [LICENSE](LICENSE).
