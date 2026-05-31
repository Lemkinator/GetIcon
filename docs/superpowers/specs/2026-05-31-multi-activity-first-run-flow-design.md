# Multi-Activity Architecture + Shared First-Run Flow — Design

**Date:** 2026-05-31
**Repos:** `GetIcon`, `common-utils` (branch `feat/single-activity` on both)
**Status:** Approved design, pending spec review

---

## 1. Background & Problem

The `feat/single-activity` branch migrated GetIcon (and the shared `common-utils`
screens) from a multi-activity architecture to a single-activity + Navigation
Component architecture. The original justification was a benchmark problem:
OOBE was a separate activity that called `finishAfterTransition()` on
`MainActivity` during cold start, so the macrobenchmark's `startActivityAndWait()`
landed on a Main that immediately died — unable to generate a clean startup
profile. "Best practice / single-activity" was adopted as the broader rationale.

The migration produced persistent problems:

- **Buggy menu** — `MenuProvider` add/remove juggled across fragment lifecycle
  against an activity-owned OneUI toolbar.
- **Buggy/leaky transitions** — fragment shared-element container transforms leak;
  the branch added `TransitionFragment.clearTransitionState()` (~120 lines of
  reflection into private `androidx.transition` fields) plus a
  `clearLastNestedScrollingChild()` reflection hack in `DrawerUtils`.
- **OneUI screens are authored as activities** — OOBE / About / Settings / Libs /
  AboutMe layouts and chrome assume an activity host.

### Key finding: OneUI is activity-oriented

OneUI's own flagship sample (`oneui-design/sample-app`) is **hybrid**, not pure
single-activity: a `NavHostFragment` is used **only** for sibling tab/content
screens; `About`, `CustomAbout`, `Preferences`, and detail screens are **separate
activities** (About is even declared as `<activity>` in the nav graph). GetIcon has
**one** main content screen — none of the sibling/tab structure that justifies a
nav host.

Forcing Google's single-activity dogma onto sesl-androidx + OneUI is analogous to
forcing Jetpack Compose somewhere it doesn't fit: it is "best practice" for vanilla
Android, but on a modified, activity-oriented framework it produces more tradeoffs
(reflection leak-fighting, menu lifecycle friction, fragile fragment transforms)
than benefits. GetIcon's usual single-activity wins do not apply: no internal deep
links, one content screen, and activity-launch cost is already covered by the
baseline profile. Multi-activity does **not** cost deep links — activity deep links
are native (`<intent-filter>` + `TaskStackBuilder`).

GetIcon is the proof-of-concept for a fleet of apps. The POC's verdict: if a
*simple* app needs reflection to avoid leaks, single-activity on OneUI is the wrong
direction fleet-wide.

## 2. Strategic Decision

**Stay multi-activity (OneUI-native) across the fleet. Use fragments only for true
sibling/tab content within a single screen — never for leaf, detail, or onboarding
screens.** Abandon and revert the single-activity work in both repos.

## 3. First-Run Flow Model (the shared piece)

A first run is an **ordered chain of task-root activities**, advanced by
*finish-then-start-next*, with OOBE as the built-in first step.

Properties (all required, all satisfied by this model):

- **Each step is task root when shown** (the previous step has already finished) ⇒
  the predictive back gesture shows **app exit**, with no Main behind it.
- **`MainActivity` is the durable launcher/entry** — no router/trampoline activity.
  The deep-link intent stays with Main.
- **No leak on first start** — Main decides and redirects *before inflating any UI*
  (above `setContentView`), so the expensive app-picker enumeration, drawer, and
  binding never run when redirecting to OOBE.
- **Fast normal start** — non-first start is a single cheap pref read, then Main.
- **Completion committed once, at the end of the chain** — the `acceptedTosVersion`
  flag is written only when advancing *past the last step*. This makes the whole
  chain atomic: killing the app mid-chain replays it from OOBE next launch.
- **Full chain on any first-run trigger** (fresh install *or* TOS-version bump that
  has not been re-accepted). Kept deliberately simple and uniform — a single commit
  point for both cases. (Normal app updates with no TOS change skip straight to
  Main, as today.)

Accepted cost: on first run only, `MainActivity` is created → finished (empty, no UI
inflated) → ... → created fresh at the end. A one-time double-create of an empty
Main. This is the unavoidable price of making each step task-root for the exit
gesture, and is acceptable.

## 4. common-utils API

New shared first-run flow API (replaces the deprecated `checkAppStartAndHandleOOBE`
/ `openOOBEAndFinish`):

- `setupFirstRunFlow(steps: List<Class<out Activity>> = emptyList())`
  Declares app-specific steps that run *after* OOBE. Simple apps omit it
  (OOBE-only). Apps needing extra first-run screens pass them here.
