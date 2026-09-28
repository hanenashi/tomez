# tomez

A small native Android plaintext editor written in Java with Android Views and no external runtime dependencies.

## What works

- New, Open, Save, Save As, and Close from the overflow menu; New opens the keyboard, while Close returns to a quiet blank document
- Android's document picker for opening and creating files
- Unsaved-change prompts before New, Open, Close, or exiting with Back
- UTF-8 text, including Unicode and UTF-8 files with a byte-order mark
- System sans, system serif, and monospace fonts; a text-size slider with custom entry; light, dark, grey, and green themes
- A fullscreen toggle; appearance and fullscreen choices saved across launches
- One native EditText for reading and editing, inside a platform ScrollView for fling, edge feedback, and a thin fading scrollbar on long documents; scrolling and tapping text do not open the keyboard
- Tap the pencil button to open the keyboard; native cursor, selection, copy/paste, and undo remain available
- Filename and unsaved marker in the toolbar; tap or long-press it for the full location in a matching panel
- Current font, size, and theme shown in the menu; matching compact font, theme, and text-size dialogs
- Version and GitHub link at the bottom of the overflow menu
- An internal recovery draft for unsaved edits, written after a short pause in typing and when the app goes into the background

The editor accepts files up to 1 MiB. Files that are not valid UTF-8 are rejected without changing the open document. Save As makes the new file the current document. The recovery draft is local to the app and is cleared after a successful save or explicit discard.

The document stays visible in one EditText. Tap or scroll without opening the keyboard; press the pencil when ready to type. New opens the keyboard for a blank document. Tap or long-press the filename to see its complete location, including a provider URI when no filesystem path is available. The text-size slider covers 10–40 sp; the number field accepts 8–96 sp. Size changes preview in the document behind the dialog; Cancel restores the saved size. Fullscreen hides Android's status and navigation bars. Swipe from an edge to reveal them briefly, or turn Fullscreen off in the overflow menu.

## Build

Install the Android SDK (platform 35) and a JDK 17 or newer, then run:

```sh
./gradlew :app:assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. The app supports Android 8.0 (API 26) and newer.

## Scope

This is an early version. It does not detect changes made to an open document by another app, and saves rely on the selected document provider to complete writes. It does not yet offer other text encodings or files larger than 1 MiB. The source is original code; Bill Farmer's Editor was used as a reference, with no code copied from it.
