# Multi-Activity Architecture + Shared First-Run Flow — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Revert both repos from single-activity back to multi-activity (OneUI-native), and add a reusable common-utils first-run flow (chain of task-root activities, OOBE-first) wired into GetIcon, with OOBE kept out of the benchmark path.

**Architecture:** common-utils owns a first-run flow API — `setupFirstRunFlow`, `handleFirstRun`, `advanceFirstRun`, `isFirstRunStep` — built on the existing `CommonUtilsOOBEActivity`. Each first-run step is its own activity launched as task root (finish-then-start-next), so predictive back = app exit at every step; the completion flag is committed once, past the last step. GetIcon reverts to `MainActivity` + `IconActivity` and uses the flow with OOBE only.

**Tech Stack:** Kotlin, AndroidX, Hilt, OneUI (sesl-androidx), DataStore, `androidx.baselineprofile` plugin, Robolectric/Roborazzi, Konsist, Spotless/Detekt/Kover.

**Spec:** `docs/superpowers/specs/2026-05-31-multi-activity-first-run-flow-design.md`

**Note on method:** This is a revert-heavy migration. Mechanical reverts use exact `git checkout main -- <path>` / `git rm` commands (precise and verifiable against `main`) rather than inlining hundreds of restored lines. New code (the flow API, benchmark wiring, CLAUDE.md) is given in full. Pure TDD applies only to the flow-decision logic (Task 3); activity/layout reverts are verified by compilation and the existing Konsist/screenshot/lint suites.

**Repo order:** Phase 1 (common-utils) must land before Phase 2 (GetIcon) compiles, because GetIcon consumes the new API via the composite `includeBuild("../common-utils")`.

---

## Phase 1 — common-utils (`A:\repo\android\common-utils`)

All paths in Phase 1 are relative to `A:\repo\android\common-utils`. Run git commands from that directory.

### Task 1: Restore activity-based screens; remove fragment/nav single-activity code

**Files:**

- Restore from `main`: `lib/src/main/java/de/lemke/commonutils/ui/activity/` (whole dir), `lib/src/main/java/de/lemke/commonutils/DrawerUtils.kt`, `lib/src/main/java/de/lemke/commonutils/TransformationUtils.kt`, `lib/src/main/java/de/lemke/commonutils/ActivityUtils.kt`, `lib/src/main/AndroidManifest.xml`, `lib/build.gradle.kts`, `gradle/libs.versions.toml`
- Restore layouts from `main`: `lib/src/main/res/layout/activity_about.xml`, `activity_about_me.xml`, `activity_about_me_content.xml`, `activity_oobe.xml`, `activity_settings_common_utils.xml`
- Delete: `lib/src/main/java/de/lemke/commonutils/ui/fragment/` (whole dir), fragment layouts `lib/src/main/res/layout/fragment_about.xml`, `fragment_about_me.xml`, `fragment_about_me_content.xml`, `fragment_libs.xml`, `fragment_oobe.xml`

- [ ] **Step 1: Restore the activity sources and shared utils from `main`**

```bash
git checkout main -- \
  lib/src/main/java/de/lemke/commonutils/ui/activity \
  lib/src/main/java/de/lemke/commonutils/DrawerUtils.kt \
  lib/src/main/java/de/lemke/commonutils/TransformationUtils.kt \
  lib/src/main/java/de/lemke/commonutils/ActivityUtils.kt \
  lib/src/main/AndroidManifest.xml \
  lib/build.gradle.kts \
  gradle/libs.versions.toml
```

- [ ] **Step 2: Restore activity layouts from `main`**

```bash
git checkout main -- \
  lib/src/main/res/layout/activity_about.xml \
  lib/src/main/res/layout/activity_about_me.xml \
  lib/src/main/res/layout/activity_about_me_content.xml \
  lib/src/main/res/layout/activity_oobe.xml \
  lib/src/main/res/layout/activity_settings_common_utils.xml
```

- [ ] **Step 3: Delete the fragment sources and layouts**

```bash
git rm -r lib/src/main/java/de/lemke/commonutils/ui/fragment
git rm lib/src/main/res/layout/fragment_about.xml \
       lib/src/main/res/layout/fragment_about_me.xml \
       lib/src/main/res/layout/fragment_about_me_content.xml \
       lib/src/main/res/layout/fragment_libs.xml \
       lib/src/main/res/layout/fragment_oobe.xml
```

