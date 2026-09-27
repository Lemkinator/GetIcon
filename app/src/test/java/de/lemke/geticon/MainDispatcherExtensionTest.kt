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

package de.lemke.geticon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun mainResolves() = runCatching { Dispatchers.Main.immediate }.isSuccess

private class InitLaunchViewModel : ViewModel() {
    val launchedOnConstructingThread = CompletableDeferred<Boolean>()

    init {
        val constructingThread = Thread.currentThread()
        viewModelScope.launch { launchedOnConstructingThread.complete(Thread.currentThread() == constructingThread) }
    }
}

class MainDispatcherExtensionTest : ShouldSpec(
    {
        var mainResolvedInBeforeEach = false
        lateinit var viewModel: InitLaunchViewModel

        beforeEach {
            mainResolvedInBeforeEach = mainResolves()
            viewModel = InitLaunchViewModel()
        }

        afterEach {
            mainResolves() shouldBe true
        }

        should("resolve Dispatchers.Main in beforeEach") {
            mainResolvedInBeforeEach shouldBe true
        }

        should("run a viewModelScope launch from beforeEach on the constructing thread") {
            viewModel.launchedOnConstructingThread.await() shouldBe true
        }

        context("a container") {
            should("resolve Dispatchers.Main in a nested test") {
                mainResolves() shouldBe true
            }

            val mainResolvedAfterNestedTest = mainResolves()

            should("keep Dispatchers.Main in the container after a nested test ends") {
                mainResolvedAfterNestedTest shouldBe true
            }
        }
    },
)
