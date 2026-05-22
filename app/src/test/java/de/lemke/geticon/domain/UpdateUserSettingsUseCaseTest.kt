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

package de.lemke.geticon.domain

import de.lemke.geticon.data.UserSettings
import de.lemke.geticon.data.UserSettingsRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

class UpdateUserSettingsUseCaseTest : FunSpec(
    {
        val repo = mockk<UserSettingsRepository>()
        val useCase = UpdateUserSettingsUseCase(repo)

        test("delegates transform to repository") {
            val transformSlot = slot<(UserSettings) -> UserSettings>()
            val base =
                UserSettings(
                    iconSize = 512,
                    maskEnabled = true,
                    colorEnabled = false,
                    recentForegroundColors = listOf(UserSettings.DEFAULT_FOREGROUND_COLOR),
                    recentBackgroundColors = listOf(UserSettings.DEFAULT_BACKGROUND_COLOR),
                )
            val updated =
                UserSettings(
                    iconSize = 128,
                    maskEnabled = true,
                    colorEnabled = false,
                    recentForegroundColors = listOf(UserSettings.DEFAULT_FOREGROUND_COLOR),
                    recentBackgroundColors = listOf(UserSettings.DEFAULT_BACKGROUND_COLOR),
                )
            coEvery { repo.updateSettings(capture(transformSlot)) } returns updated

            val result = useCase { it.copy(iconSize = 128) }

            result shouldBe updated
            coVerify(exactly = 1) { repo.updateSettings(any()) }
            transformSlot.captured(base).iconSize shouldBe 128
        }
    },
)
