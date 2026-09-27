# tomez

A small native Android plaintext editor written in Java with Android Views and no external runtime dependencies.

## What works

- New, Open, Save, Save As, and Close from the gear menu
- Android's document picker for opening and creating files
- Unsaved-change prompts before New, Open, Close, or exiting with Back
- UTF-8 text, including Unicode and UTF-8 files with a byte-order mark
- System sans, system serif, and monospace fonts; a text-size slider with custom entry; light, dark, grey, and green themes
- A fullscreen toggle; appearance and fullscreen choices saved across launches
- Version and GitHub link in the bottom bar
- An internal recovery draft for unsaved edits, written after a short pause in typing and when the app goes into the background

The editor accepts files up to 1 MiB. Files that are not valid UTF-8 are rejected without changing the open document. Save As makes the new file the current document. The recovery draft is local to the app and is cleared after a successful save or explicit discard.

The text-size slider covers 10–40 sp; the number field accepts 8–96 sp. Fullscreen hides Android's status and navigation bars. Swipe from an edge to reveal them briefly, or turn Fullscreen off in the gear menu.

## Build

Install the Android SDK (platform 35) and a JDK 17 or newer, then run:

```sh
./gradlew :app:assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. The app supports Android 8.0 (API 26) and newer.

## Scope

This is an early version. It does not detect changes made to an open document by another app, and saves rely on the selected document provider to complete writes. It does not yet offer other text encodings or files larger than 1 MiB. The source is original code; Bill Farmer's Editor was used as a reference, with no code copied from it.