- [ ] **Step 4: Confirm no dangling references to removed symbols**

Run:

```bash
git grep -n "setupCommonUtilsNavGraph\|CommonUtilsOOBEFragment\|CommonUtilsAboutFragment\|CommonUtilsAboutMeFragment\|CommonUtilsSettingsFragment\|CommonUtilsLibsFragment\|TransitionFragment\|clearLastNestedScrollingChild\|DrawerHost\|prepareFragmentTransformationTo\|transformToFragment\|navigateWithTransform" lib/src || echo "CLEAN"
```

Expected: `CLEAN` (CheckAppStartUtils still references the OOBE activity — that is handled in Task 2/4; it should not reference any fragment symbol).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "revert: restore activity-based common-utils screens; drop single-activity fragment/nav code"
```

---

### Task 2: Add the first-run flow API

**Files:**

- Create: `lib/src/main/java/de/lemke/commonutils/FirstRunFlowUtils.kt`

- [ ] **Step 1: Create `FirstRunFlowUtils.kt`**

```kotlin
/*
 * Copyright 2024-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package de.lemke.commonutils

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.app.Activity
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.appcompat.app.AppCompatActivity
import de.lemke.commonutils.data.commonUtilsSettings
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity

/** Intent extra marking an activity launched as a step of the first-run chain. */
const val EXTRA_FIRST_RUN = "commonUtilsFirstRunStep"

/** Intent extra (honored only when the host opts in) that bypasses the first-run chain, for benchmarks. */
const val EXTRA_SKIP_FIRST_RUN = "commonUtilsSkipFirstRun"

/** Holds the ordered first-run chain configuration. OOBE is always the implicit first step. */
object FirstRunFlow {
    /** App-specific steps that run after OOBE, in order. */
    var steps: List<Class<out Activity>> = emptyList()

    /** The activity to land on once the chain completes (set by [handleFirstRun]). */
    var mainActivity: Class<out Activity>? = null
}

/** Declares the app-specific first-run steps that run after OOBE. Apps with OOBE only may skip this. */
fun setupFirstRunFlow(steps: List<Class<out Activity>> = emptyList()) {
    FirstRunFlow.steps = steps
}

/** The full ordered chain: OOBE first, then the configured steps. */
private fun firstRunChain(): List<Class<out Activity>> = listOf(CommonUtilsOOBEActivity::class.java) + FirstRunFlow.steps

/** Returns the step after [current] in the chain, or `null` if [current] is the last step. */
internal fun nextFirstRunStep(current: Class<*>): Class<out Activity>? {
    val chain = firstRunChain()
    val index = chain.indexOfFirst { it == current }
    return chain.getOrNull(index + 1)
}

/**
 * Call as the FIRST thing in the launcher activity's `onCreate`, before inflating any UI.
 *
 * @return `true` if a first run was detected and OOBE was launched (the caller must `return`
 *   immediately so no UI is built). `false` for a normal start (proceed to build the activity).
 *
 * When [allowSkip] is `true` and the launch intent carries [EXTRA_SKIP_FIRST_RUN], the chain is
 * bypassed (used by benchmarks). [allowSkip] must be gated by the caller (e.g. a BuildConfig flag).
 */
fun AppCompatActivity.handleFirstRun(
    versionCode: Int,
    versionName: String,
    allowSkip: Boolean = false,
): Boolean {
    if (allowSkip && intent.getBooleanExtra(EXTRA_SKIP_FIRST_RUN, false)) return false
    if (!checkAppStart(versionCode, versionName).shouldShowOOBE) return false
    FirstRunFlow.mainActivity = this::class.java
    startActivity(Intent(this, CommonUtilsOOBEActivity::class.java).putExtra(EXTRA_FIRST_RUN, true))
    @Suppress("DEPRECATION")
    if (SDK_INT < UPSIDE_DOWN_CAKE) overridePendingTransition(fade_in, fade_out)
    finishAfterTransition()
    return true
}

/**
 * Advances the first-run chain from the current step: finishes this activity and starts the next
 * step (as task root, tagged [EXTRA_FIRST_RUN]); or, past the last step, commits the completion
 * flag and starts the main activity.
 *
 * Call from a step activity when the user finishes that step.
 */
