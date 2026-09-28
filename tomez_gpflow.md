# tomez + GPFlow publishing plan

This document is the application-side release plan for moving tomez from
debug/prerelease APKs to a permanent signed release and, later, Google Play.

GPFlow owns signing-key custody, backup, and recovery. This repository owns the
app identity, build gates, release verification, store metadata, and the
decision to publish.

See the matching custody runbook in the private GPFlow repository:
`docs/tomez-signing.md`.

## Current state

At the time this plan was written:

- application ID: `com.hanenashi.tomez`
- version: `0.3.14`
- version code: `17`
- minimum Android: 8.0 / API 26
- target SDK: 35
- public test distribution: debug APK prerelease
- permanent production signing identity: not yet established
- GitHub Pages site and privacy page: present
- no app-owned network functionality, accounts, ads, analytics, or telemetry
- app works with local/user-selected text documents through Android's document
  APIs

The existing debug APK does not define the future production signing lineage.

## Phase 1 — establish the permanent tomez identity

Use the GPFlow runbook on Kurochan.

1. Mount the GPFlow VeraCrypt vault interactively.
2. Create a dedicated `Tomez-Signing` directory.
3. Generate a tomez-only permanent key:
   - keystore: `tomez-release.jks`
   - alias: `tomez-release`
   - long validity
4. Store store/key passwords only in the password manager.
5. Export the public certificate.
6. Record the public SHA-256 and SHA-1 certificate fingerprints below.
7. Dismount the vault.
8. Run the GPFlow closed-container backup and require matching SHA-256 on all
   configured destinations.
9. Perform or schedule a recovery rehearsal after the first verified backup.

Do not reuse the BokounApp key.

### Permanent certificate record

Fill this only after generation:

```text
Alias: tomez-release
Certificate SHA-256: TODO
Certificate SHA-1: TODO
Created: TODO
```

No private-key material or passwords belong here.

## Phase 2 — add a real release build

Add a `release` signing path to the Android build without placing secrets in
Git.

Preferred model:

- release keystore remains inside the mounted GPFlow vault;
- real keystore path is local-only;
- passwords come from ignored local configuration or environment variables;
- no secret values are embedded in `build.gradle`, checked-in properties, CI
  files, issues, or chat.

The first implementation should stay boring. No release automation is required
until one supervised local release works end-to-end.

Expected build outputs:

```text
./gradlew clean assembleRelease
./gradlew bundleRelease
```

Use the APK for local device testing. Use the AAB for Google Play.

Before committing the Gradle wiring, confirm that a checkout without signing
secrets can still build the normal debug variant.

## Phase 3 — define release gates

For every candidate production build:

1. Checkout must be clean and synchronized with the intended release commit.
2. Version name and version code must be deliberate and unique.
3. Debug build must still compile.
4. Release APK/AAB must build successfully.
5. Verify package ID is exactly `com.hanenashi.tomez`.
6. Verify the release artifact is signed by the expected permanent
   certificate.
7. Record SHA-256 for release artifacts.
8. Install the release APK on a real device and smoke-test at least:
   - New / Open / Save / Save As / Close;
   - UTF-8 and Unicode text;
   - opening text/JSON/XML through **Open with tomez**;
   - read-only incoming URI followed by **Save As**;
   - unsaved-change prompts;
   - keyboard show/hide via pencil;
   - selection/copy/paste;
   - wrapped and unwrapped scrolling;
   - rotation;
   - themes, font and text-size controls;
   - fullscreen;
   - recovery draft after background/restore.
9. Confirm the GitHub Pages privacy statement still matches actual app
   behavior.
10. Only after all checks pass is the artifact a release candidate.

Keep the current 1 MiB/UTF-8 limits explicit in store copy until the app changes
them.

## Phase 4 — choose Google Play signing arrangement

Before the first Play production upload, explicitly choose and document one of
these paths:

### A. Existing tomez key becomes the app-signing identity

