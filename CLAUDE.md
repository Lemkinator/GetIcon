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

Release signing properties (`releaseStoreFile`, `releaseStorePassword`, `releaseKeyAlias`, `releaseKeyPassword`) use the same lookup order.
Without them the build falls back to debug signing.

## Architecture

Single-module (`:app`) Android app — extracts and exports app icons. Layered architecture (data/domain/ui) without ViewModels — Activities
own state and inject use cases directly:

- **`data/`** — `UserSettingsRepository`: DataStore Preferences CRUD (icon size, mask, colors)
- **`domain/`** — thin use cases: `GetUserSettingsUseCase`, `UpdateUserSettingsUseCase`, `AppPickerStrategy`
- **`ui/`** — two activities: `MainActivity` (app picker + APK import), `IconActivity` (icon preview + export)
- **`App.kt`** — `@HiltAndroidApp` entry point, calls `common-utils` init
- **`PersistenceModule.kt`** — Hilt singleton providing `DataStore<Preferences>`

DI is Hilt throughout. Async via coroutines (`lifecycleScope.launch`, `suspend`). ViewBinding enabled.

## Key Patterns

**External libraries dominate UI logic.** Many helpers (`prepareActivityTransformationFrom()`, `toast`, `exportBitmap`,
`commonUtilsSettings`) live in `io.github.lemkinator:common-utils` (imported as `de.lemke.commonutils`). When changing behavior, inspect
call sites in `MainActivity.kt` / `IconActivity.kt` first.

**Resource aliasing** — code imports `de.lemke.commonutils.R as commonutilsR` alongside the app's own `R`. Be aware when touching resource
IDs.

**KSP code generation** — Hilt and Room annotations require a Gradle build to regenerate sources after editing annotated classes.

**Use cases over repositories** — prefer injecting `GetUserSettingsUseCase` / `UpdateUserSettingsUseCase` rather than
`UserSettingsRepository` directly.

**Dependency exclusions** — root `build.gradle.kts` excludes many AndroidX modules from subprojects to prevent duplicate packaging. Check
`allprojects`/`subprojects` blocks when updating dependencies.

## Static Analysis

Three tools run as part of `./gradlew build`:

- **Spotless** — enforces formatting via ktlint 1.7.1. Fix violations with `./gradlew spotlessApply`.
- **Detekt** — static analysis; config at `config/detekt/detekt.yml`. `autoCorrect = false` so fixes are manual.
- **Kover** — coverage; verify threshold with `./gradlew koverVerify`.

**ktlint rule overrides** — two rules disabled in `.editorconfig` to match community practice
(NowInAndroid, Pokedex both use the inline form):
- `ktlint_standard_annotation = disabled` — ktlint 1.7+ moves `@Inject` before `constructor` onto
  its own continuation line, doubly-indenting the class body (8 sp instead of 4 sp).
- `ktlint_standard_class-signature = disabled` — in ktlint 1.7+, both rules together enforce the
  split form; disabling only `annotation` is insufficient.

**Important**: when upgrading ktlint, files already formatted in the ktlint-native (8-space) style will
NOT be automatically reverted by `spotlessApply` — ktlint only flags violations of *enabled* rules.
If you re-enable these rules and then disable them again, you must manually restore the inline form
and re-run `spotlessApply`. See git history for the migration pattern.

When upgrading ktlint: run `./gradlew spotlessApply` after the bump, check for new IDE diagnostics,
and add `.editorconfig` overrides for any newly-misbehaving rules.

## Finding Code

- `de.lemke.geticon` package (`app/src/main/java/de/lemke/geticon/`) is the entire app surface
- Search `commonUtilsSettings` to find shared preference usage
- APK extraction flow: `MainActivity.processApk()` → temp file → `IconActivity` via intent with `ApplicationInfo`
- Icon rendering/export: `IconActivity` with size (16–1024px), mask toggle, color tint options
