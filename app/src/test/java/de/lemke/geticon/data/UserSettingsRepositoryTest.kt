/*
 * Copyright 2022-2026 Leonard Lemke
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

package de.lemke.geticon.data

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class UserSettingsRepositoryTest : ShouldSpec({
    lateinit var repo: UserSettingsRepository

    beforeEach { repo = UserSettingsRepository(FakeDataStore()) }

    should("return defaults on fresh store") {
        val settings = repo.getSettings()
        settings.iconSize shouldBe 512
        settings.maskEnabled shouldBe true
        settings.colorEnabled shouldBe false
        settings.recentForegroundColors shouldBe listOf(UserSettings.DEFAULT_FOREGROUND_COLOR)
        settings.recentBackgroundColors shouldBe listOf(UserSettings.DEFAULT_BACKGROUND_COLOR)
    }

    should("persist iconSize round-trip") {
        repo.updateSettings { it.copy(iconSize = 256) }
        repo.getSettings().iconSize shouldBe 256
    }

    should("persist maskEnabled = false") {
        repo.updateSettings { it.copy(maskEnabled = false) }
        repo.getSettings().maskEnabled shouldBe false
    }

    should("persist colorEnabled = true") {
        repo.updateSettings { it.copy(colorEnabled = true) }
        repo.getSettings().colorEnabled shouldBe true
    }

    should("persist recentForegroundColors round-trip") {
        val colors = listOf(0xFF0000FF.toInt(), 0xFF00FF00.toInt())
        repo.updateSettings { it.copy(recentForegroundColors = colors) }
        repo.getSettings().recentForegroundColors shouldBe colors
    }

    should("persist recentBackgroundColors round-trip") {
        val colors = listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        repo.updateSettings { it.copy(recentBackgroundColors = colors) }
        repo.getSettings().recentBackgroundColors shouldBe colors
    }

    should("return updated settings from updateSettings") {
        val result = repo.updateSettings { it.copy(iconSize = 128) }
        result.iconSize shouldBe 128
    }

    should("apply multiple sequential updates") {
        repo.updateSettings { it.copy(iconSize = 64) }
        repo.updateSettings { it.copy(maskEnabled = false) }
        val settings = repo.getSettings()
        settings.iconSize shouldBe 64
        settings.maskEnabled shouldBe false
    }
})