Use the GPFlow-created permanent tomez key as the Play app-signing certificate
where the Play enrollment flow allows importing the existing identity.

This keeps sideloaded and Play-distributed builds on the same permanent
certificate lineage.

### B. Play manages the app-signing identity

Let Google Play create/manage the app-signing key and use a separate tomez
**upload key** under GPFlow custody.

If this path is chosen:

- create a separate upload key;
- store it under `Tomez-Signing`;
- record only its public fingerprint here;
- dismount and back up the GPFlow vault again.

Do not make this choice casually after production publication: signing lineage
becomes a long-lived application identity.

## Phase 5 — Play Console preparation

Prepare the Play listing independently of signing.

Material already available or nearly available:

- app name: **tomez**
- package: `com.hanenashi.tomez`
- app icon: `assets/icon.png`
- website/support page: `https://hanenashi.github.io/tomez/`
- privacy policy: `https://hanenashi.github.io/tomez/privacy.html`
- English and Czech product copy
- screenshots / illustrated quick help

Still prepare/review in Play Console:

- short description;
- full description;
- phone screenshots in the required sizes;
- app category;
- contact/support information;
- content rating questionnaire;
- target audience / age declarations;
- ads declaration;
- app access declaration;
- Data safety form;
- any current target-API or testing requirements shown by Play Console.

Do not infer Play-policy answers from this document alone. At submission time,
answer the current Play Console forms from the actual shipping build.

For the current tomez design, verify before submission that the shipped app
still has no advertising, analytics, account system, telemetry, or app-owned
network transfer before describing it that way.

## Phase 6 — first Play upload

Use a non-production track first.

1. Build the approved signed AAB from the exact release commit.
2. Verify version, package, certificate/upload identity, and SHA-256.
3. Upload to the appropriate Play test track.
4. Resolve every Play Console warning that affects publication.
5. Install the Play-delivered build on a real device.
6. Repeat the core file/open/edit/save smoke test.
7. Check upgrade behavior from any prior Play test build.
8. Keep GitHub debug/prerelease distribution clearly distinguished from the
   Play-signed production lineage.
9. Production rollout is a separate explicit decision after testing.

Signing an artifact does not authorize publishing it.

## Phase 7 — production and future updates

For each later release:

1. increment `versionCode`;
2. set the intended `versionName`;
3. update release notes;
4. run the release gates;
5. sign with the established lineage/upload identity;
6. record artifact hash and expected certificate;
7. test the Play-delivered build;
8. publish only after explicit approval.

Never replace or rotate signing/upload keys merely for convenience. Follow the
platform's recovery/rotation process if a key incident actually occurs.

## Useful public verification record

Once production signing exists, keep a small non-secret section here:

```text
Application ID: com.hanenashi.tomez
Production app-signing certificate SHA-256: TODO
Upload certificate SHA-256 (if separate): TODO
First production versionCode: TODO
First production versionName: TODO
First production commit: TODO
```

Artifact SHA-256 values can live in GitHub release notes or a release-specific
record rather than accumulating indefinitely in this file.

## Stop conditions

Stop the release/publish flow if any of these occur:

- checkout is dirty or release commit is ambiguous;
- package ID is unexpected;
- version code was already used;
- release artifact is debug-signed or signed by an unexpected certificate;
- signing secrets would enter Git, chat, command history, or a public CI log;
- GPFlow vault backup verification fails after a key change;
- a core file operation fails on the signed build;
- privacy/Data safety statements no longer match actual behavior;
- Play Console submission status is ambiguous.

Fix the ambiguity first; do not "try another upload" until the state is clear.

## Immediate next steps

1. Generate the permanent tomez signing key under GPFlow custody.
2. Record the public certificate fingerprint here.
3. Add local-secret release signing support to Gradle.
4. Produce and smoke-test one signed release APK.
5. Produce the matching AAB.
6. Prepare the Play listing and current compliance forms.
7. Upload to a test track.
8. Promote to production only after the Play-delivered build passes testing.
