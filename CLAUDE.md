# BillBox

Native Android app (Kotlin, Jetpack Compose, Room), offline-only. Product spec: `../BillBox_App_Specification.md`.

## Versioning (required on every code change)

In the same commit as the change:

1. `app/build.gradle.kts`: increase `versionCode` by 1 and update `versionName` (MAJOR.MINOR.PATCH):
   - PATCH: bug fixes, text and small UI tweaks
   - MINOR: new features or screens
   - MAJOR: large redesigns or changes that break existing data
2. Add an entry at the top of `CHANGELOG.md` with the new version and what changed.

Docs-only or store-asset-only changes do not need a version bump.

## Build

`JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug lintDebug testDebugUnitTest`
Release: see README "Release build".