fun Activity.advanceFirstRun() {
    val next = nextFirstRunStep(this::class.java)
    if (next != null) {
        startActivity(Intent(this, next).putExtra(EXTRA_FIRST_RUN, true))
    } else {
        commonUtilsSettings.acceptedTosVersion = resources.getInteger(R.integer.commonutils_tos_version)
        FirstRunFlow.mainActivity?.let { startActivity(Intent(this, it)) }
    }
    @Suppress("DEPRECATION")
    if (SDK_INT < UPSIDE_DOWN_CAKE) overridePendingTransition(fade_in, fade_out)
    finishAfterTransition()
}

/** `true` if this activity was launched as a step of the first-run chain (vs. standalone). */
fun Activity.isFirstRunStep(): Boolean = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
```

- [ ] **Step 2: Compile-check the new file**

Run:

```bash
./gradlew :lib:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL (references `checkAppStart`, `shouldShowOOBE`, `commonUtilsSettings`, `CommonUtilsOOBEActivity`, `R.integer.commonutils_tos_version` — all already present).

- [ ] **Step 3: Commit**

```bash
git add lib/src/main/java/de/lemke/commonutils/FirstRunFlowUtils.kt
git commit -m "feat: add first-run flow API (chain of task-root activities, OOBE-first)"
```

---

### Task 3: Wire OOBE into the flow; remove deprecated OOBE helpers

**Files:**

- Modify: `lib/src/main/java/de/lemke/commonutils/ui/activity/CommonUtilsOOBEActivity.kt`
- Modify: `lib/src/main/java/de/lemke/commonutils/CheckAppStartUtils.kt`
- Test: `lib/src/test/java/de/lemke/commonutils/FirstRunFlowTest.kt`

- [ ] **Step 1: Write the failing test for chain decision logic**

Create `lib/src/test/java/de/lemke/commonutils/FirstRunFlowTest.kt`:

```kotlin
/*
 * Copyright 2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package de.lemke.commonutils

import com.google.common.truth.Truth.assertThat
import de.lemke.commonutils.ui.activity.CommonUtilsLibsActivity
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirstRunFlowTest {
    @After
    fun reset() {
        FirstRunFlow.steps = emptyList()
    }

    @Test
    fun `OOBE is the last step when no app steps are configured`() {
        FirstRunFlow.steps = emptyList()
        assertThat(nextFirstRunStep(CommonUtilsOOBEActivity::class.java)).isNull()
    }

    @Test
    fun `OOBE advances to the first configured app step`() {
        FirstRunFlow.steps = listOf(CommonUtilsLibsActivity::class.java)
        assertThat(nextFirstRunStep(CommonUtilsOOBEActivity::class.java))
            .isEqualTo(CommonUtilsLibsActivity::class.java)
    }

    @Test
    fun `the last configured app step has no next`() {
        FirstRunFlow.steps = listOf(CommonUtilsLibsActivity::class.java)
        assertThat(nextFirstRunStep(CommonUtilsLibsActivity::class.java)).isNull()
    }
}
```

> Note: `CommonUtilsLibsActivity` is used only as an arbitrary `Activity` class token for ordering; any restored activity class works. If it is not present, substitute `CommonUtilsSettingsActivity`.

- [ ] **Step 2: Run the test to verify it passes (logic already implemented in Task 2)**

Run:

```bash
./gradlew :lib:testDebugUnitTest --tests "de.lemke.commonutils.FirstRunFlowTest"
```

Expected: PASS. (The decision logic shipped in Task 2; this test pins its contract. If Robolectric is not yet configured in `:lib`, see Step 3.)

- [ ] **Step 3: If `:lib` lacks Robolectric, make the test a plain JVM test instead**

If Step 2 fails with a missing-runner/Robolectric error, replace `@RunWith(RobolectricTestRunner::class)` usage by removing the annotation and the import (the test instantiates no Android objects — it only compares `Class` tokens). Re-run Step 2; expected PASS.

- [ ] **Step 4: Wire `CommonUtilsOOBEActivity` to advance the chain**

