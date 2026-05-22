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

package de.lemke.geticon.ui

import android.content.pm.ApplicationInfo
import android.net.Uri
import app.cash.turbine.test
import de.lemke.geticon.domain.ProcessApkUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.receiveAsFlow

class MainViewModelTest : ShouldSpec(
    {
        val processApk = mockk<ProcessApkUseCase>()
        lateinit var viewModel: MainViewModel

        beforeEach { viewModel = MainViewModel(processApk) }

        should("emit ShowError when uri is null") {
            viewModel.events.receiveAsFlow().test {
                viewModel.onApkPicked(null)
                awaitItem() shouldBe MainEvent.ShowError
            }
        }

        should("emit ShowError when processApk returns null") {
            val uri = mockk<Uri>()
            coEvery { processApk(uri) } returns null

            viewModel.events.receiveAsFlow().test {
                viewModel.onApkPicked(uri)
                awaitItem() shouldBe MainEvent.ShowError
            }
        }

        should("emit NavigateToIcon when processApk succeeds") {
            val uri = mockk<Uri>()
            val appInfo = mockk<ApplicationInfo>()
            coEvery { processApk(uri) } returns appInfo

            viewModel.events.receiveAsFlow().test {
                viewModel.onApkPicked(uri)
                awaitItem().shouldBeInstanceOf<MainEvent.NavigateToIcon>()
            }
        }

        should("NavigateToIcon carries the returned ApplicationInfo") {
            val uri = mockk<Uri>()
            val appInfo = mockk<ApplicationInfo>()
            coEvery { processApk(uri) } returns appInfo

            viewModel.events.receiveAsFlow().test {
                viewModel.onApkPicked(uri)
                val event = awaitItem() as MainEvent.NavigateToIcon
                event.applicationInfo shouldBe appInfo
            }
        }
    },
)
