---
name: stone-apk-release
description: Builds and publishes a new Stone Kotlin APK (android-native/) with its version bumped, checks passing, docs updated and the APK committed. Use it whenever the user asks to "generar el APK", "genera un apk con su versión", "sube la versión", "compila Stone", "nueva versión", "pásame el APK", or finishes a batch of Stone changes and wants to install them on the phone — even if they only say "APK" or "versión". Do not use it when the user explicitly says not to build the APK yet ("no generar el apk aún"); then only commit the code and note the changes as unreleased.
---

# Stone APK release

Stone's installable build is the R8-optimized `preview` variant of `android-native/app`. A release
means: bump the version, prove the build is healthy, produce the APK, record it, and hand it over.
The user installs it on top of the previous one, so the signature must not change. Since phase 6 Stone has no
relay link or MCP server of its own: agents reach it only through the Gateway Hub app (local IPC).

## 1. Bump the version

In `android-native/app/build.gradle.kts` increase `versionCode` by 1 and `versionName` by one patch
step (1.7.2 → 1.7.3). Use a minor step (1.7.x → 1.8.0) only for a release the user calls big.
Never reuse a versionCode: Android refuses to install an equal or lower one over the existing app.

## 2. Run every check

From `android-native/`:

```bash
./gradlew --offline -q :core:test :app:testDebugUnitTest :app:lintDebug :app:assemblePreview
```

- Tests: sum `tests/failures/errors` in `app/build/test-results/testDebugUnitTest/*.xml`; any
  failure stops the release — fix it (never skip or disable a test).
- Lint must not add warnings. Compare `app/build/reports/lint-results-debug.txt` against the known
  pre-existing ones (`GradleDependency`, `SelectedPhotoAccess`); fix new ones (unused strings,
  missing translations in the six `values*` folders, KTX suggestions, plurals with `%d`).
- If `--offline` fails because a dependency is missing, retry once without it.
- R8 must understand the Kotlin version: if the build log says "An error occurred when parsing kotlin
  metadata", the APK can crash on the phone even though every test passes. Lower Kotlin (or raise
  AGP/R8) until that warning is gone; never ship with it.

## 3. Verify the APK before handing it over

`app/build/outputs/apk/preview/app-preview.apk`:

- `output-metadata.json` shows the new `versionName`/`versionCode`.
- Signature unchanged — otherwise the phone will not update over the old install:
  `$(ls /opt/android-sdk/build-tools/*/apksigner | tail -1) verify --print-certs <apk>` must print
  the same SHA-256 as the previous `android-native/Stone-*-optimized.apk`.
- The provider service is declared: `aapt2 dump xmltree --file AndroidManifest.xml <apk> | grep -c ProviderService` > 0
  (action `com.zirakusa.gatewayhub.action.MCP`, permission `com.zirakusa.gatewayhub.permission.LINK`).

## 4. Record the release

- If files were added, moved or split, regenerate the code map: `python3 android-native/tools/update_map.py`.
- Copy the APK to `android-native/Stone-<versionName>-optimized.apk`.
- `android-native/README.md`: update "La última versión generada es `X` (versionCode N)" and the
  `Stone-X-optimized.apk` name.
- `CHANGELOG.md`: turn a "Sin publicar" section into `# Stone Kotlin <versionName>`, or add one at
  the top. Write in Spanish, one bullet per user-visible change, saying what the user will notice.
- If MCP tools changed, update `docs/MCP.md`.

## 5. Commit, push, deliver

Commit code, docs and the new APK together on the session's development branch and push
(`git push -u origin <branch>`, retry on network errors). Then send the APK file to the user and
reply in Spanish with: the version, that it installs over the previous one, what changed, the test
count, and anything not verified on a real phone (WebView pages, widgets, system dialogs and
notifications cannot be exercised by the unit tests — say so plainly).