In `CommonUtilsOOBEActivity.kt`, replace the footer-button completion logic so that, on continue, it advances the first-run chain instead of using `nextActivity`/`onContinue`. Locate `initFooterButton()` (the block that currently does `if (setAcceptedTosVersion) commonUtilsSettings.acceptedTosVersion = ...` followed by the `nextActivity?.let { ... } ?: onContinue?.invoke() ?: finishAfterTransition()`). Replace that completion body with:

```kotlin
                advanceFirstRun()
```

Remove the now-unused `setAcceptedTosVersion`, `nextActivity`, and `onContinue` companion properties and any imports they required (`android.content.Intent` may still be needed elsewhere — only remove if unused). Keep `tosChanged` (still drives the TOS copy).

Rationale: the completion flag is now committed by `advanceFirstRun()` at the end of the chain, not by OOBE — making the whole chain atomic.

- [ ] **Step 5: Remove the deprecated OOBE helpers from `CheckAppStartUtils.kt`**

In `CheckAppStartUtils.kt`, delete the deprecated functions `checkAppStartAndHandleOOBE(...)` and `openOOBEAndFinish()` (and the now-unused imports `android.R.anim.fade_in`, `fade_out`, `android.content.Intent`, `SDK_INT`, `UPSIDE_DOWN_CAKE`). Keep `checkAppStart(...)`, the `AppStart` type, `shouldShowOOBE`, and the `tosChanged` assignment (update it to `CommonUtilsOOBEActivity.tosChanged = true`, which remains valid).

- [ ] **Step 6: Verify no references to removed OOBE statics/helpers remain**

Run:

```bash
git grep -n "checkAppStartAndHandleOOBE\|openOOBEAndFinish\|CommonUtilsOOBEActivity.nextActivity\|CommonUtilsOOBEActivity.onContinue\|setupCommonUtilsOOBEActivity" lib/src || echo "CLEAN"
```

Expected: `CLEAN`. (`setupCommonUtilsOOBEActivity` in `ActivityUtils.kt` set `nextActivity`/`onContinue`; remove those two `setupCommonUtilsOOBEActivity` overloads from `ActivityUtils.kt` as part of this step, since the flow replaces them.)

- [ ] **Step 7: Run the flow test + compile**

Run:

```bash
./gradlew :lib:testDebugUnitTest --tests "de.lemke.commonutils.FirstRunFlowTest" :lib:compileDebugKotlin
```

Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: route OOBE through first-run flow; remove deprecated OOBE helpers"
```

---

### Task 4: Formatting, static analysis, lint baseline, build

- [ ] **Step 1: Apply formatting**

Run:

```bash
./gradlew :lib:spotlessApply
```

- [ ] **Step 2: Refresh the lint baseline (renamed/removed layouts changed it)**

Run:

```bash
./gradlew :lib:updateLintBaseline
```

- [ ] **Step 3: Full lib build + checks**

Run:

```bash
./gradlew :lib:build
```

Expected: BUILD SUCCESSFUL (spotlessCheck, detekt, lint, unit tests all pass).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: spotless + lint baseline after first-run flow migration"
```

---

### Task 5: common-utils CLAUDE.md decision note

**Files:**

- Modify: `A:\repo\android\common-utils\CLAUDE.md` (or create if absent)

- [ ] **Step 1: Add a concise "First-Run Flow" section**

Add this section to `common-utils`'s `CLAUDE.md` (place it under the existing architecture/usage section; if no `CLAUDE.md` exists, create one with just this section under a top `# CLAUDE.md` heading):

```markdown
## First-Run Flow

OneUI is activity-oriented; this lib is **multi-activity** (no single-activity /
Navigation Component). Shared screens (OOBE, About, AboutMe, Settings, Libs) are
activities. Fragments only for genuine sibling/tab content inside one screen.

First run = an ordered **chain of task-root activities**, OOBE first, advanced by
finish-then-start-next (`FirstRunFlowUtils.kt`):

- `setupFirstRunFlow(steps)` — app steps after OOBE (omit for OOBE-only apps).
- `handleFirstRun(versionCode, versionName, allowSkip)` — call FIRST in the launcher
  activity's `onCreate`, before inflating UI; returns `true` (caller must `return`)
  when it launched OOBE. `allowSkip` (gated by the app) honors `EXTRA_SKIP_FIRST_RUN`
  for benchmarks.
- `advanceFirstRun()` — a step calls this when done; starts the next step or, past the
  last, commits `acceptedTosVersion` and starts the main activity.
- `isFirstRunStep()` — for dual-context steps (also reachable standalone).

Why: each step is task root ⇒ predictive back = app exit, no main behind. Redirect
before building main ⇒ no leak on first start. Commit only past the last step ⇒
atomic (kill mid-chain replays from OOBE). Full chain on any first-run trigger
(install or TOS bump) for one uniform, reliable commit point.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document multi-activity stance and first-run flow"
```

