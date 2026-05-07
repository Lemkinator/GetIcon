# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All commands run from the repo root on Windows (PowerShell or Git Bash):

```powershell
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK (falls back to debug signing if no signing props)
./gradlew installDebug           # install on connected device/emulator
./gradlew build                  # full build (used in CI)
./gradlew clean build --no-daemon
```

No unit or instrumented tests exist yet (test source directories exist but are empty).

## Private Dependencies (Required for Build)

Two private GitHub Maven repos are used:
- `https://maven.pkg.github.com/tribalfs/oneui-design`
- `https://maven.pkg.github.com/lemkinator/common-utils`

Provide credentials via **one** of these (checked in order):
1. `github.properties` in project root: `ghUsername=...` / `ghAccessToken=...`
2. `~/.gradle/gradle.properties`: `ghUsername=...` / `ghAccessToken=...`
3. Env vars: `GH_USERNAME` / `GH_ACCESS_TOKEN`

Missing credentials are the most common build failure cause.

Release signing properties (`releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`) use the same lookup order. Without them the build falls back to debug signing.

## Architecture

Single-module (`:app`) Android app — extracts and exports app icons. Layered architecture (data/domain/ui) without ViewModels — Activities own state and inject use cases directly:

- **`data/`** — `UserSettingsRepository`: DataStore Preferences CRUD (icon size, mask, colors)
- **`domain/`** — thin use cases: `GetUserSettingsUseCase`, `UpdateUserSettingsUseCase`, `AppPickerStrategy`
- **`ui/`** — two activities: `MainActivity` (app picker + APK import), `IconActivity` (icon preview + export)
- **`App.kt`** — `@HiltAndroidApp` entry point, calls `common-utils` init
- **`PersistenceModule.kt`** — Hilt singleton providing `DataStore<Preferences>`

DI is Hilt throughout. Async via coroutines (`lifecycleScope.launch`, `suspend`). ViewBinding enabled.

## Key Patterns

**External libraries dominate UI logic.** Many helpers (`prepareActivityTransformationFrom()`, `toast`, `exportBitmap`, `commonUtilsSettings`) live in `io.github.lemkinator:common-utils` (imported as `de.lemke.commonutils`). When changing behavior, inspect call sites in `MainActivity.kt` / `IconActivity.kt` first.

**Resource aliasing** — code imports `de.lemke.commonutils.R as commonutilsR` alongside the app's own `R`. Be aware when touching resource IDs.

**KSP code generation** — Hilt and Room annotations require a Gradle build to regenerate sources after editing annotated classes.

**Use cases over repositories** — prefer injecting `GetUserSettingsUseCase` / `UpdateUserSettingsUseCase` rather than `UserSettingsRepository` directly.

**Dependency exclusions** — root `build.gradle.kts` excludes many AndroidX modules from subprojects to prevent duplicate packaging. Check `allprojects`/`subprojects` blocks when updating dependencies.

## Finding Code

- `de.lemke.geticon` package (`app/src/main/java/de/lemke/geticon/`) is the entire app surface
- Search `commonUtilsSettings` to find shared preference usage
- APK extraction flow: `MainActivity.processApk()` → temp file → `IconActivity` via intent with `ApplicationInfo`
- Icon rendering/export: `IconActivity` with size (16–1024px), mask toggle, color tint options