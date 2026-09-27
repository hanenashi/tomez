# tomez — handoff

## Goal

Build an extremely lightweight Android plaintext editor, using Bill Farmer's Editor as the starting/reference point:

https://github.com/billthefarmer/editor

The priority is **small, fast, boring, reliable plaintext editing**. Do not add features just because the upstream app has them.

## Direction

- Prefer the existing Java + classic Android Views approach.
- Do **not** port to Compose.
- Do **not** rewrite to Kotlin unless there is a concrete technical reason.
- Reuse/adapt upstream code where it genuinely saves work, but strip aggressively.
- Keep dependencies to an absolute minimum.
- Use Android's native Storage Access Framework for file open/save where practical.

## Required features

### Text

- Plaintext only.
- Full Unicode text support.
- No Markdown rendering.
- No HTML/rendered preview.
- No syntax highlighting.
- Normal Android text selection / copy / paste / undo behavior.

### Appearance

- Small built-in font choice only: system sans, system serif, monospace.
- Simple font-size picker.
- A few clean themes: light, dark, black/AMOLED, optionally one warm/sepia theme if it stays trivial.
- Persist appearance choices.

### File operations

Provide a simple, clear graphical menu for:

- New
- Open
- Save
- Save As
- Close

Use normal Android file/document APIs rather than building an elaborate custom file manager.

Expected behavior:

- New starts a blank untitled document.
- Open uses the Android document picker.
- Save writes to the current document when one exists.
- Save As always asks for a destination/name.
- Close closes the current document and returns to a blank editor.
- If there are unsaved changes, destructive actions should ask before discarding them.

## UI

Keep the editor itself dominant.

Desired shape:

- minimal app bar / toolbar
- obvious file/menu button(s)
- editor fills essentially all remaining screen space
- graphical menus/dialogs rather than a huge old-style settings list
- touch-friendly on a phone
- no visual clutter

This is meant to feel like a tiny native Android utility, not an IDE or word processor.

## Explicit non-goals

Do not implement unless requested later:

- previews of any kind
- Markdown support
- rich text
- formatting spans
- syntax highlighting
- tabs / multi-document workspace
- cloud sync
- project/folder management
- plugin system
- embedded file browser
- printing
- search/replace beyond whatever basic support comes nearly free
- complicated preferences
- telemetry/accounts/network features

## First pass

1. Inspect the current upstream Bill Farmer Editor implementation and identify the smallest reusable core for editing + SAF file I/O.
2. Establish a minimal buildable Android project in this repo.
3. Get New / Open / Save / Save As / Close working first.
4. Add dirty-document protection.
5. Add the small font picker, font sizes, and themes.
6. Remove or avoid all unrelated upstream features and dependencies.
7. Build and smoke-test on a modern Android phone.

When choosing between cleverness and fewer moving parts, choose fewer moving parts.

## Working rule

Keep commits small and runnable. Do not perform a broad Java-to-Kotlin or Views-to-Compose modernization pass. The whole point of **tomez** is to end up *lighter* than the upstream editor.