---

## Phase 2 — GetIcon (`A:\repo\android\GetIcon`)

All paths in Phase 2 are relative to `A:\repo\android\GetIcon`. Run git commands from that directory. The composite `includeBuild("../common-utils")` in `settings.gradle.kts` makes Phase 1 changes available immediately.

### Task 6: Revert GetIcon to multi-activity

**Files:**

- Restore from `main`: `app/src/main/java/de/lemke/geticon/ui/MainActivity.kt`, `app/src/main/java/de/lemke/geticon/ui/IconActivity.kt`, `app/src/main/java/de/lemke/geticon/ui/IconViewModel.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/layout/activity_icon.xml`, `app/src/main/res/layout-land/activity_icon.xml`
- Restore tests from `main`: `app/src/test/java/de/lemke/geticon/ui/IconActivityTest.kt`, `app/src/test/java/de/lemke/geticon/ui/IconActivityScreenshotTest.kt`, `app/src/test/java/de/lemke/geticon/ui/IconViewModelTest.kt`, `app/src/test/java/de/lemke/geticon/ui/MainActivityScreenshotTest.kt`
- Delete: `app/src/main/java/de/lemke/geticon/ui/MainFragment.kt`, `app/src/main/java/de/lemke/geticon/ui/IconFragment.kt`, `app/src/main/res/navigation/main_navigation.xml`, `app/src/main/res/layout/fragment_main.xml`, `app/src/main/res/layout/fragment_icon.xml`, `app/src/main/res/layout-land/fragment_icon.xml`

- [ ] **Step 1: Restore activity sources, manifest, layouts, and tests from `main`**

```bash
git checkout main -- \
  app/src/main/java/de/lemke/geticon/ui/MainActivity.kt \
  app/src/main/java/de/lemke/geticon/ui/IconActivity.kt \
  app/src/main/java/de/lemke/geticon/ui/IconViewModel.kt \
  app/src/main/AndroidManifest.xml \
  app/src/main/res/layout/activity_main.xml \
  app/src/main/res/layout/activity_icon.xml \
  app/src/main/res/layout-land/activity_icon.xml \
  app/src/test/java/de/lemke/geticon/ui/IconActivityTest.kt \
  app/src/test/java/de/lemke/geticon/ui/IconActivityScreenshotTest.kt \
  app/src/test/java/de/lemke/geticon/ui/IconViewModelTest.kt \
  app/src/test/java/de/lemke/geticon/ui/MainActivityScreenshotTest.kt
```

- [ ] **Step 2: Delete the fragment/navigation sources and layouts**

```bash
git rm app/src/main/java/de/lemke/geticon/ui/MainFragment.kt \
       app/src/main/java/de/lemke/geticon/ui/IconFragment.kt \
       app/src/main/res/navigation/main_navigation.xml \
       app/src/main/res/layout/fragment_main.xml \
       app/src/main/res/layout/fragment_icon.xml \
       app/src/main/res/layout-land/fragment_icon.xml
```

- [ ] **Step 3: Confirm no dangling single-activity references**

Run:

```bash
git grep -n "MainFragment\|IconFragment\|main_navigation\|NavHostFragment\|setupCommonUtilsNavGraph\|DrawerHost\|fragment_main\|fragment_icon" app/src/main || echo "CLEAN"
```

