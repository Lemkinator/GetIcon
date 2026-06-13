# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working
with code in this repository.

## Commands

All commands run from the repo root on Windows (PowerShell or Git Bash):

```powershell
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK (debug signing fallback)
./gradlew installDebug           # install on connected device/emulator
```

Unit tests exist: `IconViewModelTest`, `IconActivityScreenshotTest` (Roborazzi),
`MainActivityScreenshotTest` (Roborazzi), plus Konsist architecture tests.
Instrumented tests: `MainActivityTest`, `IconActivityTest`, `UserSettingsRepositoryInstrumentedTest`
— run via Gradle Managed Device (no physical device needed):

```powershell
./gradlew pixel9Api35DebugAndroidTest   # downloads ~1 GB image on first run, cached after
```

The GMD device (`pixel9Api35`: Pixel 9 / API 35 / aosp / x86_64) is declared once in root
`build.gradle.kts` and shared by `:app` instrumented tests and `:benchmarks` baseline profile generation.

### Baseline Profile & Benchmarks

Generate the baseline profile (same GMD device — image already cached if you ran instrumented tests):

```powershell
./gradlew :app:generateBaselineProfile `
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Run macrobenchmarks manually (not CI-gated — numbers are advisory and device-sensitive):

```powershell
./gradlew :benchmarks:pixel9Api35BenchmarkReleaseAndroidTest
```

## Private Dependencies (Required for Build)

Two private GitHub Maven repos are used:

- `https://maven.pkg.github.com/tribalfs/oneui-design`
- `https://maven.pkg.github.com/lemkinator/common-utils`

Provide credentials via **one** of these (checked in order):

1. `github.properties` in project root: `ghUsername=...` / `ghAccessToken=...`
2. `~/.gradle/gradle.properties`: `ghUsername=...` / `ghAccessToken=...`
3. Env vars: `GH_USERNAME` / `GH_ACCESS_TOKEN`

## Architecture

Single-module (`:app`) Android app — extracts and exports app icons.
Layered architecture (data/domain/ui) without ViewModels — Activities
own state and inject use cases directly:

- **`data/`** — `UserSettingsRepository`: DataStore Preferences CRUD
  (icon size, mask, colors)
- **`domain/`** — thin use cases: `GetUserSettingsUseCase`,
  `UpdateUserSettingsUseCase`, `AppPickerStrategy`
- **`ui/`** — two activities: `MainActivity` (app picker + APK import),
  `IconActivity` (icon preview + export)
- **`App.kt`** — `@HiltAndroidApp` entry point, calls `common-utils` init
- **`PersistenceModule.kt`** — Hilt singleton providing `DataStore<Preferences>`

DI is Hilt throughout. Async via coroutines (`lifecycleScope.launch`,
`suspend`). ViewBinding enabled.

**Multi-activity (not single-activity).** OneUI (sesl-androidx) is activity-oriented;
single-activity + Navigation Component was tried and reverted (buggy menu, leaky
fragment transitions needing reflection, OneUI screens authored as activities).
`MainActivity` (app picker) and `IconActivity` (preview/export) are separate
activities; navigation between them uses shared-element activity transitions
(`transformToActivity`).

**First run** uses the common-utils onboarding flow: `onboardIfNeeded(...)` is the first
call in `MainActivity.onCreate` (before inflating UI) and launches OOBE as a task-root
activity when needed (predictive back = app exit; no Main leak on first start). GetIcon
uses OOBE only. The baseline-profile (`nonMinifiedRelease`) build sets
`BuildConfig.FIRST_RUN_SKIPPABLE = true`, so the benchmark passes `EXTRA_SKIP_ONBOARDING`
to bypass OOBE and measure Main + Icon only; production `release` keeps it `false`.

## Key Patterns

**External libraries dominate UI logic.** Many helpers
(`prepareActivityTransformationFrom()`, `toast`, `exportBitmap`,
`commonUtilsSettings`) live in `io.github.lemkinator:common-utils`
(imported as `de.lemke.commonutils`). When changing behavior, inspect
call sites in `MainActivity.kt` / `IconActivity.kt` first.

**Resource aliasing** — code imports
`de.lemke.commonutils.R as commonutilsR` alongside the app's own `R`.
Be aware when touching resource IDs.

**Use cases over repositories** — prefer injecting
`GetUserSettingsUseCase` / `UpdateUserSettingsUseCase` rather than
`UserSettingsRepository` directly.

**Dependency exclusions** — root `build.gradle.kts` excludes many AndroidX
modules from subprojects to prevent duplicate packaging. Check
`allprojects`/`subprojects` blocks when updating dependencies.

## Static Analysis

Four tools run as part of `./gradlew build`:

- **Spotless** — enforces formatting via ktlint (sole ktlint driver;
  Detekt has no ktlint wrapper). Fix violations with
  `./gradlew spotlessApply`.
- **Detekt** — static analysis; config at `config/detekt/detekt.yml`.
  `autoCorrect = false` — fixes are manual.
- **Kover** — 100% INSTRUCTION + BRANCH coverage required.
  Verify: `./gradlew koverVerifyDebug`.
- **Konsist** — architecture rules in
  `app/src/test/java/de/lemke/geticon/ArchitectureTest.kt`. Enforces
  `data/domain/ui` layering. Runs as part of `./gradlew test`.

**Pre-commit hook** — blocks commits with formatting violations. Opt in
once per clone:

```powershell
git config core.autocrlf input           # Windows: prevents CRLF violations
git config core.hooksPath .githooks
```

The hook runs `spotlessCheck` and exits 1 with a
`./gradlew spotlessApply` reminder on failure. It also fails fast with a
targeted message if `core.autocrlf=true` is detected.

**After any change** — run the full local CI suite before declaring work done:

```powershell
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest koverVerifyDebug verifyRoborazziDebug
```

If `spotlessCheck` fails, fix with `./gradlew spotlessApply` then re-run. Screenshot test failures (`verifyRoborazziDebug`) mean the code
change broke a visual — do not analyze screenshots, ask the user to verify the changes.

**Dependency analysis** — manual hygiene tool (not in CI). Invoke with:

```powershell
./gradlew buildHealth
```

Report at `build/reports/dependency-analysis/build-health-report.txt`.
Review unused/misconfigured deps case-by-case.

**ktlint rule overrides** — two rules disabled in `.editorconfig` to match
community practice (NowInAndroid, Pokedex both use the inline form):

- `ktlint_standard_annotation = disabled` — ktlint 1.7+ moves `@Inject`
  before `constructor` onto its own continuation line, doubly-indenting
  the class body (8 sp instead of 4 sp).
- `ktlint_standard_class-signature = disabled` — in ktlint 1.7+, both
  rules together enforce the split form; disabling only `annotation` is
  insufficient.

## Robolectric + JUnit 5

`@RunWith(RobolectricTestRunner::class)` + `junit-vintage-engine` is correct — Robolectric has no
native JUnit 5 support. Keep until Robolectric ships native JUnit 5.

## Finding Code

- Search `commonUtilsSettings` to find shared preference usage
- APK extraction flow: `MainActivity.processApk()` → temp file →
  `IconActivity` via intent with `ApplicationInfo`
