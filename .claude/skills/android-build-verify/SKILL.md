---
name: android-build-verify
description: Build, test and lint the Aurio Android app and fix failures. Use after code changes or when the user asks to check the build.
---

# Build and verify

Run from the project root (on Windows use `gradlew.bat`):

1. `./gradlew :app:testDebugUnitTest` – scoring rules. Any failure is a product bug; fix the code, not the test,
   unless the brief's rule itself changed.
2. `./gradlew :app:assembleDebug` – compile. Read the first error only, fix, re-run.
3. `./gradlew :app:lintDebug` – review `app/build/reports/lint-results-debug.html`; fix accessibility,
   contrast and permission warnings first.
4. Privacy gate: `grep -R "android.permission.INTERNET" app/src` must return nothing.
5. If a device is attached (`adb devices`), `./gradlew :app:installDebug` and check logcat for crashes:
   `adb logcat -s AndroidRuntime`.

Report: what passed, what failed, what you changed. Do not bump dependency versions unless asked.