Expected: `CLEAN`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "revert: restore GetIcon multi-activity (MainActivity + IconActivity)"
```

---

### Task 7: Wire the first-run flow into MainActivity

**Files:**

- Modify: `app/src/main/java/de/lemke/geticon/ui/MainActivity.kt`

The restored `MainActivity` (from `main`) calls `setupCommonUtilsOOBEActivity(...)` and `if (!checkAppStartAndHandleOOBE(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) openMain(savedInstanceState)` — both removed in Phase 1. Replace with the new flow, and move the check **before** `setContentView` so no UI is built on first run.

- [ ] **Step 1: Replace OOBE wiring with `handleFirstRun`**

In `MainActivity.onCreate`, the restored body is roughly:

```kotlin
        val splashScreen = installSplashScreen()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        if (SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCommonUtilsSplashScreen(splashScreen, binding.root) { !isUIReady }
        setupCommonUtilsOOBEActivity(nextActivity = MainActivity::class.java)
        if (!checkAppStartAndHandleOOBE(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) openMain(savedInstanceState)
```

Change it to (note the first-run check happens **before** inflating the binding, and `openMain` is always called on the normal path):

```kotlin
        val splashScreen = installSplashScreen()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        if (handleFirstRun(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, allowSkip = BuildConfig.FIRST_RUN_SKIPPABLE)) return
        if (SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCommonUtilsSplashScreen(splashScreen, binding.root) { !isUIReady }
        openMain(savedInstanceState)
```

- [ ] **Step 2: Fix imports**

Replace the import `import de.lemke.commonutils.checkAppStartAndHandleOOBE` and `import de.lemke.commonutils.setupCommonUtilsOOBEActivity` with:

```kotlin
import de.lemke.commonutils.handleFirstRun
```

Leave all other restored imports intact. (`BuildConfig.FIRST_RUN_SKIPPABLE` is added in Task 8.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/de/lemke/geticon/ui/MainActivity.kt
git commit -m "feat: use common-utils first-run flow in MainActivity (OOBE-only)"
```

---

### Task 8: Benchmark — gate first-run skip to the profiling variant

**Files:**

- Modify: `app/build.gradle.kts`
- Modify: `baselineprofile/src/main/java/de/lemke/geticon/baselineprofile/BaselineProfileGenerator.kt`

- [ ] **Step 1: Default `FIRST_RUN_SKIPPABLE` to false for all variants**

In `app/build.gradle.kts`, inside `android { defaultConfig { ... } }`, add:

```kotlin
        buildConfigField("boolean", "FIRST_RUN_SKIPPABLE", "false")
```

Ensure `android { buildFeatures { buildConfig = true } }` is set (it is, since `BuildConfig` is already used).

- [ ] **Step 2: Enable the skip only for the profile-generation variant**

In `app/build.gradle.kts`, at the top level of the `android { ... }` block, add an `androidComponents` block (or extend the existing one) that flips the flag on the plugin-generated `nonMinifiedRelease` variant:

```kotlin
androidComponents {
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.buildConfigFields.put(
            "FIRST_RUN_SKIPPABLE",
            com.android.build.api.variant.BuildConfigField("boolean", "true", "Allow benchmarks to skip the first-run chain"),
        )
    }
}
```

- [ ] **Step 3: Make the baseline profile generator skip OOBE and exercise Main → Icon → back**

Replace `baselineprofile/src/main/java/de/lemke/geticon/baselineprofile/BaselineProfileGenerator.kt` body with:

```kotlin
/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package de.lemke.geticon.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "de.lemke.geticon"
private const val TIMEOUT_MS = 5_000L
private const val EXTRA_SKIP_FIRST_RUN = "commonUtilsSkipFirstRun"

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(
            packageName = PACKAGE_NAME,
            stableIterations = 3,
            maxIterations = 10,
            includeInStartupProfile = true,
        ) {
            startActivityAndWait(Intent().apply { putExtra(EXTRA_SKIP_FIRST_RUN, true) })
            navigateToIconAndBack()
        }
}

private fun MacrobenchmarkScope.navigateToIconAndBack() {
    val appItem =
        device
            .wait(Until.findObject(By.res(PACKAGE_NAME, "appPicker")), TIMEOUT_MS)
            ?.let { device.wait(Until.findObject(By.clazz("android.widget.TextView")), TIMEOUT_MS) }
    appItem?.click()
    device.waitForIdle()
    device.wait(Until.findObject(By.res(PACKAGE_NAME, "icon")), TIMEOUT_MS)
    device.pressBack()
    device.waitForIdle()
}
```

> `startActivityAndWait(intent)` passes the skip extra; because the profiling variant sets `FIRST_RUN_SKIPPABLE = true`, `handleFirstRun` returns `false` and Main loads directly — OOBE never appears in any iteration.

- [ ] **Step 4: Verify the app compiles for the profiling variant**

Run:

```bash
./gradlew :app:assembleNonMinifiedRelease
```

Expected: BUILD SUCCESSFUL, and `BuildConfig.FIRST_RUN_SKIPPABLE` resolves (true for this variant).

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts baselineprofile/src/main/java/de/lemke/geticon/baselineprofile/BaselineProfileGenerator.kt
git commit -m "feat: skip first-run in baseline profile via gated FIRST_RUN_SKIPPABLE"
```

---

### Task 9: Build, tests, static analysis

- [ ] **Step 1: Apply formatting**

Run:

```bash
./gradlew :app:spotlessApply
```

- [ ] **Step 2: Run unit tests (incl. Konsist architecture + screenshot tests)**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS. Konsist (`ArchitectureTest`) must pass with the restored `data/domain/ui` layering. If a screenshot test fails only because a golden image changed legitimately, regenerate with `./gradlew :app:recordRoborazziDebug` and re-run; otherwise treat as a real failure.

- [ ] **Step 3: Lint (catches NewerVersionAvailable) + refresh baseline if needed**

Run:

```bash
./gradlew :app:lintDebug
```

Expected: BUILD SUCCESSFUL. If lint reports a stale baseline entry for a removed fragment layout, run `./gradlew :app:updateLintBaseline` and re-run.

- [ ] **Step 4: Full build**

Run:

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit any baseline/formatting changes**

```bash
git add -A
git commit -m "chore: spotless + lint/screenshot baselines after multi-activity revert"
```

---

### Task 10: GetIcon CLAUDE.md decision note

**Files:**

- Modify: `A:\repo\android\GetIcon\CLAUDE.md`

- [ ] **Step 1: Update the Architecture section**

In `GetIcon/CLAUDE.md`, under `## Architecture`, replace any single-activity / Navigation Component wording and add this note (keep it brief; integrate with the existing activity list rather than duplicating it):

```markdown
**Multi-activity (not single-activity).** OneUI (sesl-androidx) is activity-oriented;
single-activity + Navigation Component was tried and reverted (buggy menu, leaky
fragment transitions needing reflection, OneUI screens authored as activities).
`MainActivity` (app picker) and `IconActivity` (preview/export) are separate
activities; navigation between them uses shared-element activity transitions
(`transformToActivity`).

**First run** uses the common-utils first-run flow: `handleFirstRun(...)` is the first
call in `MainActivity.onCreate` (before inflating UI) and launches OOBE as a task-root
activity when needed (predictive back = app exit; no Main leak on first start). GetIcon
uses OOBE only. The baseline-profile (`nonMinifiedRelease`) build sets
`BuildConfig.FIRST_RUN_SKIPPABLE = true`, so the benchmark passes `EXTRA_SKIP_FIRST_RUN`
to bypass OOBE and measure Main + Icon only; production `release` keeps it `false`.
```

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document multi-activity revert and first-run flow"
```

---

## Self-Review Checklist (completed by plan author)

- **Spec coverage:** §2 strategy → Tasks 1, 6 (revert) + CLAUDE.md Tasks 5, 10. §3 flow model → Task 2. §4 API + cleanup → Tasks 1–3. §5 GetIcon → Tasks 6–7. §6 benchmark → Task 8. §7 extensibility → covered by `setupFirstRunFlow`/`advanceFirstRun`/`isFirstRunStep` (Task 2) and documented (Task 5). §8 scope → all tasks. §9 out-of-scope (other apps, OOBE-only-on-TOS, sesl source fix) → not included. ✓
- **Placeholders:** none (`git checkout main -- <path>` is an exact, verifiable action; new code given in full). ✓
- **Type consistency:** `handleFirstRun`, `advanceFirstRun`, `isFirstRunStep`, `setupFirstRunFlow`, `nextFirstRunStep`, `FirstRunFlow.steps/.mainActivity`, `EXTRA_FIRST_RUN`, `EXTRA_SKIP_FIRST_RUN`, `FIRST_RUN_SKIPPABLE` used identically across Tasks 2, 3, 7, 8. ✓