- `Activity.handleFirstRun(): Boolean`
  Called as the **very first thing** in `MainActivity.onCreate`, before inflating
  anything. Runs `checkAppStart`; if this is a first run, starts the first step
  (OOBE), finishes Main, and returns `true` (caller does `return`). Otherwise
  returns `false` and Main proceeds normally. This is the no-leak guarantee.
- `Activity.advanceFirstRun()`
  Called by a step when it is done: finishes itself and starts the next step, or —
  past the last step — commits the completion flag (`acceptedTosVersion`) and starts
  `MainActivity`.
- `Activity.isFirstRunStep(): Boolean`
  For dual-context steps (a screen that is both a first-run step and reachable
  standalone, e.g. from Settings). Set via an intent extra by the launcher; a
  standalone launch omits it and the step just finishes instead of advancing.

**Step integration is by plain helper calls** — no base class. A custom step is any
activity that calls `advanceFirstRun()` when finished and reads `isFirstRunStep()`
to know its context. Chosen over a base `FirstRunStepActivity` to keep app
activities independent of common-utils chrome assumptions, and over static
`nextActivity` chaining to centralize the completion commit and avoid per-step
routing boilerplate.

### common-utils cleanup (revert single-activity)

- **Remove:** `setupCommonUtilsNavGraph`; the fragment screens
  (`CommonUtilsOOBEFragment`, `...AboutFragment`, `...AboutMeFragment`,
  `...SettingsFragment`, `...LibsFragment`); the `TransitionFragment` reflection
  leak-clearer; `clearLastNestedScrollingChild`; the fragment/navigation transition
  helpers in `TransformationUtils`; the `DrawerHost` interface.
- **Restore/keep:** the activity screens (`CommonUtilsOOBEActivity`,
  `CommonUtilsAboutActivity`, etc.) and their `setup*Activity` config functions.
- **Replace:** deprecated OOBE-handling helpers with the new flow API.

## 5. GetIcon Changes

- Delete `MainFragment`, `IconFragment`, `main_navigation.xml`, and the `fragment_*`
  layouts. Restore `MainActivity` (app picker + drawer, activity shared-element
  transitions via `transformToActivity`) and `IconActivity` to their pre-migration
  shape.
- Wire the new flow: `if (handleFirstRun()) return` at the top of
  `MainActivity.onCreate`; OOBE-only (no `setupFirstRunFlow` steps). ~2 lines.
- Restore baseline-profile generator and tests to the activity shape.

## 6. Benchmark Handling

Goal: OOBE (one-time, not performance-relevant) must be **out** of the profiled
path; only `MainActivity` and `IconActivity` are measured.

**Approach (NIA-aligned):**

- Profiling uses the `androidx.baselineprofile` plugin's auto-generated,
  `profileable`, non-debuggable `nonMinifiedRelease` variant — **no manual,
  debuggable benchmark build type** (Pokedex uses `isDebuggable = true`, which
  Google warns skews profiles).
- Add a `BuildConfig` discriminator `FIRST_RUN_SKIPPABLE`, injected into the
  profiling variant only (via `androidComponents.onVariants(...).buildConfigFields`
  for `nonMinifiedRelease`); `false` in production `release`.
- `handleFirstRun()` honors a launch-intent extra `EXTRA_SKIP_FIRST_RUN` **only when
  `FIRST_RUN_SKIPPABLE`**. The generator launches with this extra → OOBE never runs
  in any iteration; Main is the stable launched activity; the profile block is just
  launch → Main → Icon → back.

This is deterministic, release-safe (production ignores the extra), and more
accurate than the Pokedex pattern. The skip mechanism is our own addition — neither
NIA nor Pokedex has a blocking TOS screen, so neither needs one.

## 7. Extensibility (other apps — out of implementation scope)

The flow API is designed so more complex fleet apps can add first-run functionality
without changing the shared mechanism: extra first-run screens (e.g. an interactive
intro, a notification-permission prompt) are added via `setupFirstRunFlow(steps)`,
each step calling `advanceFirstRun()` and checking `isFirstRunStep()`. Domain-coupled
prompts live inside the app's own step. A step reachable both during first-run and
standalone (e.g. replayable from Settings) uses `isFirstRunStep()` to branch. Actual
migration of other apps is a separate, later effort and is **not** in this spec's
implementation scope.

## 8. Implementation Scope

1. **common-utils:** build the first-run flow API; complete the revert of the
   single-activity work.
2. **GetIcon:** revert to clean multi-activity; wire the flow (OOBE-only); restore
   baseline profile + tests; add the benchmark skip discriminator.
3. **Documentation:** add minimal decision notes to `CLAUDE.md` in both repos
   (architecture stance + first-run flow contract), placed to keep each file
   structured and token-efficient.

## 9. Out of Scope

- Migrating other fleet apps to the new flow.
- Re-running the tutorial-skip / OOBE-only-on-TOS-change optimization (deliberately
  not done — full chain kept for simplicity and reliability).
- Investigating/fixing the sesl-androidx CoordinatorLayout leak at source (moot once
  fragment screens are removed).
