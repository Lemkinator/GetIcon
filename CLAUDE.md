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
Instrumented tests: `MainActivityTest`, `IconActivityTest`
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
Layered architecture (data/domain/ui) with ViewModels per activity:

- **`data/`** — `UserSettings`: a common-utils `SettingsRepository` subclass,
  SharedPreferences-backed (icon size, mask, colors)
- **`domain/`** — thin use cases: `GenerateIconUseCase`, `GetApplicationInfoUseCase`, `ProcessApkUseCase`.
- **`ui/`** — two activities + two ViewModels: `MainActivity` / `MainViewModel`
  (app picker + APK import), `IconActivity` / `IconViewModel` (icon preview + export)
- **`App.kt`** — `@HiltAndroidApp` entry point; injects `settings: SettingsRepository`
  and calls `settings.applyDarkMode()` in `onCreate()`
- **`di/SettingsModule.kt`** — two Hilt modules: `SettingsProvideModule`
  (`@Provides @Singleton` builds the single `UserSettings` instance) and
  `SettingsBindModule` (`@Binds` redirects `SettingsRepository`-typed requests
  to that instance)

DI is Hilt throughout. Async via coroutines (`viewModelScope.launch`, `suspend`).
ViewBinding enabled. Activities collect `StateFlow<UiState>` and one-shot
`Channel<Event>` from their ViewModel via `collectState`/`collectEvents`.

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
(`prepareActivityTransformationFrom()`, `toast`, `exportBitmap`)
live in `io.github.lemkinator:common-utils`
(imported as `de.lemke.commonutils`). When changing behavior, inspect
call sites in `MainActivity.kt` / `IconActivity.kt` first.

**Resource aliasing** — code imports
`de.lemke.commonutils.R as commonutilsR` alongside the app's own `R`.
Be aware when touching resource IDs.

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
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest koverVerifyDebug koverHtmlReportDebug verifyRoborazziDebug pixel9Api35DebugAndroidTest assembleRelease
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

Both this repo and common-utils default to JUnit 5 (Kotest runs on the JUnit 5 platform —
see `ArchitectureTest.kt`). JUnit 4 + `junit-vintage-engine` is used only for tests that need
Robolectric, because Robolectric has no native JUnit 5 support. Neither repo uses a JUnit5
bridge for Robolectric — common-utils used the experimental
`tech.apter.junit5.jupiter:robolectric-extension` for a period but reverted to plain
`@RunWith(RobolectricTestRunner::class)` after that bridge's per-class (not per-method) state
isolation caused real test pollution; it now matches this repo's pattern exactly. GetIcon's
Robolectric surface (Hilt activities, Roborazzi screenshots, Context-backed settings/use cases)
is large, so most of the suite falls on the JUnit4 side — that's a consequence of what's under
test, not a different policy than common-utils. Follow the rule per test (Kotest by default,
JUnit4+Robolectric only when Robolectric is actually required); don't force everything onto one
runner.

**Test order independence**: `io.kotest.provided.ProjectConfig` sets
`specExecutionOrder = SpecExecutionOrder.Random`, randomizing Kotest spec order run to run.
This only covers Kotest's own engine — the JUnit4/Robolectric classes (run via
`junit-vintage-engine`) have no equivalent native randomization hook through Gradle, so their
order-independence relies on test hygiene (every test resets any shared/static state it
touches, e.g. `AppCompatDelegate`'s static delegate registry via `ActivityScenario`'s
auto-`close()`) rather than a randomizer.

## Settings in Tests

Tests never mock settings: every test uses the real `UserSettings` over an isolated, empty
store, so defaults come from `UserSettings`'s own production delegates — no duplicated
default values, no manual reset helpers, no per-field mock stubs.

The only canonical way to get a fresh store in a test is `freshTestPreferences()` — published by
common-utils from `lib/src/testFixtures` (`testImplementation(testFixtures(libs.common.utils))` /
`androidTestImplementation(testFixtures(libs.common.utils))`). It returns a UUID-named
`SharedPreferences` file, fresh by construction — no manual `.edit().clear()`, no caller-supplied
names, no collision risk on a reused GMD device. Test code never calls `getSharedPreferences(...)`
or `PreferenceManager.getDefaultSharedPreferences(...)` directly.

- **`TestSettingsModule` twins** — `app/src/test/java/de/lemke/geticon/TestSettingsModule.kt`
  and `app/src/androidTest/java/de/lemke/geticon/TestSettingsModule.kt`: same package, same
  file name, byte-for-byte identical content. Each `@TestInstallIn`-replaces
  `SettingsProvideModule` with `UserSettings(freshTestPreferences(context))`, so every
  `@HiltAndroidTest` gets a real, empty `UserSettings` automatically. **Kept as twins
  deliberately** — consolidating into a single `app/src/testFixtures` file was tried and
  reverted: Hilt's kapt/ksp aggregation doesn't pick up a `@Module`/`@TestInstallIn` class
  declared in the `testFixtures` source set for the `test` (Robolectric) side, even though it
  compiles cleanly and silently falls back to the production module (verify via
  `app/build/intermediates/javac/debugUnitTest/.../hilt_aggregated_deps` if revisiting this).
- **`FakeSharedPreferences`** (published by common-utils from
  `lib/src/testFixtures/java/de/lemke/commonutils/data/FakeSharedPreferences.kt`) — a pure-JVM
  double used only by the one Kotest spec with no Context (`IconViewModelTest`); everywhere else
  Robolectric's real `SharedPreferences` (via the module above) or a Context-backed file
  (`UserSettingsTest`, using `freshTestPreferences()` directly) is used instead.
- **`bypassOobe()`** — also published by common-utils testFixtures
  (`de.lemke.commonutils.bypassOobe()`, a plain `SettingsRepository` extension), for any test
  that launches `MainActivity`. GetIcon has no settings-test code of its own left beyond the
  `TestSettingsModule` twins, which name GetIcon's own `UserSettings`/`SettingsProvideModule`
  and can't move into common-utils.

## Finding Code

- Search `@Inject lateinit var settings: SettingsRepository` to find shared preference usage
- APK extraction flow: `MainActivity.processApk()` → temp file →
  `IconActivity` via intent with `ApplicationInfo`